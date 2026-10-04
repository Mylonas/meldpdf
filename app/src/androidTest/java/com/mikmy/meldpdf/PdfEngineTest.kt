package com.mikmy.meldpdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikmy.meldpdf.pdf.PdfEngine
import com.mikmy.meldpdf.pdf.Rotation
import com.mikmy.meldpdf.pdf.ToolResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * On-device tests that run every PdfEngine operation against a real generated
 * PDF and assert the output is a valid document with the expected shape. These
 * run on the emulator (connectedDebugAndroidTest) because PDFBox-Android and
 * Android's PdfRenderer need the platform runtime.
 */
@RunWith(AndroidJUnit4::class)
class PdfEngineTest {

    private val cacheDir by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    }

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    // ---- helpers ----------------------------------------------------------

    private fun samplePdf(pages: Int, text: String = "Hello MeldPDF"): ByteArray {
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("$text page ${i + 1}")
                    cs.endText()
                }
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun pngBytes(w: Int = 200, h: Int = 200): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.CYAN)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun pageCountOf(bytes: ByteArray): Int =
        PDDocument.load(bytes).use { it.numberOfPages }

    private fun firstRotationOf(bytes: ByteArray): Int =
        PDDocument.load(bytes).use { it.getPage(0).rotation }

    private fun file(r: ToolResult): ToolResult.FileOut = r as ToolResult.FileOut

    private fun unzipNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) { names.add(e.name); e = zip.nextEntry }
        }
        return names
    }

    private fun unzipEntry(bytes: ByteArray, name: String): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == name) return zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return null
    }

    // ---- organize ---------------------------------------------------------

    @Test fun merge_sums_pages() = runBlocking {
        val r = file(PdfEngine.merge(listOf(samplePdf(2), samplePdf(3))))
        assertEquals(5, pageCountOf(r.bytes))
    }

    @Test fun split_keeps_named_pages() = runBlocking {
        val r = file(PdfEngine.split(samplePdf(3), "1,3"))
        assertEquals(2, pageCountOf(r.bytes))
    }

    @Test fun delete_drops_named_pages() = runBlocking {
        val r = file(PdfEngine.delete(samplePdf(3), "2"))
        assertEquals(2, pageCountOf(r.bytes))
    }

    @Test fun rotate_sets_page_rotation() = runBlocking {
        val r = file(PdfEngine.rotate(samplePdf(1), Rotation.CW90))
        assertEquals(90, firstRotationOf(r.bytes))
    }

    @Test fun reorganize_applies_order_and_rotation() = runBlocking {
        val r = file(PdfEngine.reorganize(samplePdf(3), listOf(2, 0), mapOf(2 to 90)))
        assertEquals(2, pageCountOf(r.bytes))
        assertEquals(90, firstRotationOf(r.bytes)) // original page index 2, rotated 90
    }

    // ---- convert ----------------------------------------------------------

    @Test fun images_to_pdf_makes_one_page() = runBlocking {
        val r = file(PdfEngine.imagesToPdf(listOf(pngBytes())))
        assertEquals(1, pageCountOf(r.bytes))
    }

    @Test fun pdf_to_images_zips_one_per_page() = runBlocking {
        val r = file(PdfEngine.pdfToImages(samplePdf(2), png = false, cacheDir))
        val names = unzipNames(r.bytes)
        assertEquals(2, names.size)
        assertTrue(names.all { it.endsWith(".jpg") })
    }

    @Test fun compress_preserves_page_count() = runBlocking {
        val r = PdfEngine.compress(samplePdf(2), PdfEngine.CompressLevel.BALANCED, cacheDir)
        assertTrue(r is ToolResult.CompareOut)
        val f = (r as ToolResult.CompareOut).file
        assertEquals(2, pageCountOf(f.bytes))
    }

    @Test fun pdf_to_docx_contains_the_text() = runBlocking {
        val r = file(PdfEngine.pdfToDocx(samplePdf(1, "DOCXTOKEN")))
        assertTrue(unzipNames(r.bytes).contains("word/document.xml"))
        val doc = unzipEntry(r.bytes, "word/document.xml")
        assertNotNull(doc)
        assertTrue(doc!!.contains("DOCXTOKEN"))
    }

    // ---- edit & stamp -----------------------------------------------------

    @Test fun page_numbers_keep_valid_doc() = runBlocking {
        val r = file(PdfEngine.addPageNumbers(samplePdf(2)))
        assertEquals(2, pageCountOf(r.bytes))
    }

    @Test fun watermark_keeps_valid_doc() = runBlocking {
        val r = file(PdfEngine.watermark(samplePdf(1), "DRAFT"))
        assertEquals(1, pageCountOf(r.bytes))
    }

    @Test fun sign_keeps_valid_doc() = runBlocking {
        val sig = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val r = file(PdfEngine.placeSignature(samplePdf(1), 0, sig, 100f, 100f, 120f, 40f))
        assertEquals(1, pageCountOf(r.bytes))
    }

    // ---- text & security --------------------------------------------------

    @Test fun extract_text_finds_content() = runBlocking {
        val r = PdfEngine.extractText(samplePdf(1, "UNIQUETOKEN"))
        assertTrue(r is ToolResult.TextOut)
        assertTrue((r as ToolResult.TextOut).text.contains("UNIQUETOKEN"))
    }

    @Test fun metadata_cleaned_copy_is_stripped() = runBlocking {
        val r = PdfEngine.metadata(samplePdf(1))
        assertTrue(r is ToolResult.MetaOut)
        val cleaned = (r as ToolResult.MetaOut).cleaned.bytes
        PDDocument.load(cleaned).use { doc ->
            val t = doc.documentInformation.title
            assertTrue(t == null || t.isBlank())
        }
    }

    @Test fun protect_then_unlock_round_trips() = runBlocking {
        val protectedBytes = file(PdfEngine.protect(samplePdf(1), "secret")).bytes
        // Wrong/no password cannot open it.
        assertThrows(Exception::class.java) { PDDocument.load(protectedBytes).close() }
        // Correct password can.
        PDDocument.load(protectedBytes, "secret").use { assertEquals(1, it.numberOfPages) }
        // Unlock removes the protection.
        val unlocked = file(PdfEngine.unlock(protectedBytes, "secret")).bytes
        PDDocument.load(unlocked).use { assertEquals(1, it.numberOfPages) }
    }

    // ---- preview helpers --------------------------------------------------

    @Test fun render_thumbnails_one_per_page() = runBlocking {
        val thumbs = PdfEngine.renderThumbnails(samplePdf(3), 120, cacheDir)
        assertEquals(3, thumbs.size)
        assertEquals(120, thumbs[0].width)
    }

    @Test fun page_point_size_is_a4() = runBlocking {
        val (w, h) = PdfEngine.pagePointSize(samplePdf(1), 0)
        assertEquals(595f, w, 2f)
        assertEquals(842f, h, 2f)
    }
}
