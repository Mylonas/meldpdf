package com.mikmy.meldpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * PDFBox-Android needs its font/resource loader initialised once, against an
 * app Context, before any PDDocument work. Doing it here means every tool can
 * assume it's ready.
 */
class MeldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
