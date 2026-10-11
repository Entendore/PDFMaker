package com.example.pdfmaker

import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private data class Card(val icon: String, val title: String, val desc: String, val open: () -> Unit)
    private data class RecentFile(val id: Long, val uri: Uri, val name: String,
        val dateAdded: Long, val sizeBytes: Long, val relPath: String)

    private enum class SortMode(val label: String) {
        NEW("Newest"), OLD("Oldest"), NAME("Name A–Z"), SIZE("Largest")
    }

    private lateinit var createGrid: LinearLayout
    private lateinit var toolsGrid: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var recentsBox: LinearLayout
    private lateinit var folderChipsRow: LinearLayout
    private lateinit var selectionBar: View
    private lateinit var tvSelCount: TextView
    private lateinit var tvRecentsHint: TextView
    private lateinit var tvStatsLine: TextView
    private lateinit var etSearchRecents: EditText
    private lateinit var btnSort: Button
    private lateinit var colorDot: View
    private lateinit var swLock: SwitchCompat
    private var suppressLockCallback = false

    private val recentFiles = mutableListOf<RecentFile>()
    private val thumbs = HashMap<Long, Bitmap>()
    private val folders = mutableListOf<Pair<String, String>>()
    private var selectedFolder: String? = null
    private var sortMode = SortMode.NEW
    private var selectMode = false
    private val selectedIds = mutableSetOf<Long>()
    private var colorDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        setContentView(R.layout.activity_main)

        createGrid = findViewById(R.id.createGrid)
        toolsGrid = findViewById(R.id.toolsGrid)
        recentsBox = findViewById(R.id.recentsBox)
        folderChipsRow = findViewById(R.id.folderChipsRow)
        selectionBar = findViewById(R.id.selectionBar)
        tvSelCount = findViewById(R.id.tvSelCount)
        tvRecentsHint = findViewById(R.id.tvRecentsHint)
        tvStatsLine = findViewById(R.id.tvStatsLine)
        etSearchRecents = findViewById(R.id.etSearchRecents)
        btnSort = findViewById(R.id.btnSort)
        colorDot = findViewById(R.id.colorDot)
        colorDot.setBackgroundColor(AppSettings.accent)

        chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        findViewById<HorizontalScrollView>(R.id.chipsHost).addView(chipsRow)

        swLock = findViewById(R.id.swLock)
        swLock.isChecked = AppLock.isEnabled(this)
        swLock.setOnCheckedChangeListener { _, checked ->
            if (suppressLockCallback) return@setOnCheckedChangeListener
            applyAppLock(checked)
        }
        findViewById<LinearLayout>(R.id.lockRow).setOnClickListener { swLock.toggle() }

        etSearchRecents.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { selectedIds.clear(); setSelectBar(); renderRecents() }
        })
        btnSort.setOnClickListener { sortDialog() }

        findViewById<LinearLayout>(R.id.settingsRow).setOnClickListener { showColorPicker() }
        findViewById<LinearLayout>(R.id.statsRow).setOnClickListener {
            startActivity(Intent(this, UsageActivity::class.java))
        }

        buildSelectionBar()
        buildHome()
        handleIntent(intent, savedInstanceState != null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleIntent(intent, false)
    }

    private fun handleIntent(intent: Intent?, isRecreate: Boolean) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return
        if (isRecreate) { consumeIntent(); return }
        val type = intent.type ?: return
        val streams = streamUris(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        when {
            type.startsWith("application/pdf") ->
                if (streams.size == 1) sharedPdf(streams[0]) else openMerge(streams)
            type.startsWith("image/") ->
                if (streams.isNotEmpty()) startActivity(Intent(this, ImagePrepareActivity::class.java).apply {
                    putParcelableArrayListExtra("uris", ArrayList(streams)) })
            type.startsWith("text/plain") && !text.isNullOrBlank() ->
                startActivity(Intent(this, CreateActivity::class.java).apply {
                    putExtra("type", "TEXT"); putExtra("sharedText", text.take(20000)) })
        }
        consumeIntent()
    }

    private fun consumeIntent() = setIntent(Intent(this, MainActivity::class.java))

    @Suppress("DEPRECATION")
    private fun streamUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }

    private fun sharedPdf(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Shared PDF received")
            .setItems(arrayOf(
                "Fill & sign (type on it)",
                "Reorder / extract pages",
                "Convert pages to JPG",
                "Preview & save a copy",
                "Open in Edit Hub"
            )) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, FillPdfActivity::class.java).putExtra("uri", uri.toString()))
                    1 -> startActivity(Intent(this, ReorderActivity::class.java).putExtra("uri", uri.toString()))
                    2 -> startActivity(Intent(this, ConvertActivity::class.java).apply {
                        putExtra("auto", "PDF_IMAGES"); putExtra("uri", uri.toString()) })
                    3 -> try {
                        val f = File(cacheDir, "incoming_${System.currentTimeMillis()}.pdf")
                        contentResolver.openInputStream(uri)?.use { ins -> FileOutputStream(f).use { ins.copyTo(it) } }
                        startActivity(Intent(this, PreviewActivity::class.java).apply {
                            putExtra(PreviewActivity.EXTRA_FILE, f.absolutePath)
                            putExtra(PreviewActivity.EXTRA_NAME, displayName(uri)) })
                    } catch (e: Exception) { toast("Could not read the shared file") }
                    4 -> startActivity(Intent(this, EditHubActivity::class.java).putExtra("uri", uri))
                }
            }.show()
    }

    private fun openMerge(uris: List<Uri>) {
        startActivity(Intent(this, MergeFilesActivity::class.java).apply {
            putStringArrayListExtra("uris", ArrayList(uris.map(Uri::toString)))
        })
    }

    private fun displayName(u: Uri): String = try {
        contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) ?: "PDF" else "PDF" } ?: "PDF"
    } catch (e: Exception) { "PDF" }

    private fun buildHome() {
        createGrid.removeAllViews()
        val types = listOf(
            Triple("📄", "Text Document", "Notes, letters & images") to "TEXT",
            Triple("🧾", "Invoice / Receipt", "Items, tax & totals") to "INVOICE",
            Triple("👤", "Resume / CV", "Clean, modern layout") to "RESUME",
            Triple("🏆", "Certificate", "Award with fancy border") to "CERTIFICATE",
            Triple("📦", "Packing List", "Shipments & weights") to "PACKING",
            Triple("🗓", "Meeting Minutes", "Agenda & actions") to "MINUTES")
        types.chunked(2).forEach { pair ->
            createGrid.addView(rowOf(pair.map { (info, id) ->
                Card(info.first, info.second, info.third) { openSpec(id) } }))
        }
        toolsGrid.removeAllViews()
        val tools = listOf(
            Card("📷", "Scan", "Camera to PDF") { startActivity(Intent(this, ScanActivity::class.java)) },
            Card("📝", "Open & Edit", "Fill, sign, reorder") { startActivity(Intent(this, EditHubActivity::class.java)) },
            Card("🔄", "Convert", "Merge, split, compress…") { startActivity(Intent(this, ConvertActivity::class.java)) },
            Card("📚", "Library", "100 document types") { startActivity(Intent(this, BrowseActivity::class.java)) })
        tools.chunked(2).forEach { pair -> toolsGrid.addView(rowOf(pair)) }
    }

    private fun rowOf(cards: List<Card>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        cards.forEachIndexed { i, card ->
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                foreground = selectable()
                isClickable = true; isFocusable = true
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = if (i == 1) dp(6) else 0; marginEnd = if (i == 0) dp(6) else 0 }
                addView(TextView(context).apply { text = card.icon; textSize = 24f })
                addView(TextView(context).apply {
                    text = card.title; textSize = 14f
                    setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(6), 0, 0) })
                addView(TextView(context).apply {
                    text = card.desc; textSize = 11f; maxLines = 1
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary)) })
                setOnClickListener { card.open() }
            })
        }
        return row
    }

    private fun showColorPicker() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            AppSettings.ACCENT_CHOICES.forEach { color ->
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(color)
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                        .apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                    setOnClickListener {
                        AppSettings.setAccent(this@MainActivity, color)
                        colorDot.setBackgroundColor(color)
                        PdfWidgetProvider.updateAll(this@MainActivity)
                        colorDialog?.dismiss(); toast("Accent updated")
                    }
                })
            }
        })
        colorDialog = AlertDialog.Builder(this)
            .setTitle("Accent color").setView(box)
            .setNegativeButton("Cancel", null).show()
    }

    override fun onResume() {
        super.onResume()
        setSwitchSilently(AppLock.isEnabled(this))
        populateRecentChips()
        loadRecents()
        refreshStats()
    }

    private fun setSwitchSilently(checked: Boolean) {
        suppressLockCallback = true; swLock.isChecked = checked; suppressLockCallback = false
    }

    private fun applyAppLock(on: Boolean) {
        if (on) {
            if (!AppLock.canAuthenticate(this)) {
                setSwitchSilently(false)
                toast("Set up a screen lock first — Settings → Security (PIN or fingerprint)")
                return
            }
            AppLock.setEnabled(this, true); setSwitchSilently(true); toast("App lock is on ✓")
        } else {
            AppLock.promptVerify(this, "Turn off App lock",
                onOk = { AppLock.setEnabled(this, false); setSwitchSilently(false); toast("App lock is off") },
                onFail = { setSwitchSilently(true); toast("Not verified — App lock stays on") })
        }
    }

    private fun openSpec(id: String) = startActivity(
        Intent(this, CreateActivity::class.java).putExtra("type", id))

    private fun populateRecentChips() {
        val specs = RecentTypes.list(this).mapNotNull { PdfCatalog.byId[it] }.take(5)
        chipsRow.removeAllViews()
        findViewById<HorizontalScrollView>(R.id.chipsHost).visibility =
            if (specs.isEmpty()) View.GONE else View.VISIBLE
        specs.forEach { s ->
            chipsRow.addView(TextView(this).apply {
                text = "${s.icon} ${s.label}"; textSize = 13f
                setBackgroundResource(R.drawable.bg_card)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { openSpec(s.id) }
            })
        }
    }

    private fun refreshStats() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val monthStart = cal.timeInMillis / 1000
        Thread {
            var count = 0; var bytes = 0L
            try {
                contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    arrayOf("Download/PDFMaker/%"), null)?.use { c ->
                    while (c.moveToNext()) if (c.getLong(0) >= monthStart) { count++; bytes += c.getLong(1) }
                }
            } catch (_: Exception) {}
            runOnUiThread {
                tvStatsLine.text = if (count == 0) "No PDFs created this month"
                else "This month: $count PDF${if (count == 1) "" else "s"} • ${PdfLogic.fmtFileSize(bytes)}"
            }
        }.start()
    }

    private fun loadRecents() {
        val files = mutableListOf<RecentFile>()
        try {
            contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("Download/PDFMaker/%"), null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val name = c.getString(1) ?: continue
                    files.add(RecentFile(id, ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                        name, c.getLong(2), c.getLong(3), c.getString(4) ?: ""))
                }
            }
        } catch (_: Exception) {}
        recentFiles.clear(); recentFiles.addAll(files)
        if (thumbs.size > 60) thumbs.clear()
        folders.clear()
        recentFiles.map { folderLabel(it.relPath) }.filter { it.isNotBlank() }
            .distinct().sorted().forEach { folders.add(it to "Download/PDFMaker/$it") }
        selectedIds.retainAll(recentFiles.map { it.id }.toSet())
        populateFolderChips(); renderRecents()
    }

    private fun folderLabel(relPath: String) =
        relPath.removePrefix("Download/PDFMaker/").removeSuffix("/")

    private fun populateFolderChips() {
        folderChipsRow.removeAllViews()
        fun chip(label: String, selected: Boolean, onClick: () -> Unit) =
            folderChipsRow.addView(TextView(this).apply {
                text = label; textSize = 13f
                if (selected) background = AppSettings.selectedCard(this@MainActivity)
                else setBackgroundResource(R.drawable.bg_card)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { onClick() }
            })
        chip("All", selectedFolder == null) {
            selectedFolder = null; exitSelect(); populateFolderChips(); renderRecents() }
        folders.forEach { (label, _) ->
            chip("📁 $label", selectedFolder == label) {
                selectedFolder = label; exitSelect(); populateFolderChips(); renderRecents() }
        }
    }

    private fun sortDialog() {
        val labels = SortMode.entries.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setSingleChoiceItems(labels, SortMode.entries.indexOf(sortMode)) { d, w ->
                sortMode = SortMode.entries[w]; btnSort.text = "⇅ ${sortMode.label}"
                d.dismiss(); renderRecents()
            }.show()
    }

    private fun renderRecents() {
        recentsBox.removeAllViews()
        val q = etSearchRecents.text.toString().trim()
        val inFolder = recentFiles.filter {
            selectedFolder == null || folderLabel(it.relPath) == selectedFolder }
        val matches = inFolder.filter { q.isEmpty() || it.name.contains(q, true) }.let { list ->
            when (sortMode) {
                SortMode.NEW -> list.sortedByDescending { it.dateAdded }
                SortMode.OLD -> list.sortedBy { it.dateAdded }
                SortMode.NAME -> list.sortedBy { it.name.lowercase() }
                SortMode.SIZE -> list.sortedByDescending { it.sizeBytes }
            }
        }
        tvRecentsHint.text = when {
            recentFiles.isEmpty() -> "PDFs you create appear here"
            q.isNotEmpty() -> "${matches.size} match(es) for \"$q\""
            else -> "${matches.size} file(s) • ${PdfLogic.fmtFileSize(matches.sumOf { it.sizeBytes })} • long-press to select"
        }
        fun note(s: String) = recentsBox.addView(TextView(this).apply {
            text = s; textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(dp(12), dp(16), dp(12), dp(16)) })
        if (recentFiles.isEmpty()) { note("No PDFs yet — create your first one above!"); return }
        if (matches.isEmpty()) { note("Nothing matches \"$q\""); return }
        matches.take(20).forEach { rf ->
            val row = layoutInflater.inflate(R.layout.row_recent, recentsBox, false)
            row.findViewById<TextView>(R.id.tvName).text = rf.name
            val fl = folderLabel(rf.relPath)
            row.findViewById<TextView>(R.id.tvMeta).text =
                (if (fl.isNotBlank() && selectedFolder == null) "📁 $fl • " else "") + fmtMeta(rf)
            val iv = row.findViewById<ImageView>(R.id.ivThumb)
            thumbs[rf.id]?.let { iv.setImageBitmap(it) } ?: loadThumb(rf, iv)
            if (selectMode && selectedIds.contains(rf.id))
                row.background = AppSettings.selectedCard(this)
            row.setOnClickListener {
                if (selectMode) {
                    if (!selectedIds.remove(rf.id)) selectedIds.add(rf.id)
                    setSelectBar(); renderRecents()
                } else PdfSaver.open(this, rf.uri)
            }
            row.setOnLongClickListener {
                if (!selectMode) { selectMode = true; selectedIds.add(rf.id); setSelectBar(); renderRecents() }
                true
            }
            row.findViewById<View>(R.id.btnMore).setOnClickListener {
                if (!selectMode) showFileMenu(rf) }
            recentsBox.addView(row)
        }
        if (matches.size > 20) note("Showing first 20 — refine your search to see more")
    }

    private fun loadThumb(rf: RecentFile, iv: ImageView) {
        iv.tag = rf.id
        Thread {
            val bmp = renderCover(contentResolver, rf.uri) ?: return@Thread
            runOnUiThread {
                if (iv.tag == rf.id && !isDestroyed) { thumbs[rf.id] = bmp; iv.setImageBitmap(bmp) }
            }
        }.start()
    }

    private fun buildSelectionBar() {
        findViewById<Button>(R.id.btnShareSel).setOnClickListener { batchShare() }
        findViewById<Button>(R.id.btnMergeSel).setOnClickListener { batchMerge() }
        findViewById<Button>(R.id.btnMoveSel).setOnClickListener { batchMove() }
        findViewById<Button>(R.id.btnDeleteSel).setOnClickListener { batchDelete() }
        findViewById<Button>(R.id.btnCancelSel).setOnClickListener { exitSelect(); renderRecents() }
    }

    private fun setSelectBar() {
        selectionBar.visibility = if (selectMode) View.VISIBLE else View.GONE
        tvSelCount.text = "${selectedIds.size} selected"
    }

    private fun exitSelect() { selectMode = false; selectedIds.clear(); setSelectBar() }

    private fun selFiles() = recentFiles.filter { selectedIds.contains(it.id) }

    private fun batchShare() {
        val files = selFiles(); if (files.isEmpty()) return
        PdfSaver.shareMultiple(this, files.map { it.uri })
    }

    private fun batchMerge() {
        val files = selFiles()
        if (files.size < 2) { toast("Select at least 2 files to merge"); return }
        openMerge(files.map { it.uri })
    }

    private fun batchDelete() {
        val files = selFiles(); if (files.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete ${files.size} file(s)")
            .setMessage("Delete the selected PDFs permanently?")
            .setPositiveButton("Delete") { _, _ ->
                var ok = 0
                files.forEach { f ->
                    try { if (contentResolver.delete(f.uri, null, null) > 0) ok++ } catch (_: Exception) {}
                    thumbs.remove(f.id)
                }
                exitSelect(); loadRecents(); toast("Deleted $ok of ${files.size}")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun batchMove() {
        val files = selFiles(); if (files.isEmpty()) return
        val options = ArrayList(folders.map { "📁 ${it.first}" })
        options.add("＋ New folder…")
        AlertDialog.Builder(this)
            .setTitle("Move ${files.size} file(s) to…")
            .setItems(options.toTypedArray()) { _, w ->
                if (w == options.size - 1) newFolderThenMove(files)
                else doMove(files, folders[w].second)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun newFolderThenMove(files: List<RecentFile>) {
        val input = EditText(this).apply { hint = "Folder name"; setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("New folder").setView(input)
            .setPositiveButton("Create & Move") { _, _ ->
                val clean = input.text.toString().trim()
                    .replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { null }
                if (clean == null) { toast("Enter a valid folder name"); return@setPositiveButton }
                doMove(files, "Download/PDFMaker/$clean")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun doMove(files: List<RecentFile>, folder: String) {
        var ok = 0
        files.forEach { f -> if (PdfSaver.moveFile(this, f.uri, folder)) ok++ }
        exitSelect(); loadRecents()
        toast("Moved $ok of ${files.size} to ${folder.removePrefix("Download/PDFMaker/")}")
    }

    private fun showFileMenu(rf: RecentFile) {
        AlertDialog.Builder(this)
            .setTitle(rf.name)
            .setItems(arrayOf("Open", "Share", "Print", "Rename", "Details",
                "Compress a copy", "Move to folder", "Delete")) { _, w ->
                when (w) {
                    0 -> PdfSaver.open(this, rf.uri)
                    1 -> PdfSaver.share(this, rf.uri, rf.name)
                    2 -> try {
                        val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                        pm.print(rf.name.removeSuffix(".pdf"),
                            PdfPrintAdapter(contentResolver, rf.uri, rf.name),
                            PrintAttributes.Builder().build())
                    } catch (e: Exception) { toast("Couldn't start printing") }
                    3 -> renameDialog(rf)
                    4 -> detailsDialog(rf)
                    5 -> startActivity(Intent(this, ConvertActivity::class.java).apply {
                        putExtra("auto", "COMPRESS"); putExtra("uri", rf.uri.toString()) })
                    6 -> { selectedIds.clear(); selectedIds.add(rf.id); batchMove() }
                    7 -> confirmDelete(rf)
                }
            }.show()
    }

    private fun renameDialog(rf: RecentFile) {
        val input = EditText(this).apply { setSingleLine(); setText(rf.name.removeSuffix(".pdf")) }
        AlertDialog.Builder(this)
            .setTitle("Rename").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val clean = input.text.toString().trim()
                    .replace(Regex("[^A-Za-z0-9 _()-]"), "").ifBlank { null } ?: run {
                    toast("Enter a valid name"); return@setPositiveButton }
                try {
                    contentResolver.update(rf.uri, android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "$clean.pdf") }, null, null)
                    loadRecents(); toast("Renamed ✓")
                } catch (e: Exception) { toast("Can't rename this file from the app") }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun detailsDialog(rf: RecentFile) {
        Thread {
            val pages = pdfPageCount(contentResolver, rf.uri)
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Details")
                    .setMessage("Name: ${rf.name}\nFolder: ${folderLabel(rf.relPath).ifBlank { "PDFMaker" }}\n" +
                            "Pages: $pages\nSize: ${PdfLogic.fmtFileSize(rf.sizeBytes)}\n" +
                            "Created: ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)
                                .format(Date(rf.dateAdded * 1000))}")
                    .setPositiveButton("OK", null).show()
            }
        }.start()
    }

    private fun confirmDelete(rf: RecentFile) {
        AlertDialog.Builder(this)
            .setTitle("Delete PDF")
            .setMessage("Delete \"${rf.name}\" permanently?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    contentResolver.delete(rf.uri, null, null)
                    thumbs.remove(rf.id); loadRecents(); toast("Deleted")
                } catch (e: Exception) { toast("Can't delete this file from the app") }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun fmtMeta(rf: RecentFile) =
        "${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(rf.dateAdded * 1000))} • ${PdfLogic.fmtFileSize(rf.sizeBytes)}"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun selectable() = try {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        ContextCompat.getDrawable(this, tv.resourceId)
    } catch (e: Exception) { null }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
