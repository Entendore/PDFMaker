package com.example.pdfmaker
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageStatsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clear() = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    @Test fun recordsAndRanks() {
        repeat(3) { UsageStats.recordType(context, "INVOICE") }
        UsageStats.recordType(context, "TEXT")
        assertEquals(listOf("INVOICE" to 3, "TEXT" to 1), UsageStats.topTypes(context)) }
    @Test fun timeWindowFiltering() {
        val now = System.currentTimeMillis()
        UsageStats.recordType(context, "INVOICE")
        assertEquals(1, UsageStats.topTypesIn(context, 0, now + 60000).size)
        assertEquals(0, UsageStats.topTypesIn(context, 0, now - 60000).size) }
}
