package com.example.pdfmaker
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemplateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clear() = context.getSharedPreferences("pdf_templates", Context.MODE_PRIVATE).edit().clear().commit()

    @Test fun saveListDelete() {
        TemplateStore.save(context, "Acme", "INVOICE", mapOf("business" to "Acme Corp", "tax" to "10"), emptyMap())
        val list = TemplateStore.list(context, "INVOICE")
        assertEquals(1, list.size); assertEquals("Acme", list[0].name)
        assertEquals("Acme Corp", list[0].fields["business"])
        TemplateStore.delete(context, list[0].id)
        assertEquals(0, TemplateStore.list(context, "INVOICE").size) }
    @Test fun sameNameSameTypeOverwrites() {
        TemplateStore.save(context, "Acme", "INVOICE", mapOf("tax" to "10"), emptyMap())
        TemplateStore.save(context, "Acme", "INVOICE", mapOf("tax" to "20"), emptyMap())
        val list = TemplateStore.list(context, "INVOICE")
        assertEquals(1, list.size); assertEquals("20", list[0].fields["tax"]) }
    @Test fun sameNameDifferentTypeKeptSeparately() {
        TemplateStore.save(context, "Acme", "INVOICE", mapOf(), emptyMap())
        TemplateStore.save(context, "Acme", "QUOTE", mapOf(), emptyMap())
        assertEquals(1, TemplateStore.list(context, "INVOICE").size)
        assertEquals(1, TemplateStore.list(context, "QUOTE").size) }
    @Test fun defaultSetAndClear() {
        TemplateStore.save(context, "Acme", "INVOICE", mapOf(), emptyMap())
        val id = TemplateStore.list(context, "INVOICE")[0].id
        TemplateStore.setDefault(context, "INVOICE", id)
        assertEquals(id, TemplateStore.defaultId(context, "INVOICE"))
        TemplateStore.setDefault(context, "INVOICE", null)
        assertNull(TemplateStore.defaultId(context, "INVOICE")) }
    @Test fun formStateRoundTrip() {
        val fields = mapOf("a" to "1", "b" to "")
        val rows = mapOf("items" to listOf(Triple("Widget", "2", "9.99")))
        val (f, r) = TemplateStore.decodeForm(TemplateStore.encodeForm(fields, rows))!!
        assertEquals(fields, f); assertEquals(rows, r) }
    @Test fun decodeForm_toleratesGarbage() = assertNull(TemplateStore.decodeForm("{oops"))
    @Test fun renameRejectsBlank() {
        TemplateStore.save(context, "Acme", "INVOICE", mapOf(), emptyMap())
        val id = TemplateStore.list(context, "INVOICE")[0].id
        assertFalse(TemplateStore.rename(context, id, "   ")) }
    @Test fun importRenamesDuplicatesAndSkipsUnknownTypes() {
        TemplateStore.save(context, "Acme", "INVOICE", mapOf("x" to "1"), emptyMap())
        val json = TemplateStore.exportJson(context)
        val r = TemplateStore.importJson(context, json)
        assertEquals(1, r.imported); assertEquals(0, r.skipped)
        assertTrue(TemplateStore.list(context, "INVOICE").any { it.name == "Acme (2)" })
        val bad = """{"app":"PDFMaker","version":1,"templates":[
            {"name":"Ghost","type":"NOT_A_TYPE","fields":{},"rows":{}}]}"""
        val r2 = TemplateStore.importJson(context, bad)
        assertEquals(0, r2.imported); assertEquals(1, r2.skipped) }
}
