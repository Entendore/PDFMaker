package com.example.pdfmaker
import java.util.Locale

object PdfLogic {
    fun parsePages(input: String): List<Int> {
        val out = sortedSetOf<Int>()
        for (part in input.split(',', ' ')) {
            val p = part.trim(); if (p.isEmpty()) continue
            val r = Regex("(\\d+)\\s*-\\s*(\\d+)").matchEntire(p)
            if (r != null) {
                val a = r.groupValues[1].toInt(); val b = r.groupValues[2].toInt()
                for (i in minOf(a,b)..maxOf(a,b)) if (i >= 1) out.add(i-1)
            } else p.toIntOrNull()?.let { if (it >= 1) out.add(it-1) }
        }
        return out.toList()
    }
    fun padTo4(n: Int) = ((n + 3) / 4) * 4
    fun bookletSides(padded: Int): List<Pair<Int,Int>> = (0 until padded/2).map { p ->
        val l = if (p % 2 == 0) padded - p else p + 1
        val r = if (p % 2 == 0) p + 1 else padded - p
        l to r
    }
    fun batesDigits(maxNumber: Int) = maxOf(4, maxNumber.toString().length)
    fun batesLabel(prefix: String, number: Int, digits: Int) =
        (if (prefix.isEmpty()) "" else "$prefix-") + number.toString().padStart(digits, '0')
    fun sanitizeFileName(name: String, maxLen: Int = 50) =
        name.replace(Regex("[^A-Za-z0-9 _-]"),"").trim().take(maxLen).ifBlank { "Document" }
    fun fmtFileSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.US,"%.1f MB", bytes/1_000_000f)
        bytes >= 1_000 -> String.format(Locale.US,"%.0f KB", bytes/1_000f)
        else -> "$bytes B"
    }
}
