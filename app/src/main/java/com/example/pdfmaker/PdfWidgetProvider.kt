package com.example.pdfmaker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PdfWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppSettings.load(context)
        val views = buildViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PdfWidgetProvider::class.java))
            if (ids.isEmpty()) return
            AppSettings.load(context)
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private sealed class Tap {
            object Scan : Tap(); object Library : Tap()
            data class Doc(val typeId: String) : Tap()
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_pdf_maker)
            views.setInt(R.id.widgetAccentBar, "setBackgroundColor", AppSettings.accent)
            views.setOnClickPendingIntent(R.id.widgetHeader,
                tap(context, Intent(context, MainActivity::class.java)))
            val cells = mapOf(
                R.id.cellScan to Tap.Scan,
                R.id.cellInvoice to Tap.Doc("INVOICE"),
                R.id.cellReceipt to Tap.Doc("RECEIPT"),
                R.id.cellResume to Tap.Doc("RESUME"),
                R.id.cellCert to Tap.Doc("CERTIFICATE"),
                R.id.cellPacking to Tap.Doc("PACKING"),
                R.id.cellText to Tap.Doc("TEXT"),
                R.id.cellMore to Tap.Library)
            cells.forEach { (viewId, t) ->
                val intent = when (t) {
                    is Tap.Scan -> Intent(context, ScanActivity::class.java)
                    is Tap.Library -> Intent(context, BrowseActivity::class.java)
                    is Tap.Doc -> Intent(context, CreateActivity::class.java).putExtra("type", t.typeId)
                }
                views.setOnClickPendingIntent(viewId, tap(context, intent))
            }
            return views
        }

        private fun tap(context: Context, intent: Intent): PendingIntent =
            PendingIntent.getActivity(context, intent.hashCode(),
                intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
