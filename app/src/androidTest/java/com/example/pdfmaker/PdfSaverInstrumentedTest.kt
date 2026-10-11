package com.example.pdfmaker

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfSaverInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun onePagePdf(): PdfDocument {
        val doc = PdfDocument()
        val p = doc.startPage(PdfDocument.PageInfo.Builder(100, 100, 1).create())
        p.canvas.drawColor(Color.WHITE); doc.finishPage(p); return doc }

    @Test fun savePdfDocument_roundTrip() {
        val doc = onePagePdf()
        val uri = PdfSaver.savePdfDocument(context, doc, "unit_test_${System.nanoTime()}.pdf")
        try {
            assertNotNull(uri)
            context.contentResolver.openInputStream(uri!!)!!.use { assertTrue(it.readBytes().size > 100) }
        } finally { doc.close(); uri?.let { context.contentResolver.delete(it, null, null) } } }

    @Test fun saveIntoSubfolder_landsInRightFolder() {
        val doc = onePagePdf()
        val folder = "${PdfSaver.FOLDER}/unit_test_folder"
        val uri = PdfSaver.savePdfDocument(context, doc, "sub_test.pdf", folder)
        try {
            assertNotNull(uri)
            context.contentResolver.query(uri!!,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c ->
                assertTrue(c.moveToFirst()); assertEquals("$folder/", c.getString(0)) }
        } finally { doc.close(); uri?.let { context.contentResolver.delete(it, null, null) } } }

    @Test fun saveTxt_writesText() {
        val uri = PdfSaver.saveTxt(context, "txt_test.txt", "hello world")
        try {
            assertNotNull(uri)
            context.contentResolver.openInputStream(uri!!)!!.bufferedReader().use {
                assertEquals("hello world", it.readText()) }
        } finally { uri?.let { context.contentResolver.delete(it, null, null) } } }
}
