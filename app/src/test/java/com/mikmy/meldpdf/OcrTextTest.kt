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
    fun stats_say_when_a_long_document_was_cut_short() {
        assertEquals("2 pages (first 2 of 90) · 2 words · 2 characters", OcrText.stats(listOf("a", "b"), 90))
    }
}
