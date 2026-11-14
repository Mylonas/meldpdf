package com.mikmy.meldpdf.pdf

/**
 * The pure, device-independent half of OCR: deciding what the input is,
 * turning per-page recognition results into the text and summary the user
 * sees, and working out where each recognised line belongs on a PDF page.
 * Kept free of Android classes so it is unit-testable on the JVM.
 */
object OcrText {

    /** Scanned PDFs can be long; stop here rather than run for minutes. */
    const val MAX_PAGES = 60

    /** One recognised line of text and its box on the page bitmap, in pixels from the top-left. */
    data class Line(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * Where a line's invisible text goes on a PDF page, in the page's own
     * (unrotated) coordinate space, measured in points from its bottom-left.
     * [angle] is the anticlockwise text rotation in degrees.
     */
    data class Placement(val x: Float, val y: Float, val angle: Int, val width: Float, val height: Float)

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

    /** The plain text of one page: its lines, top to bottom. */
    fun pageText(lines: List<Line>): String = lines.joinToString("\n") { it.text.trim() }.trim()

    /** "3 pages · 120 words · 700 characters", with a note when the document was cut short. */
    fun stats(pages: List<String>, totalPages: Int): String {
        val words = pages.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotBlank() } }
        val chars = pages.sumOf { it.trim().length }
        val pageLabel = if (pages.size == 1) "1 page" else "${pages.size} pages"
        val cut = if (totalPages > pages.size) " (first ${pages.size} of $totalPages)" else ""
        return "$pageLabel$cut · $words words · $chars characters"
    }

    /**
     * Maps a line box on the rendered bitmap to a text position on the page.
     *
     * The bitmap shows the page as a reader sees it, i.e. after the page's
     * `/Rotate` of [rotation] degrees clockwise. PDF content is written in the
     * unrotated space, so for a rotated page both the position and the text
     * direction have to be turned back. [pageWidth] and [pageHeight] are the
     * unrotated page size in points; [pxPerPoint] is the render scale.
     *
     * The baseline sits a fifth of the box height above the box bottom, which
     * is where descenders leave it for ordinary type.
     */
    fun place(line: Line, pxPerPoint: Float, pageWidth: Float, pageHeight: Float, rotation: Int): Placement {
        val w = (line.right - line.left) / pxPerPoint
        val h = (line.bottom - line.top) / pxPerPoint
        val xd = line.left / pxPerPoint
        val yd = line.bottom / pxPerPoint - h * 0.2f
        return when (((rotation % 360) + 360) % 360) {
            90 -> Placement(yd, xd, 90, w, h)
            180 -> Placement(pageWidth - xd, yd, 180, w, h)
            270 -> Placement(pageWidth - yd, pageHeight - xd, 270, w, h)
            else -> Placement(xd, pageHeight - yd, 0, w, h)
        }
    }
}
