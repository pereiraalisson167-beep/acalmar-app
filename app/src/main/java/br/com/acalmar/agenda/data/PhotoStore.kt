package br.com.acalmar.agenda.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/**
 * Guarda as fotos das clientes dentro da área privada do app (não aparecem na galeria do celular).
 * Cada foto é reduzida e gravada como JPEG; o banco de dados guarda só o nome do arquivo.
 */
class PhotoStore(context: Context) {

    private val appContext = context.applicationContext

    val cacheDir: File get() = appContext.cacheDir

    private val dir: File
        get() = File(appContext.filesDir, "photos").apply { mkdirs() }

    /** Arquivo de uma foto. Só o nome é usado, para nunca sair da pasta das fotos. */
    fun file(name: String): File = File(dir, File(name).name)

    fun exists(name: String): Boolean = file(name).isFile

    /**
     * Copia a imagem escolhida (galeria ou câmera) para dentro do app, já na posição certa e
     * reduzida. Devolve o nome do arquivo criado.
     */
    fun importFrom(uri: Uri): String {
        val resolver = appContext.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Não foi possível abrir a imagem.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("O arquivo escolhido não é uma imagem.")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Não foi possível ler a imagem.")

        // Fotos da câmera costumam vir "deitadas", com a posição correta anotada no arquivo.
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longEdge
            matrix.postScale(scale, scale)
        }
        if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (transformed !== bitmap) bitmap.recycle()
            bitmap = transformed
        }

        val name = UUID.randomUUID().toString() + ".jpg"
        try {
            FileOutputStream(file(name)).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IOException("Não foi possível gravar a foto.")
                }
            }
        } catch (e: Exception) {
            file(name).delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        return name
    }

    fun delete(name: String) {
        file(name).delete()
    }

    /** Apaga todas as fotos guardadas, menos as de nome indicado. */
    fun deleteAllExcept(keep: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    /** Endereço temporário onde a câmera grava a foto tirada. */
    fun newCameraUri(): Uri {
        val folder = File(appContext.cacheDir, "camera").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val file = File(folder, "foto-" + System.currentTimeMillis() + ".jpg")
        return FileProvider.getUriForFile(appContext, authority(appContext), file)
    }

    companion object {
        /** Maior lado da foto guardada, em pixels. */
        const val MAX_EDGE = 1600
        const val JPEG_QUALITY = 88

        fun authority(context: Context): String = context.packageName + ".files"
    }
}
