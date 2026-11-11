package br.com.acalmar.agenda.ui.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.BackupSummary
import br.com.acalmar.agenda.data.Keys
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

private fun plural(count: Int, one: String, many: String): String = "$count " + if (count == 1) one else many

private fun describe(summary: BackupSummary): String {
    val parts = mutableListOf(
        plural(summary.clients, "cliente", "clientes"),
        plural(summary.appointments, "agendamento", "agendamentos"),
        plural(summary.cashEntries, "lançamento de caixa", "lançamentos de caixa")
    )
    if (summary.photos > 0) parts += plural(summary.photos, "foto", "fotos")
    return parts.dropLast(1).joinToString(", ") + " e " + parts.last()
}

/**
 * Backup: grava todos os dados e as fotos em um único arquivo (por exemplo, no Google Drive)
 * e restaura a partir dele. Também aceita o arquivo de backup da primeira versão do app.
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = emptyMap())
    val (statusText, late) = backupStatus(settings[Keys.LAST_BACKUP])

    var busy by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pendingSummary by remember { mutableStateOf("") }

    fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Sem acesso ao arquivo")

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            busy = "Gravando o backup…"
            scope.launch {
                val result = runCatching {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Sem acesso ao arquivo")
                    val summary = stream.use { repo.exportBackup(it) }
                    repo.markBackupDone()
                    summary
                }
                busy = null
                val summary = result.getOrNull()
                notifier.show(
                    if (summary != null) "Backup salvo: ${describe(summary)}" else "Não foi possível salvar o backup"
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = "Conferindo o arquivo…"
            scope.launch {
                val summary = runCatching { repo.inspectBackup { open(uri) } }.getOrNull()
                busy = null
                if (summary != null) {
                    pendingSummary = describe(summary)
                    pendingImport = uri
                } else {
                    notifier.show("O arquivo escolhido não é um backup do Acalmar")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Backup", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SoftCard(Modifier.fillMaxWidth(), container = if (late) Brand.BeigeSoft else Brand.Card) {
                Column(Modifier.padding(16.dp)) {
                    Text(statusText, style = MaterialTheme.typography.titleMedium, color = if (late) Brand.BronzeDark else Brand.Brown)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Os dados e as fotos do app ficam só neste celular. O backup guarda uma cópia de tudo em um arquivo: " +
                            "se o celular quebrar, for trocado ou o app for apagado, é com ele que tudo volta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.BrownSoft
                    )
                }
            }

            Button(
                onClick = { exportLauncher.launch("acalmar-backup-${LocalDate.now()}.zip") },
                enabled = busy == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Fazer backup agora", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Na tela que abrir, escolha o Google Drive como local para salvar. Assim a cópia fica fora do celular. " +
                    "O ideal é fazer um backup por semana.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain")
                    )
                },
                enabled = busy == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restaurar um backup", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Restaurar substitui tudo o que está no app pelos dados do arquivo escolhido.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )

            val working = busy
            if (working != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Brand.Bronze)
                    Spacer(Modifier.width(12.dp))
                    Text(working, color = Brand.BrownSoft)
                }
            }
        }
    }

    val uri = pendingImport
    if (uri != null) {
        ConfirmDialog(
            title = "Restaurar este backup?",
            text = "O arquivo tem $pendingSummary. Tudo o que está no app agora será substituído por esses dados.",
            confirmLabel = "Restaurar",
            danger = true,
            onConfirm = {
                pendingImport = null
                busy = "Restaurando…"
                scope.launch {
                    val ok = runCatching { repo.importBackup { open(uri) } }.isSuccess
                    busy = null
                    notifier.show(if (ok) "Backup restaurado" else "Não foi possível restaurar o backup")
                }
            },
            onDismiss = { pendingImport = null }
        )
    }
}
