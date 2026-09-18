package com.mikmy.meldpdf

/**
 * Pure game/app logic — no Android imports — so it runs on the JVM under
 * `testDebugUnitTest` without an emulator. Keep scoring, state transitions,
 * and economy math here; keep rendering and platform glue out. This split is
 * what lets CI validate the rules cheaply on every push. See references/architecture.md.
 */
object Rules {
    const val BASE_POINTS = 1
    const val PERFECT_MULTIPLIER = 2

    /** Points awarded for a single successful action. */
    fun scoreFor(perfect: Boolean): Int =
        if (perfect) BASE_POINTS * PERFECT_MULTIPLIER else BASE_POINTS
}
