package com.mikmy.meldpdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikmy.meldpdf.pdf.OcrEngine
import com.mikmy.meldpdf.pdf.PdfEngine
import com.mikmy.meldpdf.pdf.ToolResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Runs the real recogniser on the emulator against pages we draw ourselves, so
 * the expected words are known. A "scanned PDF" is exactly what a scanner
 * produces: a PDF whose pages are pictures with no text layer.
 */
@RunWith(AndroidJUnit4::class)
class OcrEngineTest {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    /** A white page with [lines] printed in large black type, as PNG bytes. */
    private fun pagePng(vararg lines: String): ByteArray {
        val bmp = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        lines.forEachIndexed { i, line -> canvas.drawText(line, 100f, 240f + i * 140f, paint) }
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            out.toByteArray()
        }
    }

    private fun read(bytes: ByteArray): ToolResult.TextOut =
        runBlocking { OcrEngine.read(bytes, context.cacheDir) } as ToolResult.TextOut

    @Test
    fun reads_text_from_a_photo() {
        val out = read(pagePng("Invoice number 4821", "Total due 96 euro"))
        assertTrue("got: ${out.text}", out.text.contains("Invoice", ignoreCase = true))
        assertTrue("got: ${out.text}", out.text.contains("4821"))
        assertTrue("got: ${out.stats}", out.stats.startsWith("1 page"))
    }

    @Test
    fun reads_every_page_of_a_scanned_pdf() {
        val scanned = runBlocking {
            PdfEngine.imagesToPdf(listOf(pagePng("First page heading"), pagePng("Second page heading")))
        } as ToolResult.FileOut
        val out = read(scanned.bytes)
        assertTrue("got: ${out.text}", out.text.contains("--- Page 1 ---"))
        assertTrue("got: ${out.text}", out.text.contains("First", ignoreCase = true))
        assertTrue("got: ${out.text}", out.text.contains("--- Page 2 ---"))
        assertTrue("got: ${out.text}", out.text.contains("Second", ignoreCase = true))
        assertTrue("got: ${out.stats}", out.stats.startsWith("2 pages"))
    }

    @Test
    fun a_blank_page_reports_that_nothing_was_found() {
        assertThrows(IllegalStateException::class.java) { read(pagePng()) }
    }

    @Test
    fun a_file_that_is_neither_pdf_nor_image_is_rejected() {
        assertThrows(IllegalStateException::class.java) { read("just some text".toByteArray()) }
    }
}
