package com.chibialarm.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
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

    @Before fun clearGuestCheckpointAndVoicePreference() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(WorkoutRestVoiceCuePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
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

    private fun simulateUserSeekBarChange(slider: SeekBar, progress: Int) {
        val listenerField = generateSequence(slider.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { SeekBar.OnSeekBarChangeListener::class.java.isAssignableFrom(it.type) }
            ?: throw AssertionError("SeekBar listener was not installed")
        listenerField.isAccessible = true
        val listener = listenerField.get(slider) as SeekBar.OnSeekBarChangeListener
        slider.progress = progress
        // setProgress(progress, animate) reports fromUser=false; simulate the real callback.
        listener.onProgressChanged(slider, progress, true)
    }

    @Test fun restVoiceCueIsVisibleOptOutAndPersistedInGuestPreferencesAndCheckpoint() {
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

            val guestPreference = RuntimeEnvironment.getApplication()
                .getSharedPreferences(WorkoutRestVoiceCuePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
            assertFalse(
                "The opt-out must persist beyond the current workout checkpoint",
                guestPreference.getBoolean(WorkoutRestVoiceCuePreference.KEY_ENABLED, true)
            )

            val checkpoint = RuntimeEnvironment.getApplication()
                .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            assertFalse("The opt-out must survive a guest session checkpoint",
                checkpoint.getBoolean("restVoiceCueEnabled", true))

            val bundle = android.os.Bundle()
            GuidedWorkoutActivity::class.java
                .getDeclaredMethod("onSaveInstanceState", android.os.Bundle::class.java)
                .apply { isAccessible = true }
                .invoke(activity, bundle)
            assertFalse("The opt-out must survive Activity recreation",
                bundle.getBoolean("workout_rest_voice_cue_enabled", true))

            toggle.performClick()
            assertTrue(toggle.isChecked)
            assertTrue(field("restVoiceCueEnabled").getBoolean(activity))
        } finally {
            activity.finish()
        }
    }

    @Test fun optOutSurvivesANewGuestWorkoutActivity() {
        val first = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            field("resting").setBoolean(first, true)
            field("paused").setBoolean(first, true)
            field("restRemainingMs").setLong(first, 5_000L)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(first)

            val firstToggle = findTagged(
                first.findViewById<ViewGroup>(android.R.id.content),
                "workout-rest-auto-voice-cue"
            ) as CheckBox
            assertTrue(firstToggle.isChecked)
            firstToggle.performClick()
            assertFalse(firstToggle.isChecked)
        } finally {
            first.finish()
        }

        // A fresh workout has no checkpoint, but the guest's explicit voice opt-out remains.
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val next = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            assertFalse(field("restVoiceCueEnabled").getBoolean(next))
            field("resting").setBoolean(next, true)
            field("paused").setBoolean(next, true)
            field("restRemainingMs").setLong(next, 5_000L)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(next)

            val nextToggle = findTagged(
                next.findViewById<ViewGroup>(android.R.id.content),
                "workout-rest-auto-voice-cue"
            ) as CheckBox
            assertFalse(nextToggle.isChecked)
            assertTrue(nextToggle.contentDescription.toString().contains("lưu trên thiết bị này"))
        } finally {
            next.finish()
        }
    }

    @Test fun leadTimeSliderOffersFiveSecondStepsAndPersistsAcrossFreshGuestWorkout() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            field("resting").setBoolean(activity, true)
            field("paused").setBoolean(activity, true)
            field("restRemainingMs").setLong(activity, 15_000L)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(activity)

            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val slider = findTagged(root, "workout-rest-voice-lead-slider") as SeekBar
            val label = findTagged(root, "workout-rest-voice-lead-label") as TextView
            assertEquals(3, slider.max)
            assertEquals(1, slider.progress)
            assertEquals("Nhắc trước: 10 giây", label.text.toString())

            simulateUserSeekBarChange(slider, 2)
            assertEquals(15, field("restVoiceCueLeadSeconds").getInt(activity))
            assertEquals("Nhắc trước: 15 giây", label.text.toString())

            val preferences = RuntimeEnvironment.getApplication().getSharedPreferences(
                WorkoutRestVoiceCuePreference.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            assertEquals(15, preferences.getInt(WorkoutRestVoiceCuePreference.KEY_LEAD_SECONDS, 10))
            val checkpoint = RuntimeEnvironment.getApplication().getSharedPreferences(
                "chibi_workout_checkpoint_guest",
                Context.MODE_PRIVATE
            )
            assertEquals(15, checkpoint.getInt("restVoiceCueLeadSeconds", 10))

            val bundle = android.os.Bundle()
            GuidedWorkoutActivity::class.java
                .getDeclaredMethod("onSaveInstanceState", android.os.Bundle::class.java)
                .apply { isAccessible = true }
                .invoke(activity, bundle)
            assertEquals(15, bundle.getInt("workout_rest_voice_cue_lead_seconds", 0))
        } finally {
            activity.finish()
        }

        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val next = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            assertEquals(15, field("restVoiceCueLeadSeconds").getInt(next))
            field("resting").setBoolean(next, true)
            field("paused").setBoolean(next, true)
            field("restRemainingMs").setLong(next, 15_000L)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(next)
            val slider = findTagged(
                next.findViewById<ViewGroup>(android.R.id.content),
                "workout-rest-voice-lead-slider"
            ) as SeekBar
            assertEquals(2, slider.progress)
        } finally {
            next.finish()
        }
    }

    @Test fun restVoiceCueToggleStaysHiddenOutsideRestAndDuringSafetyPause() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val toggle = findTagged(root, "workout-rest-auto-voice-cue") as CheckBox
            val slider = findTagged(root, "workout-rest-voice-lead-slider") as SeekBar
            val label = findTagged(root, "workout-rest-voice-lead-label") as TextView
            assertEquals(View.GONE, toggle.visibility)
            assertEquals(View.GONE, slider.visibility)
            assertEquals(View.GONE, label.visibility)

            field("resting").setBoolean(activity, true)
            field("pausedForSafety").setBoolean(activity, true)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(activity)
            assertEquals("Safety pause must hide the automatic voice control", View.GONE, toggle.visibility)
            assertEquals("Safety pause must hide the lead-time slider", View.GONE, slider.visibility)
            assertEquals("Safety pause must hide the lead-time label", View.GONE, label.visibility)
        } finally {
            activity.finish()
        }
    }


    @Test fun manualRestVoicePreviewIsVisibleOnlyDuringSafeRestAndDoesNotChangeTimersOrOneShot() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val preview = findTagged(root, "workout-rest-voice-cue-preview") as Button
            assertEquals(View.GONE, preview.visibility)

            field("resting").setBoolean(activity, true)
            field("paused").setBoolean(activity, true)
            field("restRemainingMs").setLong(activity, 15_000L)
            field("restTenSecondCueGiven").setBoolean(activity, false)
            field("ready").setBoolean(activity, false)
            field("restVoiceCueEnabled").setBoolean(activity, false)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(activity)

            assertEquals(View.VISIBLE, preview.visibility)
            assertTrue(preview.contentDescription.toString().contains("không dùng microphone"))
            assertTrue(preview.contentDescription.toString().contains("không thay đổi bộ đếm"))
            preview.performClick()

            assertEquals(15_000L, field("restRemainingMs").getLong(activity))
            assertFalse(field("restTenSecondCueGiven").getBoolean(activity))
            assertTrue(field("resting").getBoolean(activity))
            assertTrue(field("paused").getBoolean(activity))

            field("pausedForSafety").setBoolean(activity, true)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderRestPreview")
                .apply { isAccessible = true }
                .invoke(activity)
            assertEquals("Safety pause must hide manual voice preview", View.GONE, preview.visibility)
        } finally {
            activity.finish()
        }
    }
}
