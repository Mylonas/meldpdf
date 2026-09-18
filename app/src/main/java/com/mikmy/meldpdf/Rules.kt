package com.mikmy.meldpdf

/**
 * Pure app logic — no Android imports — so it runs on the JVM under
 * `testDebugUnitTest` without an emulator. Page-range math and formatting live
 * here; PDF I/O and platform glue stay out. This split is what lets CI validate
 * the core cheaply on every push. See references/architecture.md.
 */
object Rules {

    /**
     * Parse a human page-range string like "1-3, 5, 8-10" into zero-based page
     * indices, clamped to [0, max). Mirrors the web app's parseRanges so the
     * native tools accept the same input. Invalid or out-of-range parts are
     * skipped. Result is sorted and de-duplicated.
     */
    fun parseRanges(spec: String, max: Int): List<Int> {
        if (max <= 0) return emptyList()
        val out = sortedSetOf<Int>()
        for (raw in spec.split(',')) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val dash = part.indexOf('-')
            if (dash > 0) {
                val a = part.substring(0, dash).trim().toIntOrNull()
                val b = part.substring(dash + 1).trim().toIntOrNull()
                if (a == null || b == null) continue
                val lo = minOf(a, b)
                val hi = maxOf(a, b)
                for (i in lo..hi) if (i in 1..max) out.add(i - 1)
            } else {
                val n = part.toIntOrNull() ?: continue
                if (n in 1..max) out.add(n - 1)
            }
        }
        return out.toList()
    }

    /** The complement of [parseRanges] — the pages that would remain. */
    fun invertRanges(spec: String, max: Int): List<Int> {
        val drop = parseRanges(spec, max).toHashSet()
        return (0 until max).filter { it !in drop }
    }

    /** Compact human file size, matching the web app's fmt(). */
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1_048_576 -> "${(bytes / 1024.0).toInt()} KB"
        else -> String.format("%.1f MB", bytes / 1_048_576.0)
    }
}
