package br.com.acalmar.agenda.domain

import java.util.Locale

// Assinatura feita com o dedo na tela, guardada como texto.
//
// Formato: "proporção|x,y x,y;x,y x,y" — a proporção é largura/altura da área de assinatura,
// cada traço é separado por ";" e cada ponto tem x e y entre 0 e 1.

data class SignatureData(
    val aspect: Float,
    val strokes: List<List<Pair<Float, Float>>>
) {
    val isEmpty: Boolean get() = strokes.none { it.isNotEmpty() }
}

private fun number(value: Float): String = String.format(Locale.ROOT, "%.4f", value)

fun encodeSignature(aspect: Float, strokes: List<List<Pair<Float, Float>>>): String {
    val body = strokes.filter { it.isNotEmpty() }.joinToString(";") { stroke ->
        stroke.joinToString(" ") { (x, y) -> number(x.coerceIn(0f, 1f)) + "," + number(y.coerceIn(0f, 1f)) }
    }
    return number(if (aspect > 0f) aspect else 2f) + "|" + body
}

/** Lê uma assinatura gravada; texto inválido vira assinatura vazia. */
fun decodeSignature(text: String): SignatureData {
    val parts = text.split("|", limit = 2)
    if (parts.size != 2) return SignatureData(2f, emptyList())
    val aspect = parts[0].toFloatOrNull()?.takeIf { it > 0f } ?: 2f
    val strokes = parts[1].split(";").mapNotNull { stroke ->
        val points = stroke.trim().split(" ").mapNotNull { point ->
            val xy = point.split(",")
            val x = xy.getOrNull(0)?.toFloatOrNull()
            val y = xy.getOrNull(1)?.toFloatOrNull()
            if (xy.size == 2 && x != null && y != null) x to y else null
        }
        points.takeIf { it.isNotEmpty() }
    }
    return SignatureData(aspect, strokes)
}
