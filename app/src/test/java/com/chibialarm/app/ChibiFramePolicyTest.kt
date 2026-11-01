package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChibiFramePolicyTest {
    @Test fun invisibleScenesNeverScheduleFrames() {
        assertNull(ChibiFramePolicy.nextDelayMs(false, false, true, true))
        assertNull(ChibiFramePolicy.nextDelayMs(false, true, false, true))
    }

    @Test fun walkingAndEffectsAreResponsiveButIdleUsesLessCpu() {
        assertEquals(33L, ChibiFramePolicy.nextDelayMs(true, false, true, false))
        assertEquals(33L, ChibiFramePolicy.nextDelayMs(true, false, false, true))
        assertEquals(100L, ChibiFramePolicy.nextDelayMs(true, false, false, false))
    }

    @Test fun reducedMotionStopsContinuousRendering() {
        assertNull(ChibiFramePolicy.nextDelayMs(true, true, false, false))
        assertNull(ChibiFramePolicy.nextDelayMs(true, true, true, false))
        assertEquals(33L, ChibiFramePolicy.nextDelayMs(true, true, false, true))
    }

    @Test fun instantMovementRespectsBoundedTarget() {
        val state = ChibiSceneState()
        assertEquals(ChibiSceneState.Action.WALK, state.tap(236f, 210f))
        assertEquals(204f, state.finishWalk(), 0.001f)
        assertFalse(state.isWalking)
        assertEquals(ChibiSceneState.Action.WALK, state.tap(88f, 210f))
        assertEquals(116f, state.finishWalk(), 0.001f)
        assertFalse(state.isWalking)
    }
}
