package com.example.pdfmaker
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileInputStream

object PdfSaver {
    const val FOLDER = "Download/PDFMaker"
    private fun insert(c: Context, fileName: String, mime: String, folder: String = FOLDER): Uri? {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder) }
        return c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
    }
    fun save(c: Context, w: PdfWriter, fileName: String): Uri? {
        val uri = insert(c, fileName, "application/pdf") ?: return null
        c.contentResolver.openOutputStream(uri)?.use { w.writeTo(it) }; return uri }
    fun savePdfDocument(c: Context, pdf: PdfDocument, fileName: String, folder: String = FOLDER): Uri? {
        val uri = insert(c, fileName, "application/pdf", folder) ?: return null
        c.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }; return uri }
    fun saveBytes(c: Context, fileName: String, bytes: ByteArray): Uri? {
        val uri = insert(c, fileName, "application/pdf") ?: return null
        c.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }; return uri }
    fun saveFile(c: Context, source: File, fileName: String): Uri? {
        val uri = insert(c, fileName, "application/pdf") ?: return null
        val out = c.contentResolver.openOutputStream(uri) ?: return null
        FileInputStream(source).use { i -> out.use { i.copyTo(it) } }; return uri }
    fun saveTxt(c: Context, fileName: String, content: String): Uri? {
        val uri = insert(c, fileName, "text/plain") ?: return null
        c.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }; return uri }
    fun open(c: Context, uri: Uri) { try {
        c.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    } catch (e: Exception) { Toast.makeText(c, "No PDF viewer app found", Toast.LENGTH_SHORT).show() } }
    fun share(c: Context, uri: Uri, name: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        c.startActivity(Intent.createChooser(i, "Share PDF")) }
    fun shareMultiple(c: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData("PDFs", arrayOf("application/pdf"), ClipData.Item(uris[0])).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        c.startActivity(Intent.createChooser(i, "Share PDFs")) }
    fun moveFile(c: Context, uri: Uri, newFolder: String): Boolean = try {
        var name: String? = null
        c.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { q -> if (q.moveToFirst()) name = q.getString(0) }
        val n = name ?: return false
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, n)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, newFolder) }
        val newUri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return false
        val ins = c.contentResolver.openInputStream(uri); val outs = c.contentResolver.openOutputStream(newUri)
        if (ins == null || outs == null) { ins?.close(); outs?.close(); c.contentResolver.delete(newUri, null, null); return false }
        ins.use { i -> outs.use { o -> i.copyTo(o) } }
        c.contentResolver.delete(uri, null, null); true
    } catch (e: Exception) { false }
}
