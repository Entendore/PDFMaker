package com.example.pdfmaker
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FillMemoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clear() = context.getSharedPreferences("fill_memory", Context.MODE_PRIVATE).edit().clear().commit()
    private val ann = FillMemory.SavedAnn(0, 0.1f, 0.2f, "Hello", 14f, 0.3f,
        listOf(listOf(0.1f, 0.2f), listOf(0.3f, 0.4f)), Color.BLUE)
    @Test fun keyIsStable() {
        assertEquals(FillMemory.keyOf("w4", 12345L, 2), FillMemory.keyOf("w4", 12345L, 2))
        assertNotEquals(FillMemory.keyOf("w4", 12345L, 2), FillMemory.keyOf("w4", 99999L, 2)) }
    @Test fun saveFindDelete() {
        val key = FillMemory.keyOf("form", 1L, 1)
        FillMemory.save(context, key, "form", "content://x", mapOf(0 to listOf(ann)))
        val found = FillMemory.find(context, key)!!
        assertEquals("form", found.name); assertEquals(1, found.entryCount())
        val a = found.pages[0]!![0]
        assertEquals("Hello", a.text); assertEquals(Color.BLUE, a.color); assertEquals(2, a.points.size)
        FillMemory.delete(context, key)
        assertNull(FillMemory.find(context, key)) }
    @Test fun signatureRoundTrip() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        FillMemory.saveSignature(context, bmp)
        val loaded = FillMemory.loadSignature(context)
        assertNotNull(loaded); assertEquals(4, loaded!!.width) }
}
