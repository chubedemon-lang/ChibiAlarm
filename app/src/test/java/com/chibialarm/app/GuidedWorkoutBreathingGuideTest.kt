package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutBreathingGuideTest {
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

    @Test fun breathingGuideShowsTheActiveMovesCueAndOffersExplicitTts() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            val field = GuidedWorkoutActivity::class.java.getDeclaredField("moves").apply {
                isAccessible = true
            }
            val plank = TrainingCatalog.allMoves.single { it.name == "Plank khuỷu hoặc plank gối" }
            field.set(activity, listOf(plank))
            GuidedWorkoutActivity::class.java.getDeclaredField("index").apply {
                isAccessible = true
            }.setInt(activity, 0)

            val button = findTagged(activity.window.decorView, "workout-breathing-guide") as? Button
            assertNotNull("The guided workout must expose breathing guidance", button)
            assertTrue(button!!.contentDescription.toString().contains("không sử dụng microphone"))
            button.performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()

            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(dialog.title.toString().contains("Nhịp thở"))
            val message = dialog.findViewById<TextView>(android.R.id.message)
            assertNotNull(message)
            assertTrue(message!!.text.toString().contains("Giữ nhịp hít vào và thở ra đều"))
            assertTrue(message.text.toString().contains("không đánh giá tư thế"))
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString().contains("Nghe hướng dẫn"))
        } finally {
            activity.finish()
        }
    }

    @Test fun safetyPauseDoesNotOpenOrSpeakTheBreathingGuide() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            GuidedWorkoutActivity::class.java.getDeclaredField("pausedForSafety").apply {
                isAccessible = true
            }.setBoolean(activity, true)
            val button = findTagged(activity.window.decorView, "workout-breathing-guide") as? Button
            assertNotNull(button)
            button!!.performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(ShadowAlertDialog.getLatestAlertDialog() == null)
        } finally {
            activity.finish()
        }
    }
}
