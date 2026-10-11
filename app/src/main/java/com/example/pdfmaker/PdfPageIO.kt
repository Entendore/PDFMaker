package com.example.pdfmaker
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.util.Locale

fun withRenderer(resolver: ContentResolver, uri: Uri, block: (PdfRenderer) -> Unit) {
    val pfd = resolver.openFileDescriptor(uri, "r") ?: throw Exception("Cannot open PDF (is it a valid, unencrypted PDF?)")
    try { PdfRenderer(pfd).use(block) } finally { try { pfd.close() } catch (_: Exception) {} }
}
fun renderPage(page: PdfRenderer.Page, scale: Float = 2f, maxPixels: Int = 8_000_000): Bitmap {
    val base = page.width * page.height
    var s = scale
    if (base * s * s > maxPixels) s = kotlin.math.sqrt(maxPixels / base.toFloat())
    val bmp = Bitmap.createBitmap((page.width*s).toInt().coerceAtLeast(1), (page.height*s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    page.render(bmp, null, Matrix().apply { setScale(s, s) }, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
    return bmp
}
fun renderCover(resolver: ContentResolver, uri: Uri, targetWidth: Float = 120f): Bitmap? = try {
    var thumb: Bitmap? = null
    withRenderer(resolver, uri) { r -> if (r.pageCount > 0) r.openPage(0).use { p ->
        val s = targetWidth / p.width
        val b = Bitmap.createBitmap((p.width*s).toInt().coerceAtLeast(1), (p.height*s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE); p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); thumb = b } }
    thumb
} catch (e: Exception) { null }
fun pdfPageCount(resolver: ContentResolver, uri: Uri): Int = try {
    var n = 0; withRenderer(resolver, uri) { n = it.pageCount }; n } catch (e: Exception) { 0 }
fun mergePdfDocuments(resolver: ContentResolver, uris: List<Uri>, onPageDone: (Int) -> Unit = {}): PdfDocument {
    val merged = PdfDocument()
    try { var out = 0
        for (uri in uris) withRenderer(resolver, uri) { r ->
            for (i in 0 until r.pageCount) r.openPage(i).use { page ->
                val pg = merged.startPage(PdfDocument.PageInfo.Builder(page.width, page.height, ++out).create())
                val bmp = renderPage(page)
                pg.canvas.drawBitmap(bmp, null, RectF(0f,0f,page.width.toFloat(),page.height.toFloat()), null)
                bmp.recycle(); merged.finishPage(pg); onPageDone(out) } }
    } catch (e: Exception) { merged.close(); throw e }
    return merged
}
fun decodeImageScaled(resolver: ContentResolver, uri: Uri, maxDim: Int = 1600): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var s = 1; while (maxOf(bounds.outWidth, bounds.outHeight)/(s*2) >= maxDim) s *= 2
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
} catch (e: Exception) { null }
