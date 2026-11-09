package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutRestConfirmationTest {
    @Before fun clearCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun remaining(activity: GuidedWorkoutActivity): Long {
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        field.isAccessible = true
        return field.getLong(activity)
    }

    private fun propose(activity: GuidedWorkoutActivity, seconds: Int): AlertDialog {
        val method = GuidedWorkoutActivity::class.java
            .getDeclaredMethod("confirmExtraRest", Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(activity, seconds)
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    @Test fun restRequestDoesNotMutateUntilConfirmed() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val dialog = propose(activity, 30)
        assertTrue(dialog.isShowing)
        assertEquals(before, remaining(activity))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(before, remaining(activity))
        activity.finish()
    }

    @Test fun acceptingRestPreviewAddsOnlyRequestedTime() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val dialog = propose(activity, 30)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals((before + 30_000L).coerceAtMost(600_000L), remaining(activity))
        activity.finish()
    }

    @Test fun outdatedRestPreviewCannotOverwriteNewTimer() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val dialog = propose(activity, 30)
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("remainingMs")
        field.isAccessible = true
        field.setLong(activity, remaining(activity) + 1_000L)
        val changed = remaining(activity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(changed, remaining(activity))
        activity.finish()
    }
}
