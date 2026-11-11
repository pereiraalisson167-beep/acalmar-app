package br.com.acalmar.agenda.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import br.com.acalmar.agenda.R
import br.com.acalmar.agenda.data.BusinessInfo
import br.com.acalmar.agenda.data.Consent
import br.com.acalmar.agenda.data.ConsentModel
import br.com.acalmar.agenda.data.Quote
import br.com.acalmar.agenda.data.QuoteItem
import br.com.acalmar.agenda.domain.businessContact
import br.com.acalmar.agenda.domain.decodeSignature
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateLong
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.quoteNumber
import br.com.acalmar.agenda.domain.quoteSubtotal
import br.com.acalmar.agenda.domain.quoteTotal
import java.io.File
import kotlin.math.max
import kotlin.math.min

// Desenho dos documentos que saem do app: imagem do orçamento, PDF do termo de consentimento
// e imagem de comparação "antes e depois". Tudo com as cores e a fonte do logo.

private val CREAM = Color.rgb(0xFB, 0xF5, 0xEE)
private val BEIGE = Color.rgb(0xF5, 0xDB, 0xC1)
private val GOLD = Color.rgb(0xD2, 0xA6, 0x7A)
private val BRONZE = Color.rgb(0x90, 0x5B, 0x2B)
private val BRONZE_DARK = Color.rgb(0x6E, 0x42, 0x1C)
private val BROWN = Color.rgb(0x4A, 0x2F, 0x1A)
private val BROWN_SOFT = Color.rgb(0x7A, 0x5E, 0x49)
private val LINE = Color.rgb(0xEB, 0xD9, 0xC5)

private fun serif(context: Context): Typeface =
    runCatching { ResourcesCompat.getFont(context, R.font.marcellus_regular) }.getOrNull() ?: Typeface.SERIF

private fun textPaint(size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT, spacing: Float = 0f): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
        letterSpacing = spacing
    }

private fun textBlock(
    text: CharSequence,
    paint: TextPaint,
    width: Int,
    align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    lineExtra: Float = 0f
): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, max(width, 1))
        .setAlignment(align)
        .setLineSpacing(lineExtra, 1f)
        .setIncludePad(false)
        .build()

/** Desenha um bloco de texto com o canto superior esquerdo em (x, y) e devolve a altura usada. */
private fun Canvas.drawBlock(layout: StaticLayout, x: Float, y: Float): Float {
    save()
    translate(x, y)
    layout.draw(this)
    restore()
    return layout.height.toFloat()
}

private fun linePaint(color: Int, width: Float): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = width
        style = Paint.Style.STROKE
    }

private fun fillPaint(color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

private fun Canvas.drawRounded(bitmap: Bitmap, src: Rect?, dst: RectF, radius: Float) {
    save()
    clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
    drawBitmap(bitmap, src, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    restore()
}

private fun logoBitmap(context: Context, maxWidth: Int): Bitmap? {
    val full = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.logo_acalmar) }.getOrNull()
        ?: return null
    if (full.width <= maxWidth) return full
    val height = full.height * maxWidth / full.width
    return Bitmap.createScaledBitmap(full, maxWidth, height, true)
}

// ---------------------------------------------------------------------------------------------
// Orçamento (imagem)
// ---------------------------------------------------------------------------------------------

