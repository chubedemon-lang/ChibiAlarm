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

    @Test fun butterfliesLoopAndStayInsideTheirDecorativeBounds() {
        for (index in 0..1) {
            assertEquals(ChibiAmbientMotion.butterflyXOffset(0L, index, false),
                ChibiAmbientMotion.butterflyXOffset(6_400L, index, false), 0.0001f)
            assertEquals(ChibiAmbientMotion.butterflyYOffset(0L, index, false),
                ChibiAmbientMotion.butterflyYOffset(6_400L, index, false), 0.0001f)
            assertEquals(ChibiAmbientMotion.butterflyWingScale(0L, index, false),
                ChibiAmbientMotion.butterflyWingScale(420L, index, false), 0.0001f)
            for (time in 0L..12_800L step 179L) {
                assertTrue(ChibiAmbientMotion.butterflyXOffset(time, index, false) in -11f..11f)
                assertTrue(ChibiAmbientMotion.butterflyYOffset(time, index, false) in -4f..4f)
                assertTrue(ChibiAmbientMotion.butterflyWingScale(time, index, false) in 0.65f..1.0f)
            }
        }
    }

    @Test fun reducedMotionFreezesButterfliesEvenAtExtremeTimestamps() {
        for (time in listOf(-1L, 0L, 500L, Long.MAX_VALUE)) {
            for (index in 0..1) {
                assertEquals(0f, ChibiAmbientMotion.butterflyXOffset(time, index, true), 0f)
                assertEquals(0f, ChibiAmbientMotion.butterflyYOffset(time, index, true), 0f)
                assertEquals(0.7f, ChibiAmbientMotion.butterflyWingScale(time, index, true), 0f)
            }
        }
    }

    @Test fun negativeButterflyTimeIsClampedToZero() {
        for (index in 0..1) {
            assertEquals(ChibiAmbientMotion.butterflyXOffset(0L, index, false),
                ChibiAmbientMotion.butterflyXOffset(-100L, index, false), 0f)
            assertEquals(ChibiAmbientMotion.butterflyWingScale(0L, index, false),
                ChibiAmbientMotion.butterflyWingScale(-100L, index, false), 0f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun butterflyRejectsInvalidIndex() {
        ChibiAmbientMotion.butterflyXOffset(100L, 2, false)
    }
    @Test fun wateringDropsFallWithinGardenAndFadeWithoutOverflow() {
        for (index in 0..2) {
            val start = 96f + index * 8f
            assertEquals(start, ChibiAmbientMotion.wateringDropY(850L, index, false), 0f)
            assertEquals(start + 55f, ChibiAmbientMotion.wateringDropY(0L, index, false), 0f)
            for (remaining in listOf(-10L, 0L, 1L, 200L, 425L, 850L, 900L, Long.MAX_VALUE)) {
                val y = ChibiAmbientMotion.wateringDropY(remaining, index, false)
                assertTrue(y.isFinite() && y in start..(start + 55f))
                assertTrue(ChibiAmbientMotion.wateringDropAlpha(remaining, false) in 0..210)
            }
        }
        assertEquals(0, ChibiAmbientMotion.wateringDropAlpha(0L, false))
        assertEquals(210, ChibiAmbientMotion.wateringDropAlpha(850L, false))
    }

    @Test fun reducedMotionShowsStationaryWaterDrops() {
        for (index in 0..2) {
            assertEquals(96f + index * 8f,
                ChibiAmbientMotion.wateringDropY(100L, index, true), 0f)
        }
        assertEquals(180, ChibiAmbientMotion.wateringDropAlpha(400L, true))
        assertEquals(0, ChibiAmbientMotion.wateringDropAlpha(-1L, true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wateringDropsRejectInvalidIndex() {
        ChibiAmbientMotion.wateringDropY(200L, 3, false)
    }

}
