package com.example.pdfmaker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import java.io.File

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE = "file"
        const val EXTRA_NAME = "name"
        const val EXTRA_PRESET = "preset"
    }

    private lateinit var pdfView: PDFView
    private lateinit var tvName: TextView
    private lateinit var btnSave: Button
    private lateinit var btnDiscard: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        supportActionBar?.title = "Preview"
        pdfView = findViewById(R.id.pdfView)
        tvName = findViewById(R.id.tvPreviewName)
        btnSave = findViewById(R.id.btnSave)
        btnDiscard = findViewById(R.id.btnDiscard)
        progress = findViewById(R.id.progressBar)

        val path = intent.getStringExtra(EXTRA_FILE) ?: run { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Document.pdf"
        val file = File(path)
        tvName.text = name
        progress.visibility = View.VISIBLE
        pdfView.fromFile(file)
            .onLoad { pages -> progress.visibility = View.GONE
                Toast.makeText(this, "$pages page(s)", Toast.LENGTH_SHORT).show() }
            .onError { e -> progress.visibility = View.GONE
                Toast.makeText(this, "Cannot open preview: ${e.message}", Toast.LENGTH_LONG).show() }
            .spacing(2).load()

        btnDiscard.setOnClickListener { finish() }
        btnSave.setOnClickListener {
            val saved = PdfSaver.saveFile(this, file, name)
            if (saved != null) {
                Toast.makeText(this, "Saved to Download/PDFMaker", Toast.LENGTH_LONG).show()
                val openIntent = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(openIntent)
                finish()
            } else Toast.makeText(this, "Could not save", Toast.LENGTH_LONG).show()
        }
    }
}
