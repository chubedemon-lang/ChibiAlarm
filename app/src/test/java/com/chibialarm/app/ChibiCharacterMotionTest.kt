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
    @Test fun petHeartsFadeAndRiseWithinTheReactionWindow() {
        assertEquals(0, ChibiCharacterMotion.petHeartAlpha(0L, false))
        assertEquals(255, ChibiCharacterMotion.petHeartAlpha(1_100L, false))
        assertEquals(0f, ChibiCharacterMotion.petHeartRise(1_100L, false), 0f)
        assertTrue(ChibiCharacterMotion.petHeartRise(550L, false) in 8.9f..9.1f)
        assertTrue(ChibiCharacterMotion.petHeartAlpha(550L, false) in 126..128)
        for (remaining in listOf(1L, 100L, 550L, 1_099L, 1_100L)) {
            assertTrue(ChibiCharacterMotion.petHeartAlpha(remaining, false) in 0..255)
            assertTrue(ChibiCharacterMotion.petHeartRise(remaining, false) in 0f..18f)
        }
    }

    @Test fun petHeartsAreStillInReducedMotionAndIgnoreStaleDeadlines() {
        for (remaining in listOf(1L, 500L, 1_100L)) {
            assertEquals(255, ChibiCharacterMotion.petHeartAlpha(remaining, true))
            assertEquals(0f, ChibiCharacterMotion.petHeartRise(remaining, true), 0f)
        }
        for (remaining in listOf(-1L, 0L, 1_101L, Long.MAX_VALUE)) {
            assertEquals(0, ChibiCharacterMotion.petHeartAlpha(remaining, false))
            assertEquals(0, ChibiCharacterMotion.petHeartAlpha(remaining, true))
            assertEquals(0f, ChibiCharacterMotion.petHeartRise(remaining, false), 0f)
        }
    }

    @Test fun walkingDustIsBoundedPeriodicAndPurelyVisual() {
        assertEquals(110, ChibiCharacterMotion.footstepDustAlpha(0L, true, false))
        assertEquals(110, ChibiCharacterMotion.footstepDustAlpha(480L, true, false))
        for (time in listOf(-5L, 0L, 1L, 240L, 479L, 480L, Long.MAX_VALUE)) {
            val alpha = ChibiCharacterMotion.footstepDustAlpha(time, true, false)
            val radius = ChibiCharacterMotion.footstepDustRadius(time, true, false)
            assertTrue(alpha in 0..110)
            assertTrue(radius.isFinite() && radius in 2f..6f)
        }
    }

    @Test fun walkingDustStopsImmediatelyWhenIdleOrMotionDisabled() {
        for (time in listOf(0L, 240L, Long.MAX_VALUE)) {
            assertEquals(0, ChibiCharacterMotion.footstepDustAlpha(time, false, false))
            assertEquals(0, ChibiCharacterMotion.footstepDustAlpha(time, true, true))
            assertEquals(0f, ChibiCharacterMotion.footstepDustRadius(time, false, false), 0f)
            assertEquals(0f, ChibiCharacterMotion.footstepDustRadius(time, true, true), 0f)
        }
    }

}
