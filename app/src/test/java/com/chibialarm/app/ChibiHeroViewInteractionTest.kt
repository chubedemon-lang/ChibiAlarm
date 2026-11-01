package com.chibialarm.app

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Verify the actual Android touch-to-scene wiring, not only the pure hitbox rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChibiHeroViewInteractionTest {
    private fun tap(view: ChibiHeroView, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, x, y, 0)
        try {
            assertTrue(view.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    @Test fun visualDoorAndAccessibleButtonSwitchTheSameScene() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 320, 240)
        val visited = mutableListOf<Boolean>()
        view.onRoomChanged = { visited.add(it) }

        tap(view, 160f, 233f)
        assertEquals(ChibiSceneState.Scene.GARDEN, view.sceneState.scene)
        view.toggleScene()
        assertEquals(ChibiSceneState.Scene.BEDROOM, view.sceneState.scene)
        assertEquals(listOf(true, false), visited)
    }

    @Test fun gardenWaterCannotOpenBedroomAlarmAndPetStillWorks() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 320, 240)
        var waterCount = 0
        var petCount = 0
        val props = mutableListOf<ChibiSceneActions.Target>()
        view.onGardenAction = { waterCount++ }
        view.onPet = { petCount++ }
        view.onSceneAction = { props.add(it) }

        view.toggleScene()
        tap(view, 40f, 160f)
        assertEquals(1, waterCount)
        assertTrue(props.isEmpty())
        tap(view, 160f, 110f)
        assertEquals(1, petCount)

        view.toggleScene()
        tap(view, 40f, 160f)
        assertEquals(listOf(ChibiSceneActions.Target.BED), props)
    }
}
