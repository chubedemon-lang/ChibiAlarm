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

    @Test fun slowFramesKeepWalkSpeedAndLongPausesAreBounded() {
        val slow = ChibiSceneState()
        val fast = ChibiSceneState()
        assertEquals(ChibiSceneState.Action.WALK, slow.tap(200f, 210f))
        assertEquals(ChibiSceneState.Action.WALK, fast.tap(200f, 210f))
        repeat(2) { slow.advance(0.1f) }
        repeat(4) { fast.advance(0.05f) }
        assertEquals(181f, slow.heroX, 0.001f)
        assertEquals(fast.heroX, slow.heroX, 0.001f)

        val resumed = ChibiSceneState()
        resumed.tap(200f, 210f)
        assertEquals(186.25f, resumed.advance(10f), 0.001f)
        assertTrue(resumed.isWalking)
        assertEquals(200f, resumed.advance(0.25f), 0.001f)
        assertFalse(resumed.isWalking)
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

    @Test fun snapshotRestoresSceneAndInFlightWalkWithoutRewards() {
        val original = ChibiSceneState()
        original.switchScene()
        assertEquals(ChibiSceneState.Action.WALK, original.tap(200f, 210f))
        original.advance(0.05f)
        val saved = original.snapshot()
        val restored = ChibiSceneState()
        assertTrue(restored.restore(saved))
        assertEquals(ChibiSceneState.Scene.GARDEN, restored.scene)
        assertEquals(saved.heroX, restored.heroX, 0.001f)
        assertEquals(saved.destinationX, restored.destinationX, 0.001f)
        assertTrue(restored.isWalking)
        repeat(20) { restored.advance(0.05f) }
        assertEquals(200f, restored.heroX, 0.001f)
        assertFalse(restored.isWalking)
    }

    @Test fun walkingFacesDestinationAndRestoresDirectionWithoutChangingRewards() {
        val state = ChibiSceneState()
        assertTrue(state.facingRight)
        assertEquals(ChibiSceneState.Action.WALK, state.tap(120f, 210f))
        assertFalse(state.facingRight)
        val restored = ChibiSceneState()
        assertTrue(restored.restore(state.snapshot()))
        assertFalse(restored.facingRight)
        repeat(20) { restored.advance(0.05f) }
        assertEquals(120f, restored.heroX, 0.001f)
        assertEquals(ChibiSceneState.Action.WALK, restored.tap(200f, 210f))
        assertTrue(restored.facingRight)
        assertEquals(ChibiSceneState.Action.PET, restored.tap(120f, 110f))
        assertTrue(restored.facingRight)
    }

    @Test fun corruptedSnapshotCannotEscapeWalkableFloorOrChangeScene() {
        val state = ChibiSceneState()
        assertFalse(state.restore(ChibiSceneState.Snapshot(
            ChibiSceneState.Scene.GARDEN, Float.NaN, 160f)))
        assertFalse(state.restore(ChibiSceneState.Snapshot(
            ChibiSceneState.Scene.GARDEN, 160f, Float.POSITIVE_INFINITY)))
        assertFalse(state.restore(ChibiSceneState.Snapshot(
            ChibiSceneState.Scene.GARDEN, 999f, 160f)))
        assertEquals(ChibiSceneState.Scene.BEDROOM, state.scene)
        assertEquals(160f, state.heroX, 0f)
        assertEquals(ChibiSceneState.Scene.GARDEN, state.switchScene())
        assertEquals(ChibiSceneState.Scene.BEDROOM, state.switchScene())
        assertEquals(160f, state.destinationX, 0f)
    }

    @Test fun gardenCareIsBoundedVisualOnlyAndSurvivesSceneSnapshot() {
        val state = ChibiSceneState()
        assertEquals(0, state.tendGarden()) // Bedroom cannot grow the garden.
        state.switchScene()
        assertEquals(1, state.tendGarden())
        assertEquals(2, state.tendGarden())
        assertEquals(3, state.tendGarden())
        repeat(20) { assertEquals(3, state.tendGarden()) }
        val snapshot = state.snapshot()
        val restored = ChibiSceneState()
        assertTrue(restored.restore(snapshot))
        assertEquals(3, restored.gardenGrowth)
        restored.switchScene()
        assertEquals(3, restored.tendGarden())
        restored.switchScene()
        assertEquals(3, restored.gardenGrowth)
        // Invalid snapshot must not mutate the previous scene or care stage.
        assertFalse(restored.restore(snapshot.copy(gardenGrowth = -1)))
        assertFalse(restored.restore(snapshot.copy(gardenGrowth = 4)))
        assertEquals(3, restored.gardenGrowth)
        assertEquals(ChibiSceneState.Scene.GARDEN, restored.scene)
    }

}
