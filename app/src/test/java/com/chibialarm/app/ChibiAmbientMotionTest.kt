package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChibiAmbientMotionTest {
    @Test fun cloudsLoopSmoothlyAndNeverLeaveTheirSafeRange() {
        assertEquals(ChibiAmbientMotion.cloudOffset(0L, false),
            ChibiAmbientMotion.cloudOffset(24_000L, false), 0.0001f)
        for (time in 0L..48_000L step 317L) {
            val offset = ChibiAmbientMotion.cloudOffset(time, false)
            assertTrue(offset.isFinite() && offset in -4.5f..4.5f)
        }
    }

    @Test fun reducedMotionMakesAmbientSceneStill() {
        for (time in listOf(-1L, 0L, 200L, 12_000L, Long.MAX_VALUE)) {
            assertEquals(0f, ChibiAmbientMotion.cloudOffset(time, true), 0f)
            for (index in 0..2) {
                assertEquals(140, ChibiAmbientMotion.fireflyAlpha(time, index, true))
                assertEquals(0f, ChibiAmbientMotion.fireflyOffset(time, index, true), 0f)
            }
        }
    }

    @Test fun firefliesStayBoundedAndLoopWithoutOverflow() {
        for (index in 0..2) {
            assertEquals(ChibiAmbientMotion.fireflyAlpha(0L, index, false),
                ChibiAmbientMotion.fireflyAlpha(3_600L, index, false))
            for (time in listOf(-200L, 0L, 1L, 500L, 1800L, 3599L, Long.MAX_VALUE)) {
                assertTrue(ChibiAmbientMotion.fireflyAlpha(time, index, false) in 65..205)
                val offset = ChibiAmbientMotion.fireflyOffset(time, index, false)
                assertTrue(offset.isFinite() && offset in -3f..3f)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFireflyIndex() {
        ChibiAmbientMotion.fireflyAlpha(10L, 3, false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeFireflyIndex() {
        ChibiAmbientMotion.fireflyOffset(10L, -1, false)
    }
}
