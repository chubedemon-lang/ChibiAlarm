package com.chibialarm.app

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoachRotationContextTest {
    @Test fun activeFitnessPreferencesSurviveRotationWithoutDiskWrite() {
        val controller = Robolectric.buildActivity(CoachActivity::class.java).setup()
        val activity = controller.get()
        val field = CoachActivity::class.java.getDeclaredField("fitnessContext").apply { isAccessible = true }
        field.set(activity, FitnessNaturalRequest.Result(
            goal = 2, energy = 0, minutes = 20, equipment = 0, impact = 1, reply = "temporary"
        ))
        val saved = Bundle()
        controller.saveInstanceState(saved)
        assertEquals(false, activity.getSharedPreferences("coach_history", 0).contains("fitness_goal"))
        val restored = Robolectric.buildActivity(CoachActivity::class.java).create(saved).start().resume().get()
        val result = field.get(restored) as FitnessNaturalRequest.Result
        assertEquals(2, result.goal)
        assertEquals(0, result.energy)
        assertEquals(20, result.minutes)
        assertEquals(0, result.equipment)
        assertEquals(1, result.impact)
        controller.pause().stop().destroy()
        restored.finish()
    }

    @Test fun noContextRemainsAbsentOnFreshLaunch() {
        val activity = Robolectric.buildActivity(CoachActivity::class.java).setup().get()
        val field = CoachActivity::class.java.getDeclaredField("fitnessContext").apply { isAccessible = true }
        assertNull(field.get(activity))
        activity.finish()
    }
}
