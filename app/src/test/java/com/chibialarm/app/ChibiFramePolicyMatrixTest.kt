package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers every visibility/accessibility/activity combination so future animation
 * changes cannot accidentally schedule a hidden scene or bypass reduced motion.
 */
class ChibiFramePolicyMatrixTest {
    @Test fun allSceneStatesRespectVisibilityAndReducedMotion() {
        for (visible in listOf(false, true))
            for (reduced in listOf(false, true))
                for (walking in listOf(false, true))
                    for (effect in listOf(false, true)) {
                        val delay = ChibiFramePolicy.nextDelayMs(visible, reduced, walking, effect)
                        val expected: Long? = when {
                            !visible -> null
                            reduced && effect -> ChibiFramePolicy.REDUCED_EFFECT_MS
                            reduced -> null
                            walking -> ChibiFramePolicy.WALK_MS
                            effect -> ChibiFramePolicy.EFFECT_MS
                            else -> ChibiFramePolicy.IDLE_MS
                        }
                        assertEquals("visible=$visible reduced=$reduced walking=$walking effect=$effect", expected, delay)
                        if (delay != null) assertTrue("Frame interval must be positive", delay > 0L)
                    }
    }

    @Test fun hiddenSceneNeverSchedulesEvenWithSimultaneousMovementAndEffects() {
        for (reduced in listOf(false, true)) {
            assertNull(ChibiFramePolicy.nextDelayMs(false, reduced, true, true))
        }
    }
}
