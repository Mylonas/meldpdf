package com.mikmy.meldpdf

import com.mikmy.meldpdf.pdf.OcrText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextTest {

    @Test
    fun recognises_the_pdf_signature() {
        assertTrue(OcrText.isPdf("%PDF-1.7\n".toByteArray()))
    }

    @Test
    fun images_and_short_inputs_are_not_pdfs() {
        assertFalse(OcrText.isPdf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertFalse(OcrText.isPdf("%PD".toByteArray()))
        assertFalse(OcrText.isPdf(ByteArray(0)))
    }

    @Test
    fun a_single_page_has_no_heading() {
        assertEquals("Hello world", OcrText.assemble(listOf("  Hello world\n")))
    }

    @Test
    fun several_pages_are_headed_and_numbered_even_when_one_is_empty() {
        assertEquals(
            "--- Page 1 ---\nOne\n\n--- Page 2 ---\n\n--- Page 3 ---\nThree",
            OcrText.assemble(listOf("One", "", "Three")),
        )
    }

    @Test
    fun all_empty_pages_assemble_to_nothing() {
        assertEquals("", OcrText.assemble(listOf("", "  ", "\n")))
        assertEquals("", OcrText.assemble(emptyList()))
    }

    @Test
    fun stats_count_pages_words_and_characters() {
        assertEquals("2 pages · 3 words · 13 characters", OcrText.stats(listOf("Hello world", "ok"), 2))
        assertEquals("1 page · 1 words · 2 characters", OcrText.stats(listOf("ok"), 1))
    }

    @Test
    fun page_text_is_the_lines_in_order() {
        val lines = listOf(OcrText.Line(" One ", 0f, 0f, 10f, 10f), OcrText.Line("Two", 0f, 20f, 10f, 30f))
        assertEquals("One" + "
" + "Two", OcrText.pageText(lines))
    }

    // A 100x40 px line whose top-left is at (200, 300) px, rendered at 2 px per point on a
    // 300x400 pt page: in reading orientation it is 50x20 pt, starting 100 pt from the left,
    // with its baseline 166 pt down (bottom 170 minus a fifth of the height).
    private val line = OcrText.Line("x", 200f, 300f, 300f, 340f)

    @Test
    fun an_upright_page_measures_up_from_the_bottom() {
        assertEquals(OcrText.Placement(100f, 234f, 0, 50f, 20f), OcrText.place(line, 2f, 300f, 400f, 0))
    }

    @Test
    fun a_page_rotated_a_quarter_turn_swaps_the_axes_and_turns_the_text() {
        assertEquals(OcrText.Placement(166f, 100f, 90, 50f, 20f), OcrText.place(line, 2f, 300f, 400f, 90))
    }

    @Test
    fun an_upside_down_page_measures_from_the_right() {
        assertEquals(OcrText.Placement(200f, 166f, 180, 50f, 20f), OcrText.place(line, 2f, 300f, 400f, 180))
    }

    @Test
    fun a_page_rotated_three_quarters_measures_from_the_far_corner() {
        assertEquals(OcrText.Placement(134f, 300f, 270, 50f, 20f), OcrText.place(line, 2f, 300f, 400f, 270))
        assertEquals(OcrText.place(line, 2f, 300f, 400f, 270), OcrText.place(line, 2f, 300f, 400f, -90))
    }

    @Test
    fun stats_say_when_a_long_document_was_cut_short() {
        assertEquals("2 pages (first 2 of 90) · 2 words · 2 characters", OcrText.stats(listOf("a", "b"), 90))
    }
}
