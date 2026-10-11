package com.example.pdfmaker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Stub implementation of FillPdfActivity. Replace with the full feature screen. */
class FillPdfActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "FillPdfActivity\n\nThis screen is a placeholder — wire up the full feature here."
            textSize = 16f
            setPadding(48, 96, 48, 96)
            gravity = android.view.Gravity.CENTER
        }
        setContentView(tv)
        supportActionBar?.title = "FillPdfActivity"
    }
}
