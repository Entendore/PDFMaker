package com.example.pdfmaker
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clear() = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    @Test fun defaultAccent() { AppSettings.load(context); assertEquals(AppSettings.DEFAULT_ACCENT, AppSettings.accent) }
    @Test fun paletteChoicePersists() {
        AppSettings.ACCENT_CHOICES.forEach { c ->
            AppSettings.setAccent(context, c); AppSettings.load(context)
            assertEquals(c, AppSettings.accent) } }
    @Test fun removedAccentResetsToDefault() {
        AppSettings.setAccent(context, 0xFFC62828.toInt()); AppSettings.load(context)
        assertEquals(AppSettings.DEFAULT_ACCENT, AppSettings.accent) }
}
