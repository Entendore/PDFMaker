package com.example.pdfmaker
import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceDataTest {
    private val items = listOf(InvoiceItem("Widget", 2.0, 10.0), InvoiceItem("Gadget", 1.0, 5.5))
    @Test fun totals() {
        val d = InvoiceData("INV-1", "$", "Biz", "", "Client", "", items, 10.0, "")
        assertEquals(25.5, d.subtotal, 1e-9); assertEquals(2.55, d.tax, 1e-9); assertEquals(28.05, d.total, 1e-9) }
    @Test fun zeroTax() {
        val d = InvoiceData("INV-1", "$", "Biz", "", "Client", "", items, 0.0, "")
        assertEquals(d.subtotal, d.total, 1e-9) }
}
