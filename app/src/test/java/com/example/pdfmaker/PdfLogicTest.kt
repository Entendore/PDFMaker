package com.example.pdfmaker
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLogicTest {
    @Test fun parsePages_singleAndRanges() = assertEquals(listOf(0,2,4,5,6,7), PdfLogic.parsePages("1,3,5-8"))
    @Test fun parsePages_reversedRange() = assertEquals(listOf(5,6,7), PdfLogic.parsePages("8-6"))
    @Test fun parsePages_ignoresJunkZeroAndDuplicates() = assertEquals(listOf(0,3), PdfLogic.parsePages("1, 0, abc, 4, 4"))
    @Test fun parsePages_empty() = assertEquals(emptyList<Int>(), PdfLogic.parsePages(""))
    @Test fun padTo4() { assertEquals(8, PdfLogic.padTo4(5)); assertEquals(4, PdfLogic.padTo4(4))
        assertEquals(4, PdfLogic.padTo4(1)); assertEquals(12, PdfLogic.padTo4(9)) }
    @Test fun bookletSides_classicImposition() =
        assertEquals(listOf(8 to 1, 2 to 7, 6 to 3, 4 to 5), PdfLogic.bookletSides(8))
    @Test fun bookletSides_oneSheet() = assertEquals(listOf(4 to 1, 2 to 3), PdfLogic.bookletSides(4))
    @Test fun batesLabel_withPrefix() = assertEquals("EXHIBIT-0007", PdfLogic.batesLabel("EXHIBIT", 7, 4))
    @Test fun batesLabel_withoutPrefix() = assertEquals("0007", PdfLogic.batesLabel("", 7, 4))
    @Test fun batesDigits_growsWithRange() { assertEquals(4, PdfLogic.batesDigits(9999)); assertEquals(5, PdfLogic.batesDigits(12000)) }
    @Test fun sanitizeFileName() { assertEquals("Q1 Report", PdfLogic.sanitizeFileName("Q1: Report?"))
        assertEquals("Document", PdfLogic.sanitizeFileName("///")); assertEquals("ab", PdfLogic.sanitizeFileName("abcdefgh", 2)) }
    @Test fun fmtFileSize() { assertEquals("999 B", PdfLogic.fmtFileSize(999))
        assertEquals("2 KB", PdfLogic.fmtFileSize(1500)); assertEquals("2.5 MB", PdfLogic.fmtFileSize(2500000)) }
}
