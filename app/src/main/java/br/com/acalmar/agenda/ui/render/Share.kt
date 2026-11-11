package br.com.acalmar.agenda.ui.render

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import br.com.acalmar.agenda.data.PhotoStore
import java.io.File

// Envio de arquivos gerados pelo app (orçamento, termo, fotos) para outros apps, como o WhatsApp.

/** Pasta temporária dos arquivos enviados; é limpa a cada novo envio. */
private fun shareDir(context: Context): File = File(context.cacheDir, "share").apply { mkdirs() }

/** Arquivo novo na pasta de envio. O nome vira o nome do arquivo recebido por quem abre. */
fun newShareFile(context: Context, name: String): File {
    val dir = shareDir(context)
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "-"))
}

fun saveBitmapForShare(context: Context, bitmap: Bitmap, name: String): File {
    val file = newShareFile(context, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

/** Abre a tela "Compartilhar" do Android com o arquivo. Devolve falso se não for possível. */
fun shareFile(context: Context, file: File, mime: String, title: String, text: String? = null): Boolean =
    runCatching {
        val uri = FileProvider.getUriForFile(context, PhotoStore.authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.isSuccess

/** Abre a tela "Compartilhar" do Android com um texto. */
fun shareText(context: Context, text: String, title: String): Boolean =
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
