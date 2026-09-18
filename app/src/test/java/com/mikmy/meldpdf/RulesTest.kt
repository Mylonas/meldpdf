package com.mikmy.meldpdf

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTest {

    @Test
    fun parses_mixed_ranges_zero_based_sorted_deduped() {
        assertEquals(listOf(0, 1, 2, 4, 7, 8, 9), Rules.parseRanges("1-3, 5, 8-10", 10))
    }

    @Test
    fun clamps_and_skips_invalid_parts() {
        assertEquals(listOf(0, 4), Rules.parseRanges("1, foo, 5, 99", 5))
    }

    @Test
    fun reversed_range_is_normalised() {
        assertEquals(listOf(1, 2, 3), Rules.parseRanges("4-2", 5))
    }

    @Test
    fun invert_returns_the_remaining_pages() {
        assertEquals(listOf(1, 3, 4), Rules.invertRanges("1,3", 5))
    }

    @Test
    fun formats_sizes_like_the_web_app() {
        assertEquals("512 B", Rules.formatSize(512))
        assertEquals("1 KB", Rules.formatSize(1024))
        assertEquals("1.5 MB", Rules.formatSize(1_572_864))
    }
}
