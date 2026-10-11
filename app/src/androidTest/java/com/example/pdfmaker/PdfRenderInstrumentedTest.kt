package com.example.pdfmaker

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfRenderInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun makePdf(pages: Int): File {
        val doc = PdfDocument()
        repeat(pages) { i ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            p.canvas.drawColor(Color.WHITE)
            p.canvas.drawText("Page ${i + 1}", 50f, 100f, Paint())
            doc.finishPage(p) }
        val f = File(context.cacheDir, "test_${System.nanoTime()}.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close(); return f }

    @Test fun pageCountReadsCorrectly() {
        val f = makePdf(3)
        assertEquals(3, pdfPageCount(context.contentResolver, Uri.fromFile(f))); f.delete() }
    @Test fun renderPageProducesBitmap() {
        val f = makePdf(1)
        withRenderer(context.contentResolver, Uri.fromFile(f)) { r ->
            r.openPage(0).use { p ->
                val bmp = renderPage(p, 2f)
                assertEquals(1190, bmp.width); assertEquals(1684, bmp.height); bmp.recycle() } }
        f.delete() }
    @Test fun coverRenderWorks() {
        val f = makePdf(2)
        assertNotNull(renderCover(context.contentResolver, Uri.fromFile(f))); f.delete() }
    @Test fun mergeDoublesPages() {
        val a = makePdf(2); val b = makePdf(3)
        val merged = mergePdfDocuments(context.contentResolver, listOf(Uri.fromFile(a), Uri.fromFile(b)))
        try { assertEquals(5, merged.pages.size) }
        finally { merged.close(); a.delete(); b.delete() } }
    @Test fun writerProducesRenderableInvoice() {
        val w = PdfWriter()
        PdfFactory.invoice(w, InvoiceData("INV-1", "$", "Biz", "", "Client", "",
            listOf(InvoiceItem("Item", 1.0, 9.99)), 10.0, ""))
        val out = java.io.ByteArrayOutputStream(); w.writeTo(out); w.close()
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(out.toByteArray()).use { assertEquals(1, it.numberOfPages) } }
    @Test fun wrapRespectsWidth() {
        val w = PdfWriter()
        val lines = w.wrap("word ".repeat(200).trim(), w.body, 200f)
        assertTrue("expected wrapping", lines.size > 5)
        lines.forEach { assertTrue("line too wide: '$it'", w.body.measureText(it) <= 200f + 0.5f) }
        w.close() }
    @Test fun textDocPageAccounting() {
        val w = PdfWriter()
        PdfFactory.textDoc(w, "T", "Para.\n\n".repeat(200), emptyList(), context.contentResolver)
        val out = java.io.ByteArrayOutputStream(); w.writeTo(out); w.close()
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(out.toByteArray()).use {
            assertTrue("long text should paginate", it.numberOfPages > 1) } }
}
