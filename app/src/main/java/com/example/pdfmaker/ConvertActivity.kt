package com.example.pdfmaker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

class ConvertActivity : AppCompatActivity() {

    private enum class Kind { IMAGES_PDF, PDF_IMAGES, MERGE, SPLIT, TXT_PDF, WEB_PDF,
        COMPRESS, FILL, FORM_FIELDS, BATES, SPLIT_ALL, FIND, NUP, TOC, SCAN, BOOK }
    private var pending: Kind? = null

    private val main = Handler(Looper.getMainLooper())
    private var progress: AlertDialog? = null
    private var webView: WebView? = null
    private var webAdapter: PrintDocumentAdapter? = null

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) startActivity(Intent(this, ImagePrepareActivity::class.java).apply {
            putParcelableArrayListExtra("uris", ArrayList(uris)) }) }

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        when (pending) {
            Kind.SPLIT -> { pending = null
                startActivity(Intent(this, ReorderActivity::class.java).apply {
                    putExtra("uri", uri.toString()); putExtra("name", baseName(uri)) }) }
            Kind.COMPRESS -> { pending = null; askQuality(uri) }
            Kind.FILL -> { pending = null
                startActivity(Intent(this, FillPdfActivity::class.java).putExtra("uri", uri.toString())) }
            Kind.FORM_FIELDS -> { pending = null
                startActivity(Intent(this, FormFieldsActivity::class.java).putExtra("uri", uri.toString())) }
            Kind.PDF_IMAGES -> { pending = null; askJpgMode(uri) }
            Kind.BATES -> { pending = null; askBates(uri) }
            Kind.SPLIT_ALL -> { pending = null; confirmSplitAll(uri) }
            Kind.FIND -> { pending = null; askFind(uri) }
            Kind.NUP -> { pending = null; askNUp(uri) }
            Kind.TOC -> { pending = null
                startActivity(Intent(this, TocActivity::class.java).putExtra("uri", uri.toString())) }
            Kind.BOOK -> { pending = null
                startActivity(Intent(this, BookBuilderActivity::class.java).apply {
                    putStringArrayListExtra("uris", arrayListOf(uri.toString())) }) }
            else -> { pending = null } } }

    private val pickPdfs = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) openMerge(uris) }

    private val pickTxt = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) runPreviewTask { txtToPdf(uri) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_convert)
        supportActionBar?.title = "Convert"
        AppSettings.load(this)
        buildOptions()
        when (intent.getStringExtra("auto")) {
            "IMAGES_PDF" -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, "uris", Uri::class.java).orEmpty()
                if (uris.isNotEmpty()) startActivity(Intent(this, ImagePrepareActivity::class.java).apply {
                    putParcelableArrayListExtra("uris", ArrayList(uris)) }) }
            "PDF_IMAGES" -> intent.getStringExtra("uri")?.let { askJpgMode(Uri.parse(it)) }
            "COMPRESS" -> intent.getStringExtra("uri")?.let { askQuality(Uri.parse(it)) } }
    }

    private fun buildOptions() {
        val box = findViewById<LinearLayout>(R.id.llConvertOptions)
        val options = listOf(
            Triple("🖼", "Images → PDF", "Pick photos — reorder, rotate & combine") to Kind.IMAGES_PDF,
            Triple("🏞", "PDF → Images (JPG)", "Export every page as a JPG picture") to Kind.PDF_IMAGES,
            Triple("➕", "Merge PDFs", "Pick files, drag to reorder, then combine") to Kind.MERGE,
            Triple("✂️", "Reorder & Extract Pages", "Drag pages, pick pages, save a new PDF") to Kind.SPLIT,
            Triple("🗂", "Split → Separate PDFs", "One PDF per page, in its own folder") to Kind.SPLIT_ALL,
            Triple("📝", "Text file → PDF", "Convert a .txt file into a PDF") to Kind.TXT_PDF,
            Triple("🌐", "Webpage → PDF", "Save a URL or pasted HTML as a PDF") to Kind.WEB_PDF,
            Triple("📉", "Compress PDF", "Shrink file size with quality presets") to Kind.COMPRESS,
            Triple("✍️", "Fill / Sign PDF", "Tap anywhere to type on any form") to Kind.FILL,
            Triple("📋", "Fill Form Boxes", "Fill real form boxes — text stays selectable") to Kind.FORM_FIELDS,
            Triple("📜", "Bates Numbering", "Sequential legal stamps: ABC-0001…") to Kind.BATES,
            Triple("🔍", "Find in PDF", "Search text & see which pages mention it") to Kind.FIND,
            Triple("🖨", "N-up Handout", "2-up • 4-up • A5 booklet • custom margins") to Kind.NUP,
            Triple("📑", "Table of Contents", "View, add & edit PDF bookmarks") to Kind.TOC,
            Triple("📷", "Scan → PDF", "Camera capture into the prep screen") to Kind.SCAN,
            Triple("📖", "Book Builder", "Book-size PDF with page numbers & contents") to Kind.BOOK)
        for ((info, kind) in options) {
            val (icon, title, desc) = info
            val pad = (16 * resources.displayMetrics.density).toInt()
            box.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = pad / 2 }
                isClickable = true; isFocusable = true
                addView(TextView(this@ConvertActivity).apply {
                    text = "$icon  $title"; textSize = 17f
                    setTextColor(ContextCompat.getColor(this@ConvertActivity, R.color.text_primary)) })
                addView(TextView(this@ConvertActivity).apply {
                    text = desc; textSize = 13f
                    setTextColor(ContextCompat.getColor(this@ConvertActivity, R.color.text_secondary)) })
                setOnClickListener { start(kind) } }) }
    }

    private fun start(kind: Kind) {
        when (kind) {
            Kind.IMAGES_PDF -> pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            Kind.PDF_IMAGES -> { pending = Kind.PDF_IMAGES; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.MERGE -> pickPdfs.launch(arrayOf("application/pdf"))
            Kind.SPLIT -> { pending = Kind.SPLIT; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.SPLIT_ALL -> { pending = Kind.SPLIT_ALL; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.TXT_PDF -> pickTxt.launch(arrayOf("text/plain"))
            Kind.WEB_PDF -> askWebInput()
            Kind.COMPRESS -> { pending = Kind.COMPRESS; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.FILL -> { pending = Kind.FILL; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.FORM_FIELDS -> { pending = Kind.FORM_FIELDS; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.BATES -> { pending = Kind.BATES; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.FIND -> { pending = Kind.FIND; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.NUP -> { pending = Kind.NUP; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.TOC -> { pending = Kind.TOC; pickPdf.launch(arrayOf("application/pdf")) }
            Kind.SCAN -> startActivity(Intent(this, ScanActivity::class.java))
            Kind.BOOK -> { pending = Kind.BOOK; pickPdf.launch(arrayOf("application/pdf")) } }
    }

    private fun openMerge(uris: List<Uri>) {
        startActivity(Intent(this, MergeFilesActivity::class.java).apply {
            putStringArrayListExtra("uris", ArrayList(uris.map(Uri::toString))) })
    }

    private fun runTask(task: () -> String) {
        showProgress("Converting…")
        Thread {
            try { val summary = task()
                main.post { hideProgress(); doneDialog(summary) }
            } catch (e: Exception) {
                main.post { hideProgress(); toast("Conversion failed: ${e.message}") } }
        }.start()
    }

    private fun runPreviewTask(preset: String? = null, task: () -> Pair<File, String>) {
        showProgress("Converting…")
        Thread {
            try {
                val (file, name) = task()
                main.post {
                    hideProgress()
                    startActivity(Intent(this, PreviewActivity::class.java).apply {
                        putExtra(PreviewActivity.EXTRA_FILE, file.absolutePath)
                        putExtra(PreviewActivity.EXTRA_NAME, name)
                        preset?.let { putExtra(PreviewActivity.EXTRA_PRESET, it) } }) }
            } catch (e: Exception) {
                main.post { hideProgress(); toast("Conversion failed: ${e.message}") } }
        }.start()
    }

    private fun tempPdf(): File = File(cacheDir, "conv_${System.currentTimeMillis()}.pdf")
    private fun PdfWriter.toTemp(f: File) { FileOutputStream(f).use { writeTo(it) } }
    private fun PdfDocument.toTemp(f: File) { FileOutputStream(f).use { writeTo(it) } }

    private fun showProgress(msg: String) {
        hideProgress()
        progress = AlertDialog.Builder(this).setTitle("Please wait").setMessage(msg).setCancelable(false).show() }
    private fun updateProgress(msg: String) { progress?.setMessage(msg) }
    private fun hideProgress() { progress?.let { try { it.dismiss() } catch (_: Exception) {} }; progress = null }
    private fun doneDialog(message: String) {
        AlertDialog.Builder(this).setTitle("Done ✔").setMessage(message).setPositiveButton("OK", null).show() }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun txtToPdf(uri: Uri): Pair<File, String> {
        val raw = contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8) } ?: throw Exception("Cannot read file")
        val name = baseName(uri).ifBlank { "Text" }
        val w = PdfWriter()
        try {
            PdfFactory.textDoc(w, name, raw, emptyList(), contentResolver)
            val f = tempPdf(); w.toTemp(f)
            return f to "${sanitize(name)}_${stamp()}.pdf"
        } finally { w.close() }
    }

    private fun imagesToPdf(uris: List<Uri>): Pair<File, String> {
        val w = PdfWriter()
        try {
            var count = 0
            for (uri in uris) {
                val bmp = decodeScaled(uri) ?: continue
                w.newPage()
                val availH = w.pageH - PdfWriter.MARGIN * 2 - 30f
                val scale = min(w.contentW / bmp.width, min(availH / bmp.height, 1f))
                val dw = bmp.width * scale; val dh = bmp.height * scale
                val x = PdfWriter.MARGIN + (w.contentW - dw) / 2f
                val y = PdfWriter.MARGIN + (availH - dh) / 2f
                w.c.drawBitmap(bmp, null, RectF(x, y, x + dw, y + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                bmp.recycle(); count++ }
            if (count == 0) throw Exception("No readable images selected")
            val name = "Images_${stamp()}.pdf"
            val f = tempPdf(); w.toTemp(f)
            return f to name
        } finally { w.close() }
    }

    private fun askJpgMode(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("How should the pages be saved?")
            .setItems(arrayOf("Separate JPG files", "Single ZIP file")) { _, which ->
                runTask { pdfToImages(uri, zip = which == 1) } }.show()
    }

    private fun pdfToImages(uri: Uri, zip: Boolean): String {
        val base = baseName(uri)
        if (!zip) {
            var count = 0
            withRenderer(contentResolver, uri) { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        main.post { updateProgress("Exporting page ${i + 1} of ${renderer.pageCount}") }
                        val bmp = renderPage(page)
                        val ok = saveImage(bmp, "${base}_p${i + 1}.jpg")
                        bmp.recycle()
                        if (ok) count++ } } }
            if (count == 0) throw Exception("No pages exported")
            return "Exported $count page(s) as JPG\nLocation: ${PdfSaver.FOLDER}"
        }
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${base}_pages.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PdfSaver.FOLDER) }
        val zUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Could not create ZIP")
        var count = 0
        try {
            contentResolver.openOutputStream(zUri)?.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zipOut ->
                    withRenderer(contentResolver, uri) { r ->
                        for (i in 0 until r.pageCount) {
                            main.post { updateProgress("Zipping page ${i + 1} of ${r.pageCount}") }
                            r.openPage(i).use { page ->
                                val bmp = renderPage(page, 1.5f, maxPixels = 6_000_000)
                                zipOut.putNextEntry(ZipEntry("${base}_p${i + 1}.jpg"))
                                bmp.compress(Bitmap.CompressFormat.JPEG, 90, zipOut)
                                zipOut.closeEntry()
                                bmp.recycle(); count++ } } } } }
                    ?: throw Exception("Could not write ZIP")
        } catch (e: Exception) {
            contentResolver.delete(zUri, null, null); throw e }
        if (count == 0) throw Exception("No pages exported")
        return "Zipped $count page(s)\nSaved: ${base}_pages.zip\nLocation: ${PdfSaver.FOLDER}"
    }

    private fun saveImage(bmp: Bitmap, name: String): Boolean {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PdfSaver.FOLDER) }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } ?: return false
        return true
    }

    private data class CompressResult(val file: File, val name: String, val before: Long, val after: Long)

    private fun askQuality(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Compression level")
            .setItems(arrayOf("High quality — best for printing",
                "Balanced — recommended", "Smallest file — best for email")) { _, i ->
                runCompressTask(uri, i) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun runCompressTask(uri: Uri, level: Int) {
        showProgress("Compressing…")
        Thread {
            try {
                val r = compressPdf(uri, level)
                main.post {
                    hideProgress()
                    val pct = if (r.before > 0) (100 - r.after * 100 / r.before).toInt().coerceIn(0, 99) else 0
                    val msg = if (r.after < r.before)
                        "${PdfLogic.fmtFileSize(r.before)}  →  ${PdfLogic.fmtFileSize(r.after)}   ($pct% smaller)"
                    else "No savings — this PDF is already smaller than a rasterized copy.\n" +
                            "Result: ${PdfLogic.fmtFileSize(r.before)}  →  ${PdfLogic.fmtFileSize(r.after)}"
                    AlertDialog.Builder(this).setTitle("Compressed ✓").setMessage(msg)
                        .setPositiveButton("Preview") { _, _ ->
                            startActivity(Intent(this, PreviewActivity::class.java).apply {
                                putExtra(PreviewActivity.EXTRA_FILE, r.file.absolutePath)
                                putExtra(PreviewActivity.EXTRA_NAME, r.name) }) }
                        .setNegativeButton("OK", null).show() }
            } catch (e: Exception) {
                main.post { hideProgress(); toast("Compression failed: ${e.message}") } }
        }.start()
    }

    private fun compressPdf(uri: Uri, level: Int): CompressResult {
        val scale = when (level) { 0 -> 1.0f; 1 -> 0.8f; else -> 0.55f }
        val label = when (level) { 0 -> "High"; 1 -> "Balanced"; else -> "Small" }
        val before = fileSize(uri)
        val out = PdfDocument()
        try {
            var n = 0
            withRenderer(contentResolver, uri) { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val pw = page.width.toFloat(); val ph = page.height.toFloat()
                        val bmp = renderPage(page, scale, maxPixels = 4_000_000)
                        val pg = out.startPage(
                            PdfDocument.PageInfo.Builder(pw.toInt(), ph.toInt(), ++n).create())
                        pg.canvas.drawBitmap(bmp, null, RectF(0f, 0f, pw, ph), null)
                        bmp.recycle(); out.finishPage(pg) } } }
            if (n == 0) throw Exception("No pages found")
            val f = tempPdf()
            FileOutputStream(f).use { out.writeTo(it) }
            return CompressResult(f, "${baseName(uri)}_$label.pdf", before, f.length())
        } finally { out.close() }
    }

    private fun fileSize(uri: Uri): Long =
        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L

    private fun askWatermark(uri: Uri) {
        val input = EditText(this).apply { hint = "Watermark text"; setSingleLine(); setText("CONFIDENTIAL") }
        AlertDialog.Builder(this).setTitle("Watermark text").setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isEmpty()) toast("Enter watermark text")
                else runPreviewTask { watermarkPdf(uri, t) } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun watermarkPdf(uri: Uri, text: String): Pair<File, String> {
        val out = PdfDocument()
        try {
            var n = 0
            withRenderer(contentResolver, uri) { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val pw = page.width.toFloat(); val ph = page.height.toFloat()
                        val bmp = renderPage(page, 1.5f, maxPixels = 6_000_000)
                        val pg = out.startPage(
                            PdfDocument.PageInfo.Builder(page.width, page.height, ++n).create())
                        val c = pg.canvas
                        c.drawBitmap(bmp, null, RectF(0f, 0f, pw, ph), null)
                        bmp.recycle()
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0x555F6368
                            textSize = min(pw, ph) * 0.14f; isFakeBoldText = true }
                        var tw = p.measureText(text)
                        if (tw > pw * 0.92f) { p.textSize *= pw * 0.92f / tw; tw = p.measureText(text) }
                        c.save(); c.rotate(-35f, pw / 2f, ph / 2f)
                        c.drawText(text, pw / 2f - tw / 2f, ph / 2f + p.textSize / 3f, p)
                        c.restore()
                        out.finishPage(pg) } } }
            if (n == 0) throw Exception("No pages found")
            val f = tempPdf(); out.toTemp(f)
            return f to "Watermark_${baseName(uri)}.pdf"
        } finally { out.close() }
    }

    private fun askBates(uri: Uri) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val prefix = EditText(this).apply { hint = "Prefix (e.g. EXHIBIT)"; setSingleLine() }
        val start = EditText(this).apply {
            hint = "First number"; inputType = InputType.TYPE_CLASS_NUMBER; setText("1") }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0)
            addView(prefix); addView(start) }
        AlertDialog.Builder(this).setTitle("Bates numbering").setView(box)
            .setPositiveButton("Next") { _, _ ->
                val n = start.text.toString().toIntOrNull() ?: 1
                if (n < 0) { toast("Start number can't be negative"); return@setPositiveButton }
                val p = prefix.text.toString().trim()
                AlertDialog.Builder(this).setTitle("Stamp position")
                    .setItems(arrayOf("Bottom-right", "Bottom-left", "Top-right", "Top-left")) { _, pos ->
                        runPreviewTask { batesPdf(uri, p, n, pos) } }.show() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun batesPdf(uri: Uri, prefix: String, startNum: Int, pos: Int): Pair<File, String> {
        val out = PdfDocument()
        try {
            var n = 0
            withRenderer(contentResolver, uri) { r ->
                val digits = PdfLogic.batesDigits(startNum + r.pageCount - 1)
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { page ->
                        val pw = page.width.toFloat(); val ph = page.height.toFloat()
                        val bmp = renderPage(page, 2f, maxPixels = 6_000_000)
                        val pg = out.startPage(
                            PdfDocument.PageInfo.Builder(page.width, page.height, ++n).create())
                        val c = pg.canvas
                        c.drawBitmap(bmp, null, RectF(0f, 0f, pw, ph), null)
                        bmp.recycle()
                        val label = PdfLogic.batesLabel(prefix, startNum + i, digits)
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF5F6368.toInt(); textSize = 11f }
                        val m = 24f
                        val x = if (pos == 0 || pos == 2) pw - m - p.measureText(label) else m
                        val y = if (pos == 0 || pos == 1) ph - m else m + p.textSize
                        c.drawText(label, x, y, p)
                        out.finishPage(pg) } } }
            if (n == 0) throw Exception("No pages found")
            val f = tempPdf(); out.toTemp(f)
            return f to "Bates_${baseName(uri)}.pdf"
        } finally { out.close() }
    }

    private fun confirmSplitAll(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Split into separate PDFs")
            .setMessage("Creates one single-page PDF for every page of this document, " +
                    "saved together in their own folder.")
            .setPositiveButton("Split") { _, _ -> runTask { splitAll(uri) } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun splitAll(uri: Uri): String {
        val base = baseName(uri)
        val folder = "${PdfSaver.FOLDER}/${base}_split"
        var created = 0
        withRenderer(contentResolver, uri) { r ->
            for (i in 0 until r.pageCount) {
                main.post { updateProgress("Splitting page ${i + 1} of ${r.pageCount}") }
                r.openPage(i).use { page ->
                    val doc = PdfDocument()
                    try {
                        val pg = doc.startPage(
                            PdfDocument.PageInfo.Builder(page.width, page.height, 1).create())
                        val bmp = renderPage(page, 2f, maxPixels = 6_000_000)
                        pg.canvas.drawBitmap(bmp, null, RectF(
                            0f, 0f, page.width.toFloat(), page.height.toFloat()), null)
                        bmp.recycle()
                        doc.finishPage(pg)
                        PdfSaver.savePdfDocument(this, doc, "${base}_p${i + 1}.pdf", folder)
                            ?: throw Exception("Could not save file")
                        created++
                    } finally { doc.close() } } } }
        if (created == 0) throw Exception("No pages found")
        return "Created $created single-page PDF(s)\nLocation: $folder"
    }

    private fun askFind(uri: Uri) {
        val input = EditText(this).apply { hint = "Word or phrase"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Find in PDF").setView(input)
            .setPositiveButton("Search") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isEmpty()) toast("Enter something to search")
                else runTask { findInPdf(uri, t) } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun findInPdf(uri: Uri, term: String): String {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Cannot read file")
        PDDocument.load(bytes).use { doc ->
            val stripper = PDFTextStripper()
            val regex = Regex(Regex.escape(term), RegexOption.IGNORE_CASE)
            var total = 0; var pagesHit = 0; var shown = 0
            val sb = StringBuilder("Results for \"$term\":")
            for (p in 1..doc.numberOfPages) {
                stripper.startPage = p; stripper.endPage = p
                val text = stripper.getText(doc)
                val count = regex.findAll(text).count()
                if (count > 0) {
                    total += count; pagesHit++
                    if (shown < 8) {
                        shown++
                        val idx = regex.find(text)?.range?.first ?: 0
                        val from = maxOf(0, idx - 30)
                        val to = minOf(text.length, idx + term.length + 30)
                        val snippet = text.substring(from, to).replace(Regex("\\s+"), " ").trim()
                        sb.append("\n\n• Page $p ($count×)  "…$snippet…"") } } }
            if (total == 0) return "No matches for \"$term\".\n(Scanned PDFs have no searchable text — " +
                    "they contain images only.)"
            if (pagesHit > shown) sb.append("\n\n…plus more pages")
            sb.append("\n\n$total match(es) across $pagesHit page(s)")
            return sb.toString() }
    }

    private fun askNUp(uri: Uri) {
        AlertDialog.Builder(this).setTitle("Layout preset")
            .setItems(arrayOf("2 pages per sheet — landscape handout",
                "4 pages per sheet — portrait handout",
                "A5 booklet — fold & staple (A4 landscape)")) { _, which ->
                val preset = if (which == 2) "a4_booklet" else "a4_actual"
                askNUpMargin(which) { mm -> runPreviewTask(preset) { nUpPdf(uri, which, mm) } } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun askNUpMargin(mode: Int, onOk: (Float) -> Unit) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val input = EditText(this).apply {
            hint = "Margin (mm)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getString("nup_margin", if (mode == 2) "8" else "12")) }
        AlertDialog.Builder(this)
            .setTitle("Custom margins")
            .setMessage("Outer margin in millimeters." +
                    if (mode == 2) "\n\nBooklet: pages meet edge-to-edge at the center fold — " +
                            "the margin applies to the outer edges only." else "")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val mm = input.text.toString().replace(',', '.').toFloatOrNull()
                    ?.coerceIn(0f, 40f) ?: 12f
                prefs.edit().putString("nup_margin", mm.toString()).apply()
                onOk(mm) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun nUpPdf(uri: Uri, mode: Int, marginMm: Float): Pair<File, String> {
        val out = PdfDocument()
        try {
            val mm = marginMm * 72f / 25.4f
            val total = pdfPageCount(contentResolver, uri)
            if (total == 0) throw Exception("No pages found")
            val border = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFFDADCE0.toInt() }
            val suffix: String
            if (mode == 2) {
                suffix = "booklet"
                val sheetW = 842f; val sheetH = 595f
                val cellH = sheetH - mm * 2f
                val padded = PdfLogic.padTo4(total)
                val fold = Paint().apply {
                    style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFFDADCE0.toInt()
                    pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f) }
                val sides = PdfLogic.bookletSides(padded)
                withRenderer(contentResolver, uri) { r ->
                    sides.forEachIndexed { p, (left, right) ->
                        val pg = out.startPage(PdfDocument.PageInfo.Builder(
                            sheetW.toInt(), sheetH.toInt(), p + 1).create())
                        val c = pg.canvas
                        drawBookletCell(r, c, left, RectF(mm, mm, sheetW / 2f, mm + cellH))
                        drawBookletCell(r, c, right, RectF(sheetW / 2f, mm, sheetW - mm, mm + cellH))
                        c.drawLine(sheetW / 2f, mm, sheetW / 2f, sheetH - mm, fold)
                        out.finishPage(pg)
                        main.post { updateProgress("Imposing sheet ${p + 1} of ${sides.size}") } } }
            } else {
                suffix = if (mode == 0) "2up" else "4up"
                val sheetW = if (mode == 0) 842f else 595f
                val sheetH = if (mode == 0) 595f else 842f
                val cols = 2
                val rows = if (mode == 0) 1 else 2
                val cellW = (sheetW - mm * (cols + 1)) / cols
                val cellH = (sheetH - mm * (rows + 1)) / rows
                var sheet = 0; var idx = 0
                withRenderer(contentResolver, uri) { r ->
                    while (idx < total) {
                        sheet++
                        val pg = out.startPage(PdfDocument.PageInfo.Builder(
                            sheetW.toInt(), sheetH.toInt(), sheet).create())
                        val c = pg.canvas
                        for (cell in 0 until cols * rows) {
                            val i = idx + cell
                            if (i >= total) break
                            r.openPage(i).use { page ->
                                val bmp = renderPage(page, 2f, maxPixels = 4_000_000)
                                val col = cell % cols; val row = cell / cols
                                val cx = mm + col * (cellW + mm)
                                val cy = mm + row * (cellH + mm)
                                val s = minOf(cellW / bmp.width, cellH / bmp.height)
                                val dw = bmp.width * s; val dh = bmp.height * s
                                val dx = cx + (cellW - dw) / 2f
                                val dy = cy + (cellH - dh) / 2f
                                c.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), null)
                                c.drawRect(RectF(dx, dy, dx + dw, dy + dh), border)
                                bmp.recycle() } }
                        out.finishPage(pg)
                        main.post {
                            updateProgress("Sheet $sheet (${minOf(idx + cols * rows, total)}/$total pages)") }
                        idx += cols * rows } } }
            val f = tempPdf(); out.toTemp(f)
            return f to "${baseName(uri)}_$suffix.pdf"
        } finally { out.close() }
    }

    private fun drawBookletCell(r: PdfRenderer, c: Canvas, pageNo: Int, rect: RectF) {
        if (pageNo < 1 || pageNo > r.pageCount) return
        r.openPage(pageNo - 1).use { page ->
            val bmp = renderPage(page, 2f, maxPixels = 4_000_000)
            val s = minOf(rect.width() / bmp.width, rect.height() / bmp.height)
            val dw = bmp.width * s; val dh = bmp.height * s
            val dx = rect.left + (rect.width() - dw) / 2f
            val dy = rect.top + (rect.height() - dh) / 2f
            c.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), null)
            bmp.recycle() } }

    private fun askWebInput() {
        val input = EditText(this).apply {
            hint = "https://example.com  —  or paste raw HTML"
            setSingleLine(false); minLines = 3 }
        AlertDialog.Builder(this).setTitle("Webpage → PDF").setView(input)
            .setPositiveButton("Convert") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) convertWeb(text) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun convertWeb(input: String) {
        showProgress("Loading page…")
        val isUrl = input.startsWith("http://") || input.startsWith("https://")
        val wv = WebView(this)
        webView = wv
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.useWideViewPort = true
        wv.settings.loadWithOverviewMode = true
        var handled = false
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (handled) return
                handled = true
                updateProgress("Rendering PDF…")
                main.postDelayed({ printWebView(view, input) }, 1500) } }
        if (isUrl) wv.loadUrl(input)
        else wv.loadDataWithBaseURL(null, input, "text/html", "utf-8", null)
    }

    private fun printWebView(view: WebView, original: String) {
        val widthPx = 1080
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, widthPx, view.measuredHeight.coerceAtLeast(1))
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        val temp = File(cacheDir, "web_${System.currentTimeMillis()}.pdf")
        val fd = ParcelFileDescriptor.open(temp,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
                    or ParcelFileDescriptor.MODE_WRITE_ONLY)
        webAdapter = view.createPrintDocumentAdapter("webpage")
        webAdapter?.onLayout(null, attrs, null,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    webAdapter?.onWrite(arrayOf(PageRange.ALL_PAGES), fd, attrs,
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>?) {
                                closeQuietly(fd); finishWeb(temp, original) }
                            override fun onWriteFailed(error: CharSequence?) {
                                closeQuietly(fd); failWeb("Write failed: $error") } }) }
                override fun onLayoutFailed(error: CharSequence?) {
                    closeQuietly(fd); failWeb("Layout failed: $error") }
            }, Bundle())
    }

    private fun finishWeb(temp: File, original: String) {
        main.post {
            try {
                val host = if (original.startsWith("http"))
                    sanitize(Uri.parse(original).host ?: "Webpage") else "HTML"
                hideProgress()
                startActivity(Intent(this, PreviewActivity::class.java).apply {
                    putExtra(PreviewActivity.EXTRA_FILE, temp.absolutePath)
                    putExtra(PreviewActivity.EXTRA_NAME, "${host}_${stamp()}.pdf") })
            } catch (e: Exception) { failWeb(e.message ?: "unknown error") } }
    }

    private fun failWeb(msg: String) = main.post { hideProgress(); toast("Webpage conversion failed: $msg") }

    private fun decodeScaled(uri: Uri, maxDim: Int = 2000): Bitmap? = try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (s * 2) >= maxDim) s *= 2
        contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = s }) }
    } catch (e: Exception) { null }

    private fun baseName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) ?: "" else "" }
            ?.removeSuffix(".pdf")?.removeSuffix(".txt")
            ?.replace(Regex("[^A-Za-z0-9 _-]"), "")?.ifBlank { "file" } ?: "file"
    } catch (e: Exception) { "file" }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun sanitize(name: String) =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(50).ifBlank { "File" }

    private fun closeQuietly(fd: ParcelFileDescriptor) { try { fd.close() } catch (_: Exception) {} }

    override fun onDestroy() {
        super.onDestroy()
        hideProgress()
        main.removeCallbacksAndMessages(null)
        webView?.destroy(); webView = null
        webAdapter = null
    }
}
