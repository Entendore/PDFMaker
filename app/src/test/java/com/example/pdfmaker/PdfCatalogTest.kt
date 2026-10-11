package com.example.pdfmaker
import org.junit.Assert.*
import org.junit.Test

class PdfCatalogTest {
    @Test fun hasExactly100Types() = assertEquals(100, PdfCatalog.all.size)
    @Test fun idsAreUnique() = assertEquals(PdfCatalog.all.size, PdfCatalog.all.map { it.id }.toSet().size)
    @Test fun everySpecHasLabelDescIcon() = PdfCatalog.all.forEach { s ->
        assertTrue("${s.id} label", s.label.isNotBlank()); assertTrue("${s.id} desc", s.desc.isNotBlank())
        assertTrue("${s.id} icon", s.icon.isNotBlank()) }
    @Test fun legacyTypesAreRoutedAsCustom() {
        val legacy = setOf("TEXT","INVOICE","RESUME","CERTIFICATE","PACKING","MINUTES","FLYER")
        assertEquals(legacy, PdfCatalog.all.filter { it.layout == DocLayout.CUSTOM }.map { it.id }.toSet()) }
    @Test fun totalTypesRequireTable() = PdfCatalog.all.forEach { s ->
        if (s.totals) assertTrue("${s.id} uses totals without a table", s.table != null) }
    @Test fun byIdLookupMatchesList() = PdfCatalog.all.forEach { assertEquals(it, PdfCatalog.byId[it.id]) }
}
