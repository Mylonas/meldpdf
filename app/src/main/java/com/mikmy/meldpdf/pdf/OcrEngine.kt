package com.mikmy.meldpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Scripts the OCR tool can read. Latin uses ML Kit; the others use Tesseract with bundled data. */
enum class OcrLanguage(val label: String, internal val tessCode: String?) {
    LATIN("English / Latin", null),
    GREEK("Greek", "ell"),
    RUSSIAN("Russian", "rus"),
}

/**
 * On-device OCR for scanned PDFs and photos. Pages are rasterised with
 * Android's PdfRenderer and read by ML Kit (Latin script) or Tesseract
 * (Greek, Russian), so nothing leaves the phone and no network is needed.
 *
 * The result is both the plain text and a searchable PDF: the original pages
 * with the recognised words laid over them as invisible, selectable text.
 */
object OcrEngine {

    /** Longest bitmap side handed to the recogniser: enough for small print, safe for memory. */
    private const val MAX_SIDE = 2400

    /** System fonts that cover Latin, Greek and Cyrillic; only encoding matters as the text is invisible. */
    private val FONT_FILES = listOf(
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    )

    suspend fun read(
        input: ByteArray,
        context: Context,
        language: OcrLanguage = OcrLanguage.LATIN,
    ): ToolResult = withContext(Dispatchers.Default) {
        val reader: Reader = if (language.tessCode == null) MlKitReader() else TessReader(context, language.tessCode)
        try {
            val out = if (OcrText.isPdf(input)) readPdf(input, context.cacheDir, reader) else readImage(input, reader)
            val text = OcrText.assemble(out.pages)
            if (text.isBlank()) {
                throw IllegalStateException(
                    "No readable text was found. OCR works best on sharp, well-lit scans of printed text, " +
                        "with the right language selected."
                )
            }
            ToolResult.OcrOut(
                text = ToolResult.TextOut(text, OcrText.stats(out.pages, out.totalPages)),
                pdf = ToolResult.FileOut(out.pdf, "searchable.pdf", "application/pdf"),
            )
        } finally {
            reader.close()
        }
    }

    private class Pages(val pages: List<String>, val totalPages: Int, val pdf: ByteArray)

    // ---- Inputs -------------------------------------------------------------

    private suspend fun readPdf(input: ByteArray, cacheDir: File, reader: Reader): Pages {
        val staged = File.createTempFile("ocr_in", ".pdf", cacheDir).apply { writeBytes(input) }
        try {
            PDDocument.load(input).use { doc ->
                val font = loadFont(doc)
                val texts = ArrayList<String>()
                var total = 0
                ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        total = renderer.pageCount
                        for (i in 0 until minOf(total, OcrText.MAX_PAGES)) {
                            var scale = 1f
                            val bmp = renderer.openPage(i).use { page ->
                                // ~200 DPI on the 72-dpi point grid, capped so huge pages stay in memory.
                                scale = minOf(200f / 72f, MAX_SIDE.toFloat() / max(page.width, page.height))
                                val w = (page.width * scale).toInt().coerceAtLeast(1)
                                val h = (page.height * scale).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(android.graphics.Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                            val lines = try {
                                reader.lines(bmp)
                            } finally {
                                bmp.recycle()
                            }
                            texts.add(OcrText.pageText(lines))
                            overlay(doc, doc.getPage(i), lines, scale, font)
                        }
                    }
                }
                return Pages(texts, total, save(doc))
            }
        } finally {
            staged.delete()
        }
    }

