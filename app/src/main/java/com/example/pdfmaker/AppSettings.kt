package com.example.pdfmaker
import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat

object AppSettings {
    const val DEFAULT_ACCENT = 0xFF1A73E8.toInt()
    val ACCENT_CHOICES = intArrayOf(0xFF1A73E8.toInt(),0xFF00897B.toInt(),0xFF2E7D32.toInt(),0xFFEF6C00.toInt(),0xFF6A1B9A.toInt())
    private const val KEY = "accent"
    @Volatile var accent: Int = DEFAULT_ACCENT; private set
    fun load(c: Context) { val s = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(KEY, DEFAULT_ACCENT); accent = if (ACCENT_CHOICES.contains(s)) s else DEFAULT_ACCENT }
    fun setAccent(c: Context, color: Int) { accent = color; c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt(KEY, color).apply() }
    fun selectedCard(c: Context) = GradientDrawable().apply {
        setColor(ContextCompat.getColor(c, R.color.card_bg))
        setStroke((3*c.resources.displayMetrics.density).toInt(), accent)
        cornerRadius = 14*c.resources.displayMetrics.density
    }
}
