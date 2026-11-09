package com.chibialarm.app

import android.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutShortenConfirmationTest {
    private fun remaining(activity: GuidedWorkoutActivity): Long {
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        field.isAccessible = true
        return field.getLong(activity)
    }

    private fun askToShorten(activity: GuidedWorkoutActivity): AlertDialog {
        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod("confirmShortenCurrentMove")
        method.isAccessible = true
        method.invoke(activity)
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    @Test fun cancellingPreviewDoesNotShortenExercise() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        assertTrue(before > 0L)
        val dialog = askToShorten(activity)
        assertTrue(dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(before, remaining(activity))
        activity.finish()
    }

    @Test fun acceptingPreviewShortensOnlyAfterConfirmation() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val dialog = askToShorten(activity)
        assertEquals(before, remaining(activity))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(com.chibialarm.app.fitness.RemainingWorkoutDuration.shortenActiveMs(before), remaining(activity))
        activity.finish()
    }
}
