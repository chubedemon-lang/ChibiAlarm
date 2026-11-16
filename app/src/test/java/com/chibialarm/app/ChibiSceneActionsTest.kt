package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChibiSceneActionsTest {
    @Test fun scenePropsMapToCorrectActions() {
        assertEquals(ChibiSceneActions.Target.BED, ChibiSceneActions.hit(40f, 160f))
        assertEquals(ChibiSceneActions.Target.DESK, ChibiSceneActions.hit(275f, 140f))
        assertEquals(ChibiSceneActions.Target.TRAINING, ChibiSceneActions.hit(50f, 220f))
        assertEquals(ChibiSceneActions.Target.HERO, ChibiSceneActions.hit(160f, 120f))
    }

    @Test fun emptyAndInvalidTouchesAreIgnored() {
        assertEquals(ChibiSceneActions.Target.NONE, ChibiSceneActions.hit(5f, 5f))
        assertEquals(ChibiSceneActions.Target.NONE, ChibiSceneActions.hit(-1f, 160f))
        assertEquals(ChibiSceneActions.Target.NONE, ChibiSceneActions.hit(Float.NaN, 40f))
        assertEquals(ChibiSceneActions.Target.NONE, ChibiSceneActions.hit(Float.POSITIVE_INFINITY, 20f))
    }
}
