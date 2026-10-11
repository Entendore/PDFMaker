package com.example.pdfmaker

import android.app.AlertDialog
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class TemplatesActivity : AppCompatActivity() {

    private lateinit var box: LinearLayout
    private lateinit var tvHint: TextView

    private val exportTpl = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) doExport(uri) }
    private val importTpl = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) doImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_templates)
        supportActionBar?.title = "Manage Templates"
        box = findViewById(R.id.tplBox)
        tvHint = findViewById(R.id.tvTplHint)
        findViewById<Button>(R.id.btnExportTpl).setOnClickListener { exportTpl.launch("pdfmaker_templates.json") }
        findViewById<Button>(R.id.btnImportTpl).setOnClickListener {
            importTpl.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        rebuild()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rebuild() {
        box.removeAllViews()
        val all = TemplateStore.listAll(this)
        tvHint.text = "${all.size} template(s) — ✎ rename • ✕ delete • ★ shows the auto-load default"
        if (all.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "No templates yet.\n\nOpen any document form, fill it in, then tap 💾 Save to keep it here."
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@TemplatesActivity, R.color.text_secondary))
                setPadding(dp(16), dp(24), dp(16), dp(24)) })
            return
        }
        PdfCatalog.all.forEach { spec ->
            val items = all.filter { it.typeId == spec.id }
            if (items.isEmpty()) return@forEach
            val defaultId = TemplateStore.defaultId(this, spec.id)
            box.addView(TextView(this).apply {
                text = "${spec.icon}  ${spec.label}"; textSize = 15f
                setTypeface(typeface, Typeface.BOLD); setPadding(dp(16), dp(16), dp(16), dp(4)) })
            items.forEach { t ->
                box.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.bg_card)
                    setPadding(dp(14), dp(10), dp(6), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(12); marginEnd = dp(12); topMargin = dp(6) }
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        addView(TextView(context).apply {
                            text = (if (t.id == defaultId) "★ " else "") + t.name
                            textSize = 14f; setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
                        addView(TextView(context).apply {
                            text = if (t.id == defaultId) "Loads automatically"
                            else "${t.fields.size} field(s) saved"
                            textSize = 11f
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary)) })
                    })
                    addView(TextView(context).apply {
                        text = "✎"; textSize = 16f; setPadding(dp(12), dp(10), dp(12), dp(10))
                        contentDescription = "Rename template"
                        setOnClickListener { renameDialog(t) } })
                    addView(TextView(context).apply {
                        text = "✕"; textSize = 15f; setPadding(dp(12), dp(10), dp(12), dp(10))
                        contentDescription = "Delete template"
                        setOnClickListener {
                            AlertDialog.Builder(context)
                                .setTitle("Delete template").setMessage("Delete \"${t.name}\"?")
                                .setPositiveButton("Delete") { _, _ ->
                                    TemplateStore.delete(this@TemplatesActivity, t.id)
                                    if (TemplateStore.defaultId(this@TemplatesActivity, t.typeId) == t.id)
                                        TemplateStore.setDefault(this@TemplatesActivity, t.typeId, null)
                                    rebuild()
                                }
                                .setNegativeButton("Cancel", null).show() } })
                })
            }
        }
    }

    private fun renameDialog(t: Template) {
        val input = EditText(this).apply { setSingleLine(); setText(t.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename template").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                if (TemplateStore.rename(this, t.id, input.text.toString())) {
                    rebuild(); toast("Renamed ✓")
                } else toast("Enter a name")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun doExport(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(TemplateStore.exportJson(this).toByteArray()) }
            toast("Templates exported ✓")
        } catch (e: Exception) { toast("Export failed: ${e.message}") }
    }

    private fun doImport(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()
                ?.use { it.readText() } ?: ""
            val r = TemplateStore.importJson(this, json)
            rebuild()
            AlertDialog.Builder(this)
                .setTitle("Import complete")
                .setMessage("Imported ${r.imported} template(s)." +
                        if (r.skipped > 0) "\n\nSkipped ${r.skipped} — unknown type or unreadable." else "")
                .setPositiveButton("OK", null).show()
        } catch (e: Exception) { toast("Import failed: ${e.message}") }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
