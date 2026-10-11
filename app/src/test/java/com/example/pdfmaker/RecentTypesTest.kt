package com.example.pdfmaker
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentTypesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clear() = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    @Test fun mostRecentFirst_noDuplicates() {
        RecentTypes.record(context, "A"); RecentTypes.record(context, "B"); RecentTypes.record(context, "A")
        assertEquals(listOf("A", "B"), RecentTypes.list(context)) }
    @Test fun cappedAt6() {
        ('A'..'H').forEach { RecentTypes.record(context, "$it") }
        assertEquals(6, RecentTypes.list(context).size)
        assertEquals(listOf("H","G","F","E","D","C"), RecentTypes.list(context)) }
}
