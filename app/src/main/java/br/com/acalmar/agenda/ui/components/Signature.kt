package br.com.acalmar.agenda.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.domain.SignatureData
import br.com.acalmar.agenda.domain.encodeSignature
import br.com.acalmar.agenda.ui.theme.Brand

/** O que a cliente desenhou na área de assinatura. */
class SignatureState {
    val strokes = mutableStateListOf<List<Offset>>()
    var current by mutableStateOf<List<Offset>>(emptyList())
    var size by mutableStateOf(IntSize.Zero)

    val isEmpty: Boolean get() = strokes.isEmpty() && current.isEmpty()

    fun clear() {
        strokes.clear()
        current = emptyList()
    }

    /** Assinatura em texto, com os pontos convertidos para valores entre 0 e 1. */
    fun encode(): String {
        val width = size.width.coerceAtLeast(1).toFloat()
        val height = size.height.coerceAtLeast(1).toFloat()
        return encodeSignature(width / height, strokes.map { stroke -> stroke.map { (it.x / width) to (it.y / height) } })
    }
}

private fun DrawScope.drawStroke(points: List<Offset>, color: Color, width: Float) {
    if (points.size == 1) {
        drawCircle(color, radius = width / 2f, center = points[0])
    } else if (points.size > 1) {
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Área onde a cliente assina com o dedo. */
@Composable
fun SignaturePad(state: SignatureState, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Canvas(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Brand.Gold, shape)
            .semantics { contentDescription = "Área de assinatura" }
            .onSizeChanged { state.size = it }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    state.current = listOf(down.position)
                    drag(down.id) { change ->
                        state.current = state.current + change.position
                        change.consume()
                    }
                    if (state.current.isNotEmpty()) state.strokes.add(state.current)
                    state.current = emptyList()
                }
            }
    ) {
        // Linha de apoio para a assinatura.
        val lineY = size.height * 0.8f
        drawLine(Brand.Line, Offset(24.dp.toPx(), lineY), Offset(size.width - 24.dp.toPx(), lineY), 1.dp.toPx())
        val width = 2.5.dp.toPx()
        state.strokes.forEach { drawStroke(it, Brand.Brown, width) }
        drawStroke(state.current, Brand.Brown, width)
    }
}

/** Mostra uma assinatura já gravada, encaixada no espaço disponível. */
@Composable
fun SignatureView(data: SignatureData, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val aspect = if (data.aspect > 0f) data.aspect else 2f
        var drawWidth = size.width
        var drawHeight = drawWidth / aspect
        if (drawHeight > size.height) {
            drawHeight = size.height
            drawWidth = drawHeight * aspect
        }
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f
        val width = 2.dp.toPx()
        data.strokes.forEach { stroke ->
            drawStroke(stroke.map { (x, y) -> Offset(left + x * drawWidth, top + y * drawHeight) }, Brand.Brown, width)
        }
    }
}
