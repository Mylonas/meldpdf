package com.mikmy.meldpdf.pdf

/**
 * The pure, device-independent half of OCR: deciding what the input is and
 * turning per-page recognition results into the text and summary the user sees.
 * Kept free of Android classes so it is unit-testable on the JVM.
 */
object OcrText {

    /** Scanned PDFs can be long; stop here rather than run for minutes. */
    const val MAX_PAGES = 60

    /** True when [bytes] is a PDF (starts with the `%PDF` signature), else it is treated as an image. */
    fun isPdf(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

    /**
     * Joins recognised pages. A single page is returned as-is; several pages get
     * a "--- Page n ---" heading each, including empty ones so numbering stays
     * true to the document.
     */
    fun assemble(pages: List<String>): String {
        val cleaned = pages.map { it.trim() }
        if (cleaned.size <= 1) return cleaned.firstOrNull().orEmpty()
        if (cleaned.all { it.isEmpty() }) return ""
        return cleaned.mapIndexed { i, t -> "--- Page ${i + 1} ---\n$t".trimEnd() }.joinToString("\n\n")
    }

    /** "3 pages · 120 words · 700 characters", with a note when the document was cut short. */
    fun stats(pages: List<String>, totalPages: Int): String {
        val words = pages.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotBlank() } }
        val chars = pages.sumOf { it.trim().length }
        val pageLabel = if (pages.size == 1) "1 page" else "${pages.size} pages"
        val cut = if (totalPages > pages.size) " (first ${pages.size} of $totalPages)" else ""
        return "$pageLabel$cut · $words words · $chars characters"
    }
}
