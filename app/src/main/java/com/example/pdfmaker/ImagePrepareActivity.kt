package com.example.pdfmaker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Stub implementation of ImagePrepareActivity. Replace with the full feature screen. */
class ImagePrepareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "ImagePrepareActivity\n\nThis screen is a placeholder — wire up the full feature here."
            textSize = 16f
            setPadding(48, 96, 48, 96)
            gravity = android.view.Gravity.CENTER
        }
        setContentView(tv)
        supportActionBar?.title = "ImagePrepareActivity"
    }
}
