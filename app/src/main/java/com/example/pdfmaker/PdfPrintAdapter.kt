package com.example.pdfmaker
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo

class PdfPrintAdapter(private val resolver: ContentResolver, private val source: Uri, private val jobName: String) : PrintDocumentAdapter() {
    override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback?, ex: Bundle?) {
        if (cancel?.isCanceled == true) { cb?.onLayoutCanceled(); return }
        cb?.onLayoutFinished(PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
    }
    override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor?, cancel: CancellationSignal?, cb: WriteResultCallback?) {
        try { resolver.openInputStream(source)?.use { ins ->
            ParcelFileDescriptor.AutoCloseOutputStream(dest!!).use { ins.copyTo(it) } } ?: throw Exception("Cannot open the PDF")
            cb?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) { cb?.onWriteFailed(e.message) }
    }
}
