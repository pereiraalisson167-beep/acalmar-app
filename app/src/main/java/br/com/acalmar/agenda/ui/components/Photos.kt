package br.com.acalmar.agenda.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

// Carregamento das fotos guardadas no app, já reduzidas ao tamanho em que serão mostradas.

private val bitmapCache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Lê a foto do arquivo reduzindo-a para que o maior lado fique perto de `maxEdge` pixels. */
fun decodeSampled(file: File, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Mostra uma foto guardada no app. Enquanto carrega (ou se o arquivo faltar), fica um fundo claro. */
@Composable
fun PhotoImage(
    file: File,
    maxEdge: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null
) {
    val key = file.path + "@" + maxEdge
    val bitmap by produceState<ImageBitmap?>(initialValue = bitmapCache.get(key), key) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) { decodeSampled(file, maxEdge)?.asImageBitmap() }
            if (loaded != null) bitmapCache.put(key, loaded)
            value = loaded
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(Brand.BeigeSoft))
    }
}
