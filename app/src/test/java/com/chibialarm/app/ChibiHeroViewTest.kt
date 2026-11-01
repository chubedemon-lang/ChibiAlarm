package com.chibialarm.app

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
}
