package com.example.pdfmaker

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CreateActivity : AppCompatActivity() {

    private lateinit var spec: DocSpec
    private val fieldValues = mutableMapOf<String, String>()
    private val rowEditors = mutableListOf<Triple<EditText, EditText, EditText>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val typeId = intent.getStringExtra("type") ?: "TEXT"
        spec = PdfCatalog.byId[typeId] ?: PdfCatalog.byId["TEXT"]!!
        supportActionBar?.title = spec.label
        AppSettings.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val titleInput = EditText(this).apply {
            hint = "Document title"; setSingleLine()
            setText(intent.getStringExtra("sharedText")?.take(80) ?: spec.label)
        }
        root.addView(TextView(this).apply {
            text = "Title"; textSize = 13f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(titleInput)

        spec.fields.forEach { fd ->
            root.addView(TextView(this).apply {
                text = fd.label; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(12), 0, 0)
            })
            val et = EditText(this).apply {
                hint = fd.label
                inputType = if (fd.multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else if (fd.num) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    else InputType.TYPE_CLASS_TEXT
                if (fd.prefill != null) setText(fd.prefill)
                if (fd.multi) minLines = 2
            }
            fieldValues[fd.key] = fd.prefill ?: ""
            et.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    fieldValues[fd.key] = s?.toString() ?: ""
                }
            })
            root.addView(et)
        }

        spec.table?.let { t ->
            root.addView(TextView(this).apply {
                text = t.title; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            })
            repeat(t.blankRows.coerceAtLeast(3)) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val a = EditText(this).apply { hint = t.c1; setSingleLine() }
                val b = EditText(this).apply { hint = t.c2; setSingleLine()
                    inputType = if (t.num2) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        else InputType.TYPE_CLASS_TEXT }
                val c = EditText(this).apply { hint = t.c3; setSingleLine()
                    inputType = if (t.num3) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        else InputType.TYPE_CLASS_TEXT }
                row.addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
                row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                root.addView(row)
                rowEditors.add(Triple(a, b, c))
            }
        }

        TemplateStore.defaultId(this, spec.id)?.let { id ->
            TemplateStore.listAll(this).firstOrNull { it.id == id }?.let { tpl ->
                tpl.fields.forEach { (k, v) -> fieldValues[k] = v }
                RecentTypes.record(this, spec.id)
            }
        }

        val btnPreview = Button(this).apply { text = "Preview PDF" }
        btnPreview.setOnClickListener {
            val vals = fieldValues.toMutableMap().apply { put("docTitle", titleInput.text.toString()) }
            val rows = rowEditors.map { Triple(it.first.text.toString(), it.second.text.toString(), it.third.text.toString()) }
            val file = generatePdf(vals, rows)
            RecentTypes.record(this, spec.id)
            UsageStats.recordType(this, spec.id)
            startActivity(Intent(this, PreviewActivity::class.java).apply {
                putExtra(PreviewActivity.EXTRA_FILE, file.absolutePath)
                putExtra(PreviewActivity.EXTRA_NAME,
                    "${PdfLogic.sanitizeFileName(titleInput.text.toString())}.pdf")
            })
        }
        root.addView(btnPreview)

        val btnSaveTpl = Button(this).apply { text = "💾 Save as template" }
        btnSaveTpl.setOnClickListener {
            val name = EditText(this).apply { hint = "Template name"; setSingleLine() }
            android.app.AlertDialog.Builder(this)
                .setTitle("Save template")
                .setView(name)
                .setPositiveButton("Save") { _, _ ->
                    val n = name.text.toString().trim().ifBlank { spec.label }
                    TemplateStore.save(this, n, spec.id, fieldValues.toMap(), emptyMap())
                    Toast.makeText(this, "Template saved: $n", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null).show()
        }
        root.addView(btnSaveTpl)

        val scroll = android.widget.ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun generatePdf(vals: Map<String, String>, rows: List<Triple<String, String, String>>): java.io.File {
        val w = PdfWriter(landscape = spec.landscape, footer = spec.footer)
        try {
            when (spec.layout) {
                DocLayout.CUSTOM -> when (spec.id) {
                    "TEXT" -> PdfFactory.textDoc(w, vals["docTitle"] ?: spec.label,
                        vals["body"] ?: intent.getStringExtra("sharedText") ?: "",
                        emptyList(), contentResolver)
                    else -> PdfFactory.specDoc(w, spec, vals, rows)
                }
                else -> PdfFactory.specDoc(w, spec, vals, rows)
            }
            val f = java.io.File(cacheDir, "create_${System.currentTimeMillis()}.pdf")
            java.io.FileOutputStream(f).use { w.writeTo(it) }
            return f
        } finally { w.close() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
