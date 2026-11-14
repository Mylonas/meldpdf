package com.mikmy.meldpdf.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * On-device OCR for scanned PDFs and photos. Pages are rasterised with
 * Android's PdfRenderer and read by ML Kit's bundled Latin-script recogniser,
 * so nothing leaves the phone and no network is needed.
 */
object OcrEngine {

    /** Longest bitmap side handed to the recogniser: enough for small print, safe for memory. */
    private const val MAX_SIDE = 2400

    suspend fun read(input: ByteArray, cacheDir: File): ToolResult = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val pages: List<String>
            val total: Int
            if (OcrText.isPdf(input)) {
                val read = readPdf(input, cacheDir, recognizer)
                pages = read.first
                total = read.second
            } else {
                pages = listOf(readImage(input, recognizer))
                total = 1
            }
            val text = OcrText.assemble(pages)
            if (text.isBlank()) {
                throw IllegalStateException(
                    "No readable text was found. OCR works best on sharp, well-lit scans of printed text."
                )
            }
            ToolResult.TextOut(text, OcrText.stats(pages, total))
        } finally {
            recognizer.close()
        }
    }

    private suspend fun readPdf(
        input: ByteArray,
        cacheDir: File,
        recognizer: TextRecognizer,
    ): Pair<List<String>, Int> {
        val staged = File.createTempFile("ocr_in", ".pdf", cacheDir).apply { writeBytes(input) }
        try {
            ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    val out = ArrayList<String>()
                    for (i in 0 until minOf(total, OcrText.MAX_PAGES)) {
                        val bmp = renderer.openPage(i).use { page ->
                            // ~200 DPI on the 72-dpi point grid, capped so huge pages stay in memory.
                            val scale = minOf(200f / 72f, MAX_SIDE.toFloat() / max(page.width, page.height))
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        try {
                            out.add(recognise(recognizer, bmp, 0))
                        } finally {
                            bmp.recycle()
                        }
                    }
                    return out to total
                }
            }
        } finally {
            staged.delete()
        }
    }

    private suspend fun readImage(input: ByteArray, recognizer: TextRecognizer): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("That file is not a PDF or a readable image.")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(input, 0, input.size, opts)
            ?: throw IllegalStateException("That image could not be opened.")
        try {
            return recognise(recognizer, bmp, exifRotation(input))
        } finally {
            bmp.recycle()
        }
    }

    /** Camera photos are often stored sideways with a rotation flag; the recogniser needs to know. */
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

    private suspend fun recognise(recognizer: TextRecognizer, bmp: Bitmap, rotation: Int): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bmp, rotation))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
}
