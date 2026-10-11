package com.example.pdfmaker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class BrowseActivity : AppCompatActivity() {

    private lateinit var box: LinearLayout
    private lateinit var tvCount: TextView
    private var query = ""

    private val exportTpl = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) doExport(uri) }
    private val importTpl = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) doImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        supportActionBar?.title = "Template Library"
        box = findViewById(R.id.browseBox)
        tvCount = findViewById(R.id.tvCount)

        findViewById<Button>(R.id.btnManageTpl).setOnClickListener {
            startActivity(Intent(this, TemplatesActivity::class.java)) }
        findViewById<Button>(R.id.btnExportTpl).setOnClickListener { exportTpl.launch("pdfmaker_templates.json") }
        findViewById<Button>(R.id.btnImportTpl).setOnClickListener {
            importTpl.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; rebuild() }
        })
        rebuild()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun selectable() = try {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        ContextCompat.getDrawable(this, tv.resourceId)
    } catch (e: Exception) { null }

    private fun sectionHeader(s: String) = TextView(this).apply {
        text = s; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(16), dp(18), dp(16), dp(4)) }

    private fun docCard(s: DocSpec) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        foreground = selectable()
        isClickable = true; isFocusable = true
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(12); marginEnd = dp(12); topMargin = dp(6) }
        addView(TextView(context).apply { text = s.icon; textSize = 22f })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            addView(TextView(context).apply {
                text = s.label; textSize = 15f; setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(context).apply {
                text = s.desc; textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary)) })
        })
        addView(TextView(context).apply {
            text = "›"; textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary)) })
        setOnClickListener {
            startActivity(Intent(this@BrowseActivity, CreateActivity::class.java).putExtra("type", s.id))
        }
    }

    private fun rebuild() {
        box.removeAllViews()
        val specs = PdfCatalog.all.filter {
            query.isEmpty() || it.label.contains(query, true) || it.desc.contains(query, true) }
        tvCount.text = "${specs.size} of ${PdfCatalog.count()} types" +
                if (query.isNotEmpty()) "  matching \"$query\"" else ""
        if (query.isEmpty()) {
            val recents = RecentTypes.list(this).mapNotNull { PdfCatalog.byId[it] }
            if (recents.isNotEmpty()) {
                box.addView(sectionHeader("Recently used"))
                recents.forEach { box.addView(docCard(it)) }
            }
        }
        Category.entries.forEach { cat ->
            val items = specs.filter { it.cat == cat }
            if (items.isEmpty()) return@forEach
            box.addView(sectionHeader(cat.label))
            items.forEach { box.addView(docCard(it)) }
        }
        if (specs.isEmpty()) box.addView(TextView(this).apply {
            text = "Nothing matches \"$query\""; textSize = 14f
            setPadding(dp(16), dp(24), dp(16), dp(24)) })
    }

    private fun doExport(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(TemplateStore.exportJson(this).toByteArray()) }
            Toast.makeText(this, "Templates exported ✓", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
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
        } catch (e: Exception) { Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
