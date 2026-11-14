package com.mikmy.meldpdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikmy.meldpdf.pdf.OcrEngine
import com.mikmy.meldpdf.pdf.OcrLanguage
import com.mikmy.meldpdf.pdf.PdfEngine
import com.mikmy.meldpdf.pdf.ToolResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Runs the real recognisers on the emulator against pages we draw ourselves, so
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

    private fun scannedPdf(vararg pages: ByteArray): ByteArray =
        (runBlocking { PdfEngine.imagesToPdf(pages.toList()) } as ToolResult.FileOut).bytes

    private fun read(bytes: ByteArray, language: OcrLanguage = OcrLanguage.LATIN): ToolResult.OcrOut =
        runBlocking { OcrEngine.read(bytes, context, language) } as ToolResult.OcrOut

    /** The text a PDF reader can select or search in [pdf]. */
    private fun selectableText(pdf: ByteArray): Pair<Int, String> =
        PDDocument.load(pdf).use { it.numberOfPages to PDFTextStripper().getText(it) }

    @Test
    fun reads_text_from_a_photo() {
        val out = read(pagePng("Invoice number 4821", "Total due 96 euro"))
        assertTrue("got: ${out.text.text}", out.text.text.contains("Invoice", ignoreCase = true))
        assertTrue("got: ${out.text.text}", out.text.text.contains("4821"))
        assertTrue("got: ${out.text.stats}", out.text.stats.startsWith("1 page"))
    }

    @Test
    fun reads_every_page_of_a_scanned_pdf() {
        val out = read(scannedPdf(pagePng("First page heading"), pagePng("Second page heading")))
        val text = out.text.text
        assertTrue("got: $text", text.contains("--- Page 1 ---"))
        assertTrue("got: $text", text.contains("First", ignoreCase = true))
        assertTrue("got: $text", text.contains("--- Page 2 ---"))
        assertTrue("got: $text", text.contains("Second", ignoreCase = true))
        assertTrue("got: ${out.text.stats}", out.text.stats.startsWith("2 pages"))
    }

    @Test
    fun a_scanned_pdf_comes_back_searchable_with_the_same_pages() {
        val scanned = scannedPdf(pagePng("First page heading"), pagePng("Second page heading"))
        assertEquals("a scan has no text to select", "", selectableText(scanned).second.trim())
        val (pages, text) = selectableText(read(scanned).pdf.bytes)
        assertEquals(2, pages)
        assertTrue("got: $text", text.contains("First", ignoreCase = true))
        assertTrue("got: $text", text.contains("Second", ignoreCase = true))
    }

    @Test
    fun a_photo_becomes_a_one_page_searchable_pdf() {
        val (pages, text) = selectableText(read(pagePng("Invoice number 4821")).pdf.bytes)
        assertEquals(1, pages)
        assertTrue("got: $text", text.contains("4821"))
    }

    @Test
    fun reads_greek() {
        val out = read(pagePng("Καλημέρα κόσμε", "Αυτό είναι ένα τεστ"), OcrLanguage.GREEK)
        val text = out.text.text
        assertTrue("got: $text", text.contains("Καλημ") || text.contains("κόσμε") || text.contains("τεστ"))
        val searchable = selectableText(out.pdf.bytes).second
        assertTrue("got: $searchable", searchable.any { it in 'α'..'ω' })
    }

    @Test
    fun reads_russian() {
        val out = read(pagePng("Привет мир", "Это простой тест"), OcrLanguage.RUSSIAN)
        val text = out.text.text
        assertTrue("got: $text", text.contains("Привет") || text.contains("мир") || text.contains("тест"))
        val searchable = selectableText(out.pdf.bytes).second
        assertTrue("got: $searchable", searchable.any { it in 'а'..'я' })
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
