package com.example.pdfmaker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Stub implementation of FormFieldsActivity. Replace with the full feature screen. */
class FormFieldsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "FormFieldsActivity\n\nThis screen is a placeholder — wire up the full feature here."
            textSize = 16f
            setPadding(48, 96, 48, 96)
            gravity = android.view.Gravity.CENTER
        }
        setContentView(tv)
        supportActionBar?.title = "FormFieldsActivity"
    }
}
