package com.chibialarm.app

import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChibiHeroViewTest {
    @Test fun clickingHeroInvokesPetCallbackAndRemainsAccessible() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        var taps = 0
        view.onPet = { taps++ }
        assertTrue(view.isClickable)
        assertTrue(view.contentDescription.toString().isNotBlank())
        assertTrue(view.performClick())
        assertEquals(1, taps)
        assertTrue(view.performClick())
        assertEquals(2, taps)
    }

    @Test fun touchDispatchRespectsSceneAndInteractiveProps() {
        val view = ChibiHeroView(RuntimeEnvironment.getApplication())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 320, 240)
        var pets = 0
        var watering = 0
        var inGarden = false
        var lastProp: ChibiSceneActions.Target? = null
        view.onPet = { pets++ }
        view.onGardenAction = { watering++ }
        view.onRoomChanged = { inGarden = it }
        view.onSceneAction = { lastProp = it }

        fun tap(x: Float, y: Float) {
            for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0L, 1L, action, x, y, 0)
                try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
            }
        }
        tap(160f, 110f)
        assertEquals(1, pets)
        tap(40f, 160f)
        assertEquals(ChibiSceneActions.Target.BED, lastProp)
        tap(160f, 233f)
        assertTrue(inGarden)
        tap(40f, 160f)
        assertEquals(1, watering)
        assertEquals(ChibiSceneActions.Target.BED, lastProp)
        tap(160f, 233f)
        assertEquals(ChibiSceneState.Scene.BEDROOM, view.sceneState.scene)
    }
}
