package com.chibialarm.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutRestVoicePreferenceTest {

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

    private fun field(name: String) =
        GuidedWorkoutActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test fun restVoiceCueIsVisibleOptOutAndPersistedOnlyInGuestSessionCheckpoint() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            field("resting").setBoolean(activity, true)
            field("paused").setBoolean(activity, true)
            field("restRemainingMs").setLong(activity, 5_000L)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(activity)

            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val toggle = findTagged(root, "workout-rest-auto-voice-cue") as CheckBox
            assertEquals(View.VISIBLE, toggle.visibility)
            assertTrue("Existing default behavior remains enabled", toggle.isChecked)
            assertTrue(toggle.contentDescription.toString().contains("không sử dụng microphone"))

            toggle.performClick()
            assertFalse(toggle.isChecked)
            assertFalse(field("restVoiceCueEnabled").getBoolean(activity))
            assertFalse(
                WorkoutRestVoiceCue.shouldAnnounce(
                    remainingMs = 5_000L,
                    alreadyAnnounced = false,
                    pausedForSafety = false,
                    voiceCueEnabled = field("restVoiceCueEnabled").getBoolean(activity)
                )
            )

            val checkpoint = RuntimeEnvironment.getApplication()
                .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            assertFalse("The opt-out must survive a guest session checkpoint",
                checkpoint.getBoolean("restVoiceCueEnabled", true))

            val bundle = android.os.Bundle()
            activity.onSaveInstanceState(bundle)
            assertFalse("The opt-out must survive Activity recreation",
                bundle.getBoolean("workout_rest_voice_cue_enabled", true))

            toggle.performClick()
            assertTrue(toggle.isChecked)
            assertTrue(field("restVoiceCueEnabled").getBoolean(activity))
        } finally {
            activity.finish()
        }
    }

    @Test fun restVoiceCueToggleStaysHiddenOutsideRestAndDuringSafetyPause() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val toggle = findTagged(root, "workout-rest-auto-voice-cue") as CheckBox
            assertEquals(View.GONE, toggle.visibility)

            field("resting").setBoolean(activity, true)
            field("pausedForSafety").setBoolean(activity, true)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(activity)
            assertEquals("Safety pause must hide the automatic voice control", View.GONE, toggle.visibility)
        } finally {
            activity.finish()
        }
    }
}
