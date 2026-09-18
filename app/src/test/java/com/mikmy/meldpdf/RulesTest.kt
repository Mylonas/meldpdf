package com.mikmy.meldpdf

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTest {
    @Test
    fun perfect_doubles_the_score() {
        assertEquals(Rules.BASE_POINTS, Rules.scoreFor(perfect = false))
        assertEquals(Rules.BASE_POINTS * Rules.PERFECT_MULTIPLIER, Rules.scoreFor(perfect = true))
    }
}
