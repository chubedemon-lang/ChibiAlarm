package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChibiCharacterMotionTest {
    @Test fun idleBreathingIsBoundedPeriodicAndFinite() {
        assertEquals(ChibiCharacterMotion.breathScale(0L, false),
            ChibiCharacterMotion.breathScale(3_200L, false), 0.00001f)
        for (time in 0L..12_800L step 113L) {
            val scale = ChibiCharacterMotion.breathScale(time, false)
            assertTrue(scale.isFinite() && scale in 0.988f..1.012f)
        }
        assertEquals(ChibiCharacterMotion.breathScale(0L, false),
            ChibiCharacterMotion.breathScale(-1L, false), 0f)
    }

    @Test fun petReactionStartsAndEndsAtRest() {
        val deadline = 2_100L
        assertEquals(0f, ChibiCharacterMotion.petHop(1_000L, deadline, false), 0.0001f)
        assertEquals(-8f, ChibiCharacterMotion.petHop(1_550L, deadline, false), 0.0001f)
        assertEquals(0f, ChibiCharacterMotion.petHop(deadline, deadline, false), 0f)
        assertEquals(0f, ChibiCharacterMotion.petHop(deadline + 1L, deadline, false), 0f)
        assertEquals(0f, ChibiCharacterMotion.petHop(0L, 2_000L, false), 0f)
    }

    @Test fun reducedMotionFreezesBreathingAndReaction() {
        for (time in listOf(-1L, 0L, 400L, 10_000L, Long.MAX_VALUE)) {
            assertEquals(1f, ChibiCharacterMotion.breathScale(time, true), 0f)
            assertEquals(0f, ChibiCharacterMotion.petHop(time, Long.MAX_VALUE, true), 0f)
        }
    }

    @Test fun extremeTimesDoNotOverflowOrCausePermanentHop() {
        assertEquals(0f, ChibiCharacterMotion.petHop(1L, Long.MAX_VALUE, false), 0f)
        assertEquals(0f, ChibiCharacterMotion.petHop(-1L, 1L, false), 0f)
        assertEquals(0f, ChibiCharacterMotion.petHop(100L, -1L, false), 0f)
        assertTrue(ChibiCharacterMotion.breathScale(Long.MAX_VALUE, false).isFinite())
    }
}
