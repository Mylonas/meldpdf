package com.mikmy.meldpdf.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.mikmy.meldpdf.Rules
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/** What a tool produces. */
sealed interface ToolResult {
    /** A file to save or share. */
    data class FileOut(val bytes: ByteArray, val name: String, val mime: String) : ToolResult
    /** Plain text (extract text). */
    data class TextOut(val text: String, val stats: String) : ToolResult
    /** Metadata rows plus a cleaned copy of the document. */
    data class MetaOut(val rows: List<Pair<String, String>>, val cleaned: FileOut) : ToolResult
}

/** Rotation choices offered by the rotate tool. */
enum class Rotation(val degrees: Int, val label: String) {
    CW90(90, "90° right"), R180(180, "180°"), CCW90(270, "90° left")
}

/**
 * All PDF operations. PDFBox handles document structure (merge/split/rotate/
 * stamp/encrypt/text); Android's PdfRenderer handles rasterisation (PDF→image).
 * Each op is a suspend fun that runs off the main thread and returns a
 * [ToolResult]. Callers catch exceptions and show a friendly message.
 */
object PdfEngine {

    private const val PDF_MIME = "application/pdf"

    private fun PDDocument.toBytes(): ByteArray =
        ByteArrayOutputStream().use { this.save(it); it.toByteArray() }

    // ---- Organize ---------------------------------------------------------

    suspend fun merge(inputs: List<ByteArray>, outName: String = "merged.pdf"): ToolResult =
        withContext(Dispatchers.Default) {
            require(inputs.isNotEmpty()) { "No files selected." }
            val merger = PDFMergerUtility()
            val out = ByteArrayOutputStream()
            merger.destinationStream = out
            inputs.forEach { merger.addSource(ByteArrayInputStream(it)) }
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            ToolResult.FileOut(out.toByteArray(), outName, PDF_MIME)
        }

    /** Keep only the pages named by [spec] (e.g. "1-3,5"). */
    suspend fun split(input: ByteArray, spec: String): ToolResult =
        keepPages(input, "split.pdf") { max -> Rules.parseRanges(spec, max) }

    /** Drop the pages named by [spec], keep the rest. */
    suspend fun delete(input: ByteArray, spec: String): ToolResult =
        keepPages(input, "deleted.pdf") { max -> Rules.invertRanges(spec, max) }

    private suspend fun keepPages(
        input: ByteArray, outName: String, pick: (Int) -> List<Int>,
    ): ToolResult = withContext(Dispatchers.Default) {
        PDDocument.load(input).use { src ->
            val keep = pick(src.numberOfPages)
            require(keep.isNotEmpty()) { "That selection leaves no pages." }
            PDDocument().use { dst ->
                // importPage deep-clones the page + its resources into dst, so the
                // saved file doesn't dangle references into the source document.
                keep.forEach { i -> dst.importPage(src.getPage(i)) }
                ToolResult.FileOut(dst.toBytes(), outName, PDF_MIME)
            }
        }
    }

    suspend fun rotate(input: ByteArray, rotation: Rotation): ToolResult =
        withContext(Dispatchers.Default) {
            PDDocument.load(input).use { doc ->
                for (page in doc.pages) {
                    page.rotation = (page.rotation + rotation.degrees) % 360
                }
                ToolResult.FileOut(doc.toBytes(), "rotated.pdf", PDF_MIME)
            }
        }

    // ---- Convert ----------------------------------------------------------