    private suspend fun readImage(input: ByteArray, reader: Reader): Pages {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("That file is not a PDF or a readable image.")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(input, 0, input.size, opts)
            ?: throw IllegalStateException("That image could not be opened.")
        val bmp = upright(decoded, exifRotation(input))
        try {
            val lines = reader.lines(bmp)
            PDDocument().use { doc ->
                // One A4-wide page showing the photo, with the text laid over it.
                val pxPerPoint = bmp.width / PDRectangle.A4.width
                val page = PDPage(PDRectangle(PDRectangle.A4.width, bmp.height / pxPerPoint))
                doc.addPage(page)
                val image = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                }
                overlay(doc, page, lines, pxPerPoint, loadFont(doc))
                return Pages(listOf(OcrText.pageText(lines)), 1, save(doc))
            }
        } finally {
            bmp.recycle()
        }
    }

    /** Camera photos are often stored sideways with a rotation flag. */
    private fun exifRotation(input: ByteArray): Int = try {
        val orientation = ExifInterface(ByteArrayInputStream(input))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun upright(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }

    // ---- Searchable PDF -----------------------------------------------------

    private fun save(doc: PDDocument): ByteArray = ByteArrayOutputStream().use { doc.save(it); it.toByteArray() }

    private fun loadFont(doc: PDDocument): PDFont {
        for (path in FONT_FILES) {
            val file = File(path)
            if (!file.canRead()) continue
            try {
                return file.inputStream().use { PDType0Font.load(doc, it, true) }
            } catch (e: Exception) {
                // Try the next font.
            }
        }
        return PDType1Font.HELVETICA
    }

    /** Drops characters the font cannot encode, so one odd symbol never loses a whole line. */
    private fun encodable(font: PDFont, text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val s = String(Character.toChars(cp))
            try {
                font.encode(s)
                sb.append(s)
            } catch (e: Exception) {
                if (Character.isWhitespace(cp)) sb.append(' ')
            }
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }

    /** Writes [lines] over [page] as invisible text sized and stretched to match each line's box. */
    private fun overlay(doc: PDDocument, page: PDPage, lines: List<OcrText.Line>, pxPerPoint: Float, font: PDFont) {
        if (lines.isEmpty()) return
        val box = page.cropBox
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setRenderingMode(RenderingMode.NEITHER)
            for (line in lines) {
                val text = encodable(font, line.text)
                if (text.isEmpty()) continue
                val p = OcrText.place(line, pxPerPoint, box.width, box.height, page.rotation)
                val size = (p.height * 0.8f).coerceAtLeast(1f)
                val natural = try {
                    font.getStringWidth(text) / 1000f * size
                } catch (e: Exception) {
                    0f
                }
                if (natural <= 0f || p.width <= 0f) continue
                cs.beginText()
                cs.setFont(font, size)
                cs.setHorizontalScaling(100f * p.width / natural)
                cs.setTextMatrix(
                    Matrix.getRotateInstance(
                        Math.toRadians(p.angle.toDouble()),
                        box.lowerLeftX + p.x,
                        box.lowerLeftY + p.y,
                    )
                )
                cs.showText(text)
                cs.endText()
            }
        }
    }

    // ---- Recognisers --------------------------------------------------------

    private interface Reader {
        suspend fun lines(bmp: Bitmap): List<OcrText.Line>
        fun close()
    }

    /** ML Kit's bundled Latin-script model. */
    private class MlKitReader : Reader {
        private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        override suspend fun lines(bmp: Bitmap): List<OcrText.Line> = suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { result ->
                    val out = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                        val r = l.boundingBox ?: return@mapNotNull null
                        OcrText.Line(l.text, r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
                    }
                    if (cont.isActive) cont.resume(out)
                }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

        override fun close() = recognizer.close()
    }

    /** Tesseract with the language data shipped in the app's assets. */
    private class TessReader(context: Context, code: String) : Reader {
        private val api = TessBaseAPI()

        init {
            val dir = File(context.filesDir, "tesseract")
            val data = File(dir, "tessdata/$code.traineddata")
            if (!data.exists() || data.length() == 0L) {
                data.parentFile?.mkdirs()
                context.assets.open("tessdata/$code.traineddata").use { src ->
                    data.outputStream().use { src.copyTo(it) }
                }
            }
            if (!api.init(dir.absolutePath, code)) {
                api.recycle()
                throw IllegalStateException("The $code language data could not be loaded.")
            }
        }

        override suspend fun lines(bmp: Bitmap): List<OcrText.Line> {
            api.setImage(bmp)
            api.getUTF8Text() // runs recognition
            val out = ArrayList<OcrText.Line>()
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            val it = api.resultIterator
            if (it != null) {
                it.begin()
                do {
                    val text = it.getUTF8Text(level)?.trim().orEmpty()
                    val r = it.getBoundingRect(level)
                    if (text.isNotEmpty() && r != null) {
                        out.add(OcrText.Line(text, r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                    }
                } while (it.next(level))
                it.delete()
            }
            api.clear()
            return out
        }

        override fun close() = api.recycle()
    }
}