/** Imagem do orçamento, com logo, itens, total, validade e contato. */
fun renderQuote(context: Context, quote: Quote, items: List<QuoteItem>, business: BusinessInfo): Bitmap {
    val width = 1080
    val margin = 72f
    val contentWidth = (width - 2 * margin).toInt()
    val right = width - margin
    val serif = serif(context)
    val bold = Typeface.DEFAULT_BOLD

    val picture = Picture()
    val c = picture.beginRecording(width, 20000)
    var y = 64f

    logoBitmap(context, 900)?.let { logo ->
        val logoWidth = 560f
        val logoHeight = logoWidth * logo.height / logo.width
        val left = (width - logoWidth) / 2f
        c.drawRounded(logo, null, RectF(left, y, left + logoWidth, y + logoHeight), 40f)
        y += logoHeight + 52f
    }

    y += c.drawBlock(
        textBlock("ORÇAMENTO", textPaint(62f, BROWN, serif, 0.1f), contentWidth, Layout.Alignment.ALIGN_CENTER),
        margin, y
    ) + 10f
    val created = formatDateLong(epochToDate(quote.createdOn))
    val headline = if (quote.id > 0) "${quoteNumber(quote.id)} · $created" else created
    y += c.drawBlock(
        textBlock(headline, textPaint(32f, BROWN_SOFT), contentWidth, Layout.Alignment.ALIGN_CENTER),
        margin, y
    ) + 40f
    c.drawLine(margin, y, right, y, linePaint(GOLD, 2.5f))
    y += 40f

    if (quote.clientName.isNotBlank()) {
        y += c.drawBlock(textBlock("PARA", textPaint(26f, BRONZE, bold, 0.2f), contentWidth), margin, y) + 8f
        y += c.drawBlock(textBlock(quote.clientName.trim(), textPaint(48f, BROWN, serif), contentWidth), margin, y) + 36f
        c.drawLine(margin, y, right, y, linePaint(LINE, 2f))
        y += 30f
    }

    // Itens: descrição à esquerda, valor à direita.
    val amountPaint = textPaint(36f, BROWN, bold).apply { textAlign = Paint.Align.RIGHT }
    val descriptionPaint = textPaint(36f, BROWN)
    val detailPaint = textPaint(28f, BROWN_SOFT)
    val descriptionWidth = contentWidth - 300
    items.forEach { item ->
        val description = textBlock(item.description.ifBlank { "Item" }, descriptionPaint, descriptionWidth, lineExtra = 4f)
        c.drawText(formatMoney(item.totalCents), right, y - amountPaint.fontMetrics.ascent, amountPaint)
        var rowHeight = c.drawBlock(description, margin, y)
        if (item.quantity > 1) {
            val detail = textBlock("${item.quantity} × ${formatMoney(item.unitCents)}", detailPaint, descriptionWidth)
            rowHeight += 6f
            rowHeight += c.drawBlock(detail, margin, y + rowHeight)
        }
        y += rowHeight + 24f
        c.drawLine(margin, y, right, y, linePaint(LINE, 2f))
        y += 26f
    }

    if (quote.discountCents > 0) {
        val label = textPaint(32f, BROWN_SOFT)
        val value = textPaint(32f, BROWN_SOFT).apply { textAlign = Paint.Align.RIGHT }
        listOf(
            "Subtotal" to formatMoney(quoteSubtotal(items)),
            "Desconto" to "− " + formatMoney(quote.discountCents)
        ).forEach { (name, amount) ->
            val baseline = y - label.fontMetrics.ascent
            c.drawText(name, margin, baseline, label)
            c.drawText(amount, right, baseline, value)
            y += (label.fontMetrics.descent - label.fontMetrics.ascent) + 14f
        }
        y += 12f
    }

    // Total em destaque.
    val totalBox = RectF(margin, y, right, y + 128f)
    c.drawRoundRect(totalBox, 30f, 30f, fillPaint(BEIGE))
    val totalLabel = textPaint(44f, BRONZE_DARK, serif)
    val totalValue = textPaint(56f, BRONZE_DARK, serif).apply { textAlign = Paint.Align.RIGHT }
    c.drawText("Total", margin + 40f, totalBox.centerY() - (totalLabel.fontMetrics.ascent + totalLabel.fontMetrics.descent) / 2f, totalLabel)
    c.drawText(
        formatMoney(quoteTotal(items, quote.discountCents)),
        right - 40f,
        totalBox.centerY() - (totalValue.fontMetrics.ascent + totalValue.fontMetrics.descent) / 2f,
        totalValue
    )
    y += 128f + 40f

    quote.validUntil?.let { until ->
        y += c.drawBlock(
            textBlock("Válido até ${formatDateShort(epochToDate(until))}", textPaint(32f, BROWN, bold), contentWidth),
            margin, y
        ) + 18f
    }
    if (quote.notes.isNotBlank()) {
        y += c.drawBlock(textBlock(quote.notes.trim(), textPaint(30f, BROWN_SOFT), contentWidth, lineExtra = 6f), margin, y) + 18f
    }

    y += 22f
    c.drawLine(margin, y, right, y, linePaint(GOLD, 2.5f))
    y += 30f
    val contact = businessContact(business)
    if (contact.isNotBlank()) {
        y += c.drawBlock(
            textBlock(contact, textPaint(32f, BROWN), contentWidth, Layout.Alignment.ALIGN_CENTER),
            margin, y
        ) + 8f
    }
    if (business.name.isNotBlank()) {
        y += c.drawBlock(
            textBlock(business.name.trim(), textPaint(27f, BROWN_SOFT, serif, 0.06f), contentWidth, Layout.Alignment.ALIGN_CENTER),
            margin, y
        )
    }
    y += 60f
    picture.endRecording()

    val bitmap = Bitmap.createBitmap(width, y.toInt().coerceIn(600, 20000), Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(CREAM)
        drawPicture(picture)
    }
    return bitmap
}

