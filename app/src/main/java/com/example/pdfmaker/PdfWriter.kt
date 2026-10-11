package com.example.pdfmaker

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextUtils
import java.io.OutputStream

class PdfWriter(private val landscape: Boolean = false, private val footer: Boolean = true) {

    companion object {
        const val MARGIN = 50f
        val DARK = Color.parseColor("#202124")
        val GRAY = Color.parseColor("#5F6368")
        val LIGHT = Color.parseColor("#DADCE0")
        val FILL = 0x0F5F6368
    }

    val accentColor = AppSettings.accent
    val pageW = if (landscape) 842f else 595f
    val pageH = if (landscape) 595f else 842f
    val contentW = pageW - MARGIN * 2
    private val bottomLimit = pageH - MARGIN - if (footer) 30f else 0f

    val h1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK; textSize = 26f; isFakeBoldText = true }
    val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; textSize = 13f; isFakeBoldText = true }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK; textSize = 12f }
    val bodyBold = Paint(body).apply { isFakeBoldText = true }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRAY; textSize = 10f }
    val thin = Paint().apply { color = LIGHT; strokeWidth = 1f }
    val accent = Paint().apply { color = accentColor; strokeWidth = 2.5f }

    private val pdf = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var pageNo = 0
    var y = 0f
    val c: Canvas get() = canvas ?: throw IllegalStateException("No page started")

    fun newPage(headerTitle: String? = null, headerSubtitle: String? = null) {
        page?.let { pdf.finishPage(it) }
        page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), ++pageNo).create())
        canvas = page!!.canvas
        y = MARGIN
        if (footer) drawFooter()
        if (headerTitle != null) drawHeader(headerTitle, headerSubtitle)
    }

    private fun drawHeader(t: String, sub: String?) {
        c.drawText(t, MARGIN, y + h1.textSize, h1); y += h1.textSize + 8f
        if (!sub.isNullOrBlank()) { c.drawText(sub, MARGIN, y + small.textSize, small); y += small.textSize + 8f }
        c.drawLine(MARGIN, y, pageW - MARGIN, y, accent); y += 20f
    }

    private fun drawFooter() {
        val fy = pageH - MARGIN + 16f
        c.drawLine(MARGIN, fy - 4f, pageW - MARGIN, fy - 4f, thin)
        val pn = "Page $pageNo"
        c.drawText(pn, pageW - MARGIN - small.measureText(pn), fy + small.textSize, small)
    }

    fun ensureSpace(h: Float) { if (y + h > bottomLimit) newPage() }
    fun spacer(h: Float) { y += h }
    fun rule() { c.drawLine(MARGIN, y + 4f, pageW - MARGIN, y + 4f, thin); y += 12f }

    fun text(s: String, paint: Paint = body, indent: Float = 0f, spacing: Float = 5f) {
        for (l in wrap(s, paint, contentW - indent)) {
            ensureSpace(paint.textSize + spacing)
            c.drawText(l, MARGIN + indent, y + paint.textSize, paint)
            y += paint.textSize + spacing
        }
    }

    fun heading(s: String) {
        ensureSpace(h2.textSize + 34f); y += 14f
        c.drawText(s.uppercase(), MARGIN, y + h2.textSize, h2); y += h2.textSize + 5f
        c.drawLine(MARGIN, y, pageW - MARGIN, y, thin); y += 10f
    }

    fun bullet(s: String, paint: Paint = body) {
        wrap(s, paint, contentW - 16f).forEachIndexed { i, l ->
            ensureSpace(paint.textSize + 4f)
            if (i == 0) c.drawText("•", MARGIN + 2f, y + paint.textSize, paint)
            c.drawText(l, MARGIN + 16f, y + paint.textSize, paint)
            y += paint.textSize + 4f
        }
    }

    fun numbered(n: Int, s: String, paint: Paint = body) {
        val indent = 20f
        wrap(s, paint, contentW - indent).forEachIndexed { i, l ->
            ensureSpace(paint.textSize + 4f)
            if (i == 0) c.drawText("$n.", MARGIN + 2f, y + paint.textSize, paint)
            c.drawText(l, MARGIN + indent, y + paint.textSize, paint)
            y += paint.textSize + 4f
        }
    }

    fun keyValue(label: String, value: String, bold: Boolean = false) {
        val p = if (bold) bodyBold else body
        ensureSpace(p.textSize + 6f)
        c.drawText(label, MARGIN, y + p.textSize, body)
        c.drawText(value, pageW - MARGIN - p.measureText(value), y + p.textSize, p)
        y += p.textSize + 6f
    }

    fun totalRow(label: String, value: String) {
        ensureSpace(32f)
        c.drawRect(MARGIN, y, pageW - MARGIN, y + 26f, Paint().apply { color = FILL })
        c.drawText(label, MARGIN + 8f, y + 17.5f, bodyBold)
        c.drawText(value, pageW - MARGIN - 8f - bodyBold.measureText(value), y + 17.5f, bodyBold)
        y += 32f
    }

    fun table(headers: List<String>, rows: List<List<String>>, weights: FloatArray,
              rightCols: Set<Int> = emptySet()) {
        val widths = weights.map { contentW * it / weights.sum() }
        val rowH = 22f
        fun drawRow(cells: List<String>, p: Paint, shade: Boolean) {
            if (shade) c.drawRect(MARGIN, y, pageW - MARGIN, y + rowH, Paint().apply { color = FILL })
            var x = MARGIN
            cells.forEachIndexed { i, cell ->
                val shown = TextUtils.ellipsize(cell, p, widths[i] - 12f, TextUtils.TruncateAt.END).toString()
                val tx = if (i in rightCols) x + widths[i] - 6f - p.measureText(shown) else x + 6f
                c.drawText(shown, tx, y + rowH / 2 + p.textSize / 2 - 2.5f, p)
                x += widths[i]
            }
            var vx = MARGIN
            for (i in 0..cells.size) { c.drawLine(vx, y, vx, y + rowH, thin); if (i < cells.size) vx += widths[i] }
            c.drawLine(MARGIN, y + rowH, pageW - MARGIN, y + rowH, thin)
            y += rowH
        }
        drawRow(headers, bodyBold, shade = true)
        rows.forEach { cells ->
            if (y + rowH > bottomLimit) { newPage(); drawRow(headers, bodyBold, true) }
            drawRow(cells, body, false)
        }
    }

    fun twoColumns(lTitle: String, lLines: List<String>, rTitle: String, rLines: List<String>) {
        ensureSpace(130f)
        val half = contentW / 2f - 14f
        val L = lLines.flatMap { wrap(it, body, half) }
        val R = rLines.flatMap { wrap(it, body, half) }
        val colX = MARGIN + contentW / 2f + 14f
        var ly = y
        c.drawText(lTitle.uppercase(), MARGIN, ly + h2.textSize, h2)
        c.drawText(rTitle.uppercase(), colX, ly + h2.textSize, h2)
        ly += h2.textSize + 7f
        repeat(maxOf(L.size, R.size)) { i ->
            L.getOrNull(i)?.let { c.drawText(it, MARGIN, ly + body.textSize, if (i == 0) bodyBold else body) }
            R.getOrNull(i)?.let { c.drawText(it, colX, ly + body.textSize, if (i == 0) bodyBold else body) }
            ly += body.textSize + 5f
        }
        y = ly + 4f
    }

    fun drawCentered(s: String, paint: Paint, gap: Float = 10f) {
        ensureSpace(paint.textSize + gap)
        c.drawText(s, (pageW - paint.measureText(s)) / 2f, y + paint.textSize, paint)
        y += paint.textSize + gap
    }

    fun drawCenteredWrapped(s: String, paint: Paint, gap: Float = 8f) {
        for (l in wrap(s, paint, contentW - 120f)) drawCentered(l, paint, gap)
    }

    fun signatureLine(label: String, width: Float = 240f) {
        ensureSpace(56f); y += 26f
        val pen = Paint().apply { color = DARK; strokeWidth = 1f }
        c.drawLine(MARGIN, y, MARGIN + width, y, pen)
        c.drawText(label, MARGIN, y + 14f, small)
        y += 26f
    }

    fun image(uri: Uri, resolver: ContentResolver, maxH: Float = 420f) {
        val bmp = decode(uri, resolver) ?: return
        val scale = minOf(contentW / bmp.width, minOf(maxH / bmp.height, 1f))
        val w = bmp.width * scale; val h = bmp.height * scale
        ensureSpace(h + 12f)
        c.drawBitmap(bmp, null, RectF(MARGIN, y, MARGIN + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
        y += h + 12f; bmp.recycle()
    }

    private fun decode(uri: Uri, resolver: ContentResolver): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
        var s = 1
        while (b.outWidth / (s * 2) >= 1400 || b.outHeight / (s * 2) >= 1400) s *= 2
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    } catch (e: Exception) { null }

    fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.split('\n')) {
            if (raw.isBlank()) { out.add(""); continue }
            var line = ""
            for (word in raw.trim().split(' ')) {
                val cand = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(cand) <= maxWidth) line = cand
                else {
                    if (line.isNotEmpty()) out.add(line)
                    line = if (paint.measureText(word) <= maxWidth) word else hardBreak(word, paint, maxWidth, out)
                }
            }
            out.add(line)
        }
        return out
    }

    private fun hardBreak(word: String, paint: Paint, maxWidth: Float, out: MutableList<String>): String {
        var part = ""
        for (ch in word) {
            if (paint.measureText(part + ch) > maxWidth && part.isNotEmpty()) { out.add(part); part = "$ch" } else part += ch
        }
        return part
    }

    fun writeTo(out: OutputStream) { page?.let { pdf.finishPage(it); page = null }; pdf.writeTo(out) }
    fun close() = pdf.close()
}
