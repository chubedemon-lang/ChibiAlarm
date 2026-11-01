package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChibiSceneStateTest {
    @Test fun bedroomPropsAndHeroHaveDistinctHitAreas() {
        val state = ChibiSceneState()
        assertEquals(ChibiSceneState.Action.BED, state.tap(40f, 160f))
        assertEquals(ChibiSceneState.Action.DESK, state.tap(275f, 140f))
        assertEquals(ChibiSceneState.Action.TRAINING, state.tap(50f, 220f))
        assertEquals(ChibiSceneState.Action.PET, state.tap(160f, 110f))
        assertEquals(ChibiSceneState.Action.NONE, state.tap(5f, 10f))
    }

    @Test fun walkingIsFrameRateIndependentAndBounded() {
        val state = ChibiSceneState()
        assertEquals(ChibiSceneState.Action.WALK, state.tap(200f, 210f))
        assertTrue(state.isWalking)
        assertEquals(165.25f, state.advance(0.05f), 0.001f)
        repeat(100) { state.advance(0.05f) }
        assertEquals(200f, state.heroX, 0.001f)
        assertFalse(state.isWalking)
        assertEquals(200f, state.advance(Float.NaN), 0.001f)
        assertEquals(200f, state.advance(-1f), 0.001f)
        assertEquals(ChibiSceneState.Action.PET, state.tap(200f, 110f))
        assertEquals(ChibiSceneState.Action.NONE, state.tap(100f, 110f))
    }

    @Test fun doorwaySwitchesSceneAndGardenDoesNotTriggerBedroomActions() {
        val state = ChibiSceneState()
        assertEquals(ChibiSceneState.Action.DOOR, state.tap(160f, 233f))
        assertEquals(ChibiSceneState.Scene.GARDEN, state.scene)
        assertEquals(ChibiSceneState.Action.WATER, state.tap(40f, 160f))
        assertEquals(ChibiSceneState.Action.NONE, state.tap(275f, 110f))
        assertEquals(ChibiSceneState.Action.DOOR, state.tap(160f, 233f))
        assertEquals(ChibiSceneState.Scene.BEDROOM, state.scene)
        assertEquals(ChibiSceneState.Action.BED, state.tap(40f, 160f))
    }

    @Test fun invalidAndOutOfBoundsInputNeverChangesState() {
        val state = ChibiSceneState()
        for (x in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 321f)) {
            assertEquals(ChibiSceneState.Action.NONE, state.tap(x, 233f))
        }
        assertEquals(ChibiSceneState.Action.NONE, state.tap(160f, Float.NEGATIVE_INFINITY))
        assertEquals(ChibiSceneState.Scene.BEDROOM, state.scene)
        assertEquals(160f, state.heroX, 0f)
    }
}