// ---------------------------------------------------------------------------------------------
// Termo de consentimento (PDF)
// ---------------------------------------------------------------------------------------------

/** Grava o termo assinado em PDF (tamanho A4, com quantas páginas forem necessárias). */
fun writeConsentPdf(context: Context, consent: Consent, business: BusinessInfo, out: File) {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 54f
    val contentWidth = (pageWidth - 2 * margin).toInt()
    val bottom = pageHeight - 64f
    val serif = serif(context)
    val contact = businessContact(business)

    val doc = PdfDocument()
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = margin

    fun canvas(): Canvas = page!!.canvas

    fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNumber++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        y = margin
        // Rodapé de todas as páginas.
        val footer = listOf(business.name.trim(), contact).filter { it.isNotBlank() }.joinToString(" · ")
        if (footer.isNotBlank()) {
            canvas().drawBlock(
                textBlock(footer, textPaint(8.5f, BROWN_SOFT), contentWidth, Layout.Alignment.ALIGN_CENTER),
                margin, pageHeight - 40f
            )
        }
    }

    fun ensureSpace(needed: Float) {
        if (y + needed > bottom) newPage()
    }

    try {
        newPage()

        // Cabeçalho da primeira página.
        logoBitmap(context, 480)?.let { logo ->
            val logoWidth = 132f
            val logoHeight = logoWidth * logo.height / logo.width
            val left = (pageWidth - logoWidth) / 2f
            canvas().drawRounded(logo, null, RectF(left, y, left + logoWidth, y + logoHeight), 10f)
            y += logoHeight + 16f
        }
        y += canvas().drawBlock(
            textBlock(
                "TERMO DE CONSENTIMENTO", textPaint(16f, BROWN, serif, 0.08f), contentWidth,
                Layout.Alignment.ALIGN_CENTER
            ),
            margin, y
        ) + 12f
        canvas().drawLine(margin, y, pageWidth - margin, y, linePaint(GOLD, 1f))
        y += 20f

        // Texto do termo, quebrado em páginas linha a linha.
        val body = textBlock(consent.text, textPaint(10.5f, BROWN), contentWidth, lineExtra = 3.5f)
        var line = 0
        while (line < body.lineCount) {
            val top = body.getLineTop(line)
            var end = line
            while (end < body.lineCount && y + (body.getLineBottom(end) - top) <= bottom) end++
            if (end == line) {
                // Nem uma linha cabe no resto da página.
                if (y <= margin + 1f) end = line + 1 else {
                    newPage()
                    continue
                }
            }
            val sliceBottom = body.getLineBottom(end - 1)
            val c = canvas()
            c.save()
            c.translate(margin, y - top)
            c.clipRect(0f, top.toFloat(), contentWidth.toFloat(), sliceBottom.toFloat())
            body.draw(c)
            c.restore()
            y += (sliceBottom - top).toFloat()
            line = end
            if (line < body.lineCount) newPage()
        }
        y += 16f

        // Uso de imagem, data e assinatura ficam juntos na mesma página.
        val imageLine = textBlock(
            ConsentModel.imageLine(consent.imageUse), textPaint(10.5f, BROWN, Typeface.DEFAULT_BOLD), contentWidth,
            lineExtra = 3f
        )
        val signature = decodeSignature(consent.signature)
        val signatureWidth = 250f
        val signatureHeight = min(signatureWidth / signature.aspect, 110f)
        ensureSpace(imageLine.height + 26f + 16f + signatureHeight + 60f)

        y += canvas().drawBlock(imageLine, margin, y) + 18f
        y += canvas().drawBlock(
            textBlock("Data: ${formatDateLong(epochToDate(consent.signedOn))}", textPaint(10.5f, BROWN), contentWidth),
            margin, y
        ) + 18f

        // Assinatura: os traços feitos na tela, encaixados na largura reservada.
        val drawWidth = min(signatureWidth, signatureHeight * signature.aspect)
        val left = margin
        val stroke = linePaint(BROWN, 1.3f).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dot = fillPaint(BROWN)
        signature.strokes.forEach { points ->
            if (points.size == 1) {
                canvas().drawCircle(left + points[0].first * drawWidth, y + points[0].second * signatureHeight, 0.8f, dot)
            } else {
                val path = Path()
                points.forEachIndexed { index, (px, py) ->
                    val x = left + px * drawWidth
                    val yy = y + py * signatureHeight
                    if (index == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                canvas().drawPath(path, stroke)
            }
        }
        y += signatureHeight + 4f
        canvas().drawLine(margin, y, margin + signatureWidth, y, linePaint(BROWN_SOFT, 0.8f))
        y += 6f
        if (consent.signerName.isNotBlank()) {
            y += canvas().drawBlock(textBlock(consent.signerName.trim(), textPaint(10.5f, BROWN), contentWidth), margin, y) + 2f
        }
        canvas().drawBlock(textBlock("Assinatura da cliente", textPaint(8.5f, BROWN_SOFT), contentWidth), margin, y)

        page?.let { doc.finishPage(it) }
        page = null
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
}

/** Transforma as páginas de um PDF em imagens, para mostrar na tela. */
fun renderPdfPages(file: File, widthPx: Int): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        val renderer = PdfRenderer(descriptor)
        try {
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                try {
                    val height = widthPx * page.height / page.width
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages += bitmap
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
        }
    }
    return pages
}

// ---------------------------------------------------------------------------------------------
// Antes e depois (imagem)
// ---------------------------------------------------------------------------------------------

/** Imagem com as duas fotos lado a lado, etiquetas "Antes" e "Depois" e o nome do negócio. */
fun renderComparison(
    context: Context,
    before: Bitmap,
    after: Bitmap,
    beforeCaption: String,
    afterCaption: String,
    business: BusinessInfo
): Bitmap {
    val panelWidth = 960
    val panelHeight = 1280
    val pad = 30
    val gap = 20
    val footer = if (business.name.isBlank()) 0 else 110
    val width = pad * 2 + panelWidth * 2 + gap
    val height = pad * 2 + panelHeight + footer
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    c.drawColor(CREAM)
    val serif = serif(context)

    fun panel(source: Bitmap, left: Float, label: String, caption: String) {
        val dst = RectF(left, pad.toFloat(), left + panelWidth, (pad + panelHeight).toFloat())
        // Corta a foto para preencher o quadro sem deformar.
        val scale = max(dst.width() / source.width, dst.height() / source.height)
        val cropWidth = (dst.width() / scale).toInt().coerceAtMost(source.width)
        val cropHeight = (dst.height() / scale).toInt().coerceAtMost(source.height)
        val cropLeft = (source.width - cropWidth) / 2
        val cropTop = (source.height - cropHeight) / 2
        c.drawRounded(source, Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight), dst, 36f)

        val tagPaint = textPaint(40f, Color.WHITE, Typeface.DEFAULT_BOLD, 0.12f)
        val text = if (caption.isBlank()) label else "$label · $caption"
        val textWidth = tagPaint.measureText(text)
        val tag = RectF(dst.left + 28f, dst.bottom - 28f - 76f, dst.left + 28f + textWidth + 56f, dst.bottom - 28f)
        c.drawRoundRect(tag, 38f, 38f, fillPaint(Color.argb(190, 0x4A, 0x2F, 0x1A)))
        c.drawText(text, tag.left + 28f, tag.centerY() - (tagPaint.fontMetrics.ascent + tagPaint.fontMetrics.descent) / 2f, tagPaint)
    }

    panel(before, pad.toFloat(), "ANTES", beforeCaption)
    panel(after, (pad + panelWidth + gap).toFloat(), "DEPOIS", afterCaption)

    if (footer > 0) {
        val name = textBlock(
            business.name.trim(), textPaint(46f, BRONZE_DARK, serif, 0.06f), width - 2 * pad,
            Layout.Alignment.ALIGN_CENTER
        )
        c.drawBlock(name, pad.toFloat(), pad + panelHeight + (footer + pad - name.height) / 2f)
    }
    return bitmap
}
