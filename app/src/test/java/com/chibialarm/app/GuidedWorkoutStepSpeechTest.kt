package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutStepSpeechTest {
    @Before fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun findTagged(root: View, tag: String): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findTagged(root.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    @Test fun automaticStepNarrationIsOptInAndCanBeTurnedOff() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            GuidedWorkoutActivity::class.java.getDeclaredMethod("showStepByStepGuide")
                .apply { isAccessible = true }
                .invoke(activity)

            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val root = dialog.window!!.decorView
            val toggle = findTagged(root, "workout-step-auto-speak") as CheckBox
            val label = findTagged(root, "workout-step-label") as TextView

            assertFalse("Auto narration must be off until the user opts in", toggle.isChecked)
            assertTrue(label.text.toString().startsWith("Bước 1/"))

            toggle.performClick()
            assertTrue(toggle.isChecked)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(label.text.toString().startsWith("Bước 2/"))
            assertTrue("Opt-in remains active while navigating", toggle.isChecked)

            toggle.performClick()
            assertFalse(toggle.isChecked)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(label.text.toString().startsWith("Bước 3/"))
            assertFalse(toggle.isChecked)
            dialog.dismiss()
        } finally {
            activity.finish()
        }
    }
}
