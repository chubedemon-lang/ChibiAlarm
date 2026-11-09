package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
    private fun editText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            editText(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test fun customRestRejectsInvalidSecondsWithoutClosingDialog() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod("requestCustomRest")
        method.isAccessible = true
        method.invoke(activity)
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val field = editText(dialog.window!!.decorView)!!
        field.setText("601")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertEquals(before, remaining(activity))
        field.setText("abc")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertEquals(before, remaining(activity))
        dialog.dismiss()
        activity.finish()
    }

    @Test fun customRestRequiresSecondExplicitConfirmation() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val before = remaining(activity)
        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod("requestCustomRest")
        method.isAccessible = true
        method.invoke(activity)
        val inputDialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        editText(inputDialog.window!!.decorView)!!.setText("45")
        inputDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val confirmation = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(confirmation.isShowing)
        assertEquals(before, remaining(activity))
        confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(before, remaining(activity))
        activity.finish()
    }

}
