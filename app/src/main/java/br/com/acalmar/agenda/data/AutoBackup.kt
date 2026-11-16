package br.com.acalmar.agenda.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Backup automático: grava uma cópia completa (dados e fotos) em uma pasta do celular escolhida
 * em Mais > Backup, sem a pessoa precisar lembrar.
 *
 * - A cópia é feita quando o app vai para segundo plano ou é aberto, se algo mudou desde a
 *   última cópia e já passou o intervalo mínimo.
 * - São mantidos 3 arquivos, em rodízio por dia. Se um deles for gravado pela metade
 *   (celular desligou no meio), os outros dois continuam inteiros.
 */
class AutoBackup(context: Context, private val repo: Repository, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val running = Mutex()

    /** Chamado quando o app abre ou sai da tela. Nunca lança erro. */
    fun runIfDue() {
        scope.launch { runCatching { run(force = false) }.onFailure { Log.w(TAG, "Falha no backup automático", it) } }
    }

    /** Faz a cópia agora (botão "Copiar agora"). Devolve a mensagem de erro, ou nulo se deu certo. */
    suspend fun runNow(): String? = run(force = true)

    private suspend fun run(force: Boolean): String? {
        val folder = repo.getSetting(Keys.AUTO_BACKUP_FOLDER)?.takeIf { it.isNotBlank() } ?: return "Nenhuma pasta escolhida."
        // "Copiar agora" espera uma cópia em andamento terminar; a conferência automática só desiste.
        if (force) running.lock() else if (!running.tryLock()) return null
        try {
            val now = System.currentTimeMillis()
            val last = repo.getSetting(Keys.AUTO_BACKUP_AT)?.toLongOrNull() ?: 0L
            if (!force && now - last in 0 until MIN_INTERVAL_MS) return null

            val fingerprint = repo.dataFingerprint()
            if (!force && fingerprint == repo.getSetting(Keys.AUTO_BACKUP_HASH)) return null

            val error = try {
                withContext(Dispatchers.IO) { write(Uri.parse(folder)) }
                null
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao gravar o backup automático", e)
                when (e) {
                    is SecurityException -> "O app perdeu o acesso à pasta. Escolha a pasta de novo."
                    else -> "Não foi possível gravar na pasta escolhida. Confira se ela ainda existe e se há espaço no celular."
                }
            }
            if (error == null) repo.setSetting(Keys.AUTO_BACKUP_HASH, fingerprint)
            repo.markAutoBackup(error)
            return error
        } finally {
            running.unlock()
        }
    }

    private suspend fun write(tree: Uri) {
        val resolver = appContext.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val name = fileNameFor(LocalDate.now().toEpochDay())

        // Procura o arquivo do rodízio de hoje; se já existe, é substituído.
        var target: Uri? = null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    target = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    break
                }
            }
        }
        val file = target
            ?: DocumentsContract.createDocument(resolver, parent, "application/zip", name)
            ?: throw IOException("A pasta não aceitou um arquivo novo.")
        val out = resolver.openOutputStream(file, "wt") ?: throw IOException("Não foi possível abrir o arquivo.")
        out.use { repo.exportBackup(it) }
    }

    companion object {
        private const val TAG = "Acalmar"

        /** Intervalo mínimo entre duas cópias automáticas. */
        const val MIN_INTERVAL_MS = 60L * 60 * 1000

        const val SLOTS = 3

        /** Nome do arquivo do dia: 3 arquivos em rodízio. */
        fun fileNameFor(epochDay: Long): String = "acalmar-backup-automatico-${Math.floorMod(epochDay, SLOTS.toLong()) + 1}.zip"

        /** Guarda a permissão de gravar na pasta escolhida, para valer depois de fechar o app. */
        fun keepAccess(context: Context, tree: Uri) {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        fun releaseAccess(context: Context, tree: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

        /** Nome da pasta para mostrar na tela (ex.: "Documents/Acalmar"). */
        fun folderLabel(tree: Uri): String =
            runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "Armazenamento do celular" } }
                .getOrDefault("Pasta escolhida")
    }
}