    suspend fun imagesToPdf(images: List<ByteArray>, outName: String = "images.pdf"): ToolResult =
        withContext(Dispatchers.Default) {
            require(images.isNotEmpty()) { "No images selected." }
            PDDocument().use { doc ->
                for (bytes in images) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: continue
                    val page = PDPage(PDRectangle(bmp.width.toFloat(), bmp.height.toFloat()))
                    doc.addPage(page)
                    val image = if (bmp.hasAlpha()) {
                        LosslessFactory.createFromImage(doc, bmp)
                    } else {
                        JPEGFactory.createFromImage(doc, bmp, 0.85f)
                    }
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(image, 0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                    }
                    bmp.recycle()
                }
                require(doc.numberOfPages > 0) { "Could not read those images." }
                ToolResult.FileOut(doc.toBytes(), outName, PDF_MIME)
            }
        }

    /**
     * Rasterise every page and return a ZIP of images. Uses Android's built-in
     * PdfRenderer, which needs a seekable file, so [input] is staged in [cacheDir].
     */
    suspend fun pdfToImages(input: ByteArray, png: Boolean, cacheDir: File): ToolResult =
        withContext(Dispatchers.Default) {
            val staged = File.createTempFile("in", ".pdf", cacheDir).apply { writeBytes(input) }
            try {
                ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val zipBytes = ByteArrayOutputStream()
                        ZipOutputStream(zipBytes).use { zip ->
                            val fmt = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            val ext = if (png) "png" else "jpg"
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    // ~150 DPI relative to the 72-dpi PDF point grid.
                                    val scale = 150f / 72f
                                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    zip.putNextEntry(ZipEntry("page-%03d.%s".format(i + 1, ext)))
                                    bmp.compress(fmt, if (png) 100 else 85, zip)
                                    zip.closeEntry()
                                    bmp.recycle()
                                }
                            }
                        }
                        val name = if (png) "pages-png.zip" else "pages-jpg.zip"
                        ToolResult.FileOut(zipBytes.toByteArray(), name, "application/zip")
                    }
                }
            } finally {
                staged.delete()
            }
        }

    // ---- Edit & stamp -----------------------------------------------------

    suspend fun addPageNumbers(input: ByteArray): ToolResult =
        withContext(Dispatchers.Default) {
            PDDocument.load(input).use { doc ->
                val font = PDType1Font.HELVETICA
                doc.pages.forEachIndexed { i, page ->
                    val box = page.mediaBox
                    PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs ->
                        val label = "${i + 1}"
                        val size = 10f
                        val width = font.getStringWidth(label) / 1000f * size
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.newLineAtOffset(box.lowerLeftX + box.width / 2f - width / 2f, box.lowerLeftY + 24f)
                        cs.showText(label)
                        cs.endText()
                    }
                }
                ToolResult.FileOut(doc.toBytes(), "numbered.pdf", PDF_MIME)
            }
        }

    suspend fun watermark(input: ByteArray, text: String): ToolResult =
        withContext(Dispatchers.Default) {
            require(text.isNotBlank()) { "Enter watermark text." }
            PDDocument.load(input).use { doc ->
                val font = PDType1Font.HELVETICA_BOLD
                for (page in doc.pages) {
                    val box = page.mediaBox
                    PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs ->
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.18f }
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(120, 120, 120)
                        val size = min(box.width, box.height) / 8f
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.setTextMatrix(
                            Matrix.getRotateInstance(
                                Math.toRadians(45.0), box.width * 0.16f, box.height * 0.28f
                            )
                        )
                        cs.showText(text)
                        cs.endText()
                    }
                }
                ToolResult.FileOut(doc.toBytes(), "watermarked.pdf", PDF_MIME)
            }
        }

    // ---- Text -------------------------------------------------------------

    suspend fun extractText(input: ByteArray): ToolResult =
        withContext(Dispatchers.Default) {
            PDDocument.load(input).use { doc ->
                val text = PDFTextStripper().getText(doc)
                val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
                val stats = "${doc.numberOfPages} pages · $words words · ${text.length} characters"
                ToolResult.TextOut(text, stats)
            }
        }

    // ---- Security ---------------------------------------------------------

    suspend fun metadata(input: ByteArray): ToolResult =
        withContext(Dispatchers.Default) {
            PDDocument.load(input).use { doc ->
                val info: PDDocumentInformation = doc.documentInformation
                val rows = listOf(
                    "Title" to info.title,
                    "Author" to info.author,
                    "Subject" to info.subject,
                    "Keywords" to info.keywords,
                    "Creator" to info.creator,
                    "Producer" to info.producer,
                    "Created" to info.creationDate?.time?.toString(),
                    "Modified" to info.modificationDate?.time?.toString(),
                ).map { (k, v) -> k to (v?.takeIf { it.isNotBlank() } ?: "—") }

                doc.documentInformation = PDDocumentInformation()
                doc.documentCatalog.metadata = null
                val cleaned = ToolResult.FileOut(doc.toBytes(), "clean.pdf", PDF_MIME)
                ToolResult.MetaOut(rows, cleaned)
            }
        }

    suspend fun protect(input: ByteArray, password: String): ToolResult =
        withContext(Dispatchers.Default) {
            require(password.isNotBlank()) { "Enter a password." }
            PDDocument.load(input).use { doc ->
                val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(policy)
                ToolResult.FileOut(doc.toBytes(), "protected.pdf", PDF_MIME)
            }
        }

    suspend fun unlock(input: ByteArray, password: String): ToolResult =
        withContext(Dispatchers.Default) {
            PDDocument.load(input, password).use { doc ->
                doc.setAllSecurityToBeRemoved(true)
                ToolResult.FileOut(doc.toBytes(), "unlocked.pdf", PDF_MIME)
            }
        }
}
