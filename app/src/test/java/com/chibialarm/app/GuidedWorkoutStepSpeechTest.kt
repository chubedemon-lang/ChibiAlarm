package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
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
            shadowOf(Looper.getMainLooper()).idle()

            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val root = dialog.window!!.decorView
            val toggle = findTagged(root, "workout-step-auto-speak") as CheckBox
            val label = findTagged(root, "workout-step-label") as TextView
            val voiceButton = findTagged(root, "workout-step-voice-control") as Button

            assertFalse("Auto narration must be off until the user opts in", toggle.isChecked)
            assertTrue(label.text.toString().startsWith("Bước 1/"))
            assertTrue("The first exercise must expose a next step", dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)

            voiceButton.performClick()
            val consent = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(consent.getButton(AlertDialog.BUTTON_POSITIVE).text.toString().contains("nghe"))
            val disclosure = consent.findViewById<TextView>(android.R.id.message)
            assertTrue(disclosure.text.toString().contains("Android sẽ hỏi quyền micro"))
            assertTrue(disclosure.text.toString().contains("không lưu bản ghi"))
            consent.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Cancelling voice consent must leave the guide open", dialog.isShowing)

            toggle.performClick()
            assertTrue(toggle.isChecked)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(label.text.toString().startsWith("Bước 2/"))
            assertTrue("Opt-in remains active while navigating", toggle.isChecked)

            toggle.performClick()
            assertFalse(toggle.isChecked)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(label.text.toString().startsWith("Bước 3/"))
            assertFalse(toggle.isChecked)
            dialog.dismiss()
        } finally {
            activity.finish()
        }
    }
}
