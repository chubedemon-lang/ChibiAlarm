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
    private fun dispatch(view: ChibiHeroView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 10L, action, x, y, 0)
        try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
    }

    private fun tap(view: ChibiHeroView, x: Float, y: Float) {
        dispatch(view, MotionEvent.ACTION_DOWN, x, y)
        dispatch(view, MotionEvent.ACTION_UP, x, y)
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

    @Test fun talkBackDescriptionTracksRoomAndRestoredScene() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        assertTrue(view.contentDescription.contains("Phòng ngủ"))
        view.toggleScene()
        assertTrue(view.contentDescription.contains("Khu vườn"))
        val restored = ChibiSceneState().apply { switchScene() }
        view.sceneState = restored
        assertTrue(view.contentDescription.contains("Khu vườn"))
        view.toggleScene()
        assertTrue(view.contentDescription.contains("Phòng ngủ"))
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
    @Test fun disabledSystemAnimationsMakeWalkingInstantAndKeepTouchResponsive() {
        val context = RuntimeEnvironment.getApplication()
        val resolver = context.contentResolver
        val setting = android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
        val previous = android.provider.Settings.Global.getFloat(resolver, setting, 1f)
        try {
            android.provider.Settings.Global.putFloat(resolver, setting, 0f)
            val view = ChibiHeroView(context)
            view.layout(0, 0, 320, 240)
            tap(view, 200f, 210f)
            assertEquals(200f, view.sceneState.heroX, 0.001f)
            assertTrue(!view.sceneState.isWalking)
            tap(view, 160f, 233f)
            assertEquals(ChibiSceneState.Scene.GARDEN, view.sceneState.scene)
        } finally {
            android.provider.Settings.Global.putFloat(resolver, setting, previous)
        }
    }

    @Test fun swipingAcrossHeroDoesNotPetOrChangeRoom() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 320, 240)
        var pets = 0
        var roomChanges = 0
        view.onPet = { pets++ }
        view.onRoomChanged = { roomChanges++ }
        dispatch(view, MotionEvent.ACTION_DOWN, 160f, 110f)
        dispatch(view, MotionEvent.ACTION_MOVE, 160f, 233f)
        dispatch(view, MotionEvent.ACTION_UP, 160f, 233f)
        assertEquals(0, pets)
        assertEquals(0, roomChanges)
        assertEquals(ChibiSceneState.Scene.BEDROOM, view.sceneState.scene)
    }

    @Test fun cancelledAndUpOnlyTouchesCannotTriggerRewards() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 320, 240)
        var pets = 0
        view.onPet = { pets++ }
        dispatch(view, MotionEvent.ACTION_UP, 160f, 110f)
        dispatch(view, MotionEvent.ACTION_DOWN, 160f, 110f)
        dispatch(view, MotionEvent.ACTION_CANCEL, 160f, 110f)
        dispatch(view, MotionEvent.ACTION_UP, 160f, 110f)
        assertEquals(0, pets)
        tap(view, 160f, 110f)
        assertEquals(1, pets)
    }


    @Test fun accessibleGardenActionMatchesTapAndIsDisabledInBedroom() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 320, 240)
        var watered = 0
        view.onGardenAction = { watered++ }
        assertTrue(!view.waterGarden())
        assertEquals(0, watered)
        view.toggleScene()
        assertTrue(view.waterGarden())
        tap(view, 40f, 160f)
        assertEquals(2, watered)
        view.toggleScene()
        assertTrue(!view.waterGarden())
        assertEquals(2, watered)
    }

}
