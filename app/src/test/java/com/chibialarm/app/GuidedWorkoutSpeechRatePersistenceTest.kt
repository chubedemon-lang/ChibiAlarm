package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutSpeechRatePersistenceTest {
    @Before fun clearLocalState() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().clear().putFloat("rate", 1.25f).commit()
    }

    private fun intent() = Intent(RuntimeEnvironment.getApplication(), GuidedWorkoutActivity::class.java)
        .putExtra("program", "full")
        .putExtra("minutes", 30)

    private fun rate(activity: GuidedWorkoutActivity): Float {
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("workoutSpeechRate")
            .apply { isAccessible = true }
        return field.getFloat(activity)
    }

    private fun setRate(activity: GuidedWorkoutActivity, value: Float) {
        GuidedWorkoutActivity::class.java.getDeclaredField("workoutSpeechRate")
            .apply { isAccessible = true }.setFloat(activity, value)
        // Mirror a real user-selected rate: asynchronous TTS initialization must not
        // replace it with the global preference while this workout is active.
        GuidedWorkoutActivity::class.java.getDeclaredField("workoutSpeechRateRestored")
            .apply { isAccessible = true }.setBoolean(activity, true)
        (GuidedWorkoutActivity::class.java.getDeclaredField("spoken")
            .apply { isAccessible = true }
            .get(activity) as? TextToSpeech)?.setSpeechRate(value)
    }

    private fun saveCheckpoint(activity: GuidedWorkoutActivity) {
        GuidedWorkoutActivity::class.java.getDeclaredMethod("saveCheckpoint")
            .apply { isAccessible = true }.invoke(activity)
    }

    @Test fun rotationBundlePreservesTheWorkoutOnlyRateWithoutChangingGlobalVoicePreference() {
        val app = RuntimeEnvironment.getApplication()
        val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val controller = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent()).setup()
        val activity = controller.get()
        try {
            setRate(activity, 1.6f)
            val state = Bundle()
            GuidedWorkoutActivity::class.java
                .getDeclaredMethod("onSaveInstanceState", Bundle::class.java)
                .apply { isAccessible = true }
                .invoke(activity, state)

            assertEquals("full", state.getString("workout_program"))
            assertEquals(1.6f, state.getFloat("workout_speech_rate"), 0.001f)
            assertEquals(1.25f, settings.getFloat("rate", 1f), 0.001f)
        } finally {
            activity.finish()
        }
    }

    @Test fun checkpointRestoresTheRateOnlyAfterTheUserChoosesRestore() {
        val app = RuntimeEnvironment.getApplication()
        val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val checkpoint = app.getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
        val first = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent()).setup().get()
        setRate(first, 1.6f)
        saveCheckpoint(first)
        assertEquals(1.6f, checkpoint.getFloat("speechRate", 1f), 0.001f)
        first.finish()
        assertEquals("onPause must preserve the user's selected workout rate",
            1.6f, checkpoint.getFloat("speechRate", 1f), 0.001f)

        val restored = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent()).setup().get()
        try {
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            assertEquals(
                "Khôi phục",
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString()
            )
            assertEquals(1.25f, settings.getFloat("rate", 1f), 0.001f)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

            assertFalse(dialog.isShowing)
            assertEquals(1.6f, rate(restored), 0.001f)
            assertEquals(1.25f, settings.getFloat("rate", 1f), 0.001f)
        } finally {
            restored.finish()
        }
    }

    @Test fun startingOverResetsTheWorkoutRateToTheCurrentGlobalPreference() {
        val app = RuntimeEnvironment.getApplication()
        val first = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent()).setup().get()
        setRate(first, 1.6f)
        saveCheckpoint(first)
        first.finish()

        val restarted = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent()).setup().get()
        try {
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertEquals(
                "Bắt đầu lại",
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString()
            )
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertFalse(dialog.isShowing)
            assertEquals(1.25f, rate(restarted), 0.001f)
        } finally {
            restarted.finish()
        }
    }
}
