package com.example.pdfmaker

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader

class PdfMakerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.load(this)
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
            .onFailure { Log.w("PdfMakerApp", "PDFBox init failed", it) }
        registerActivityLifecycleCallbacks(AppLock.lifecycleCallbacks)
    }
}
