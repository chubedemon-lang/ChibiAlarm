package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutEffortCorrectionTest {
    @Before fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun field(name: String) =
        GuidedWorkoutActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun openRatedWorkout(): Pair<GuidedWorkoutActivity, Int> {
        val app = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(
            GuidedWorkoutActivity::class.java,
            Intent(app, GuidedWorkoutActivity::class.java)
                .putExtra("program", "full")
                .putExtra("minutes", 30)
        ).setup().get()
        @Suppress("UNCHECKED_CAST")
        val moves = field("moves").get(activity) as List<TrainingMove>
        val index = moves.indices.first {
            WorkoutSetTracker.parse(moves[it].dose, moves[it].restSeconds) != null
        }
        @Suppress("UNCHECKED_CAST")
        val completed = field("completedSetsByMove").get(activity) as MutableMap<Int, Int>
        completed[index] = 1
        @Suppress("UNCHECKED_CAST")
        val efforts = field("effortLevelsByMove").get(activity) as MutableMap<Int, List<WorkoutEffortFeedback.Level?>>
        efforts[index] = listOf(WorkoutEffortFeedback.Level.HARD)
        field("index").setInt(activity, index)
        return activity to index
    }

    @Test fun latestEffortCorrectionRequiresConfirmationAndPreservesWorkoutMeasurements() {
        val (activity, index) = openRatedWorkout()
        try {
            GuidedWorkoutActivity::class.java.getDeclaredMethod("showLatestEffortCorrectionDialog")
                .apply { isAccessible = true }
                .invoke(activity)

            val chooser = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(chooser.isShowing)
            val easyIndex = WorkoutEffortFeedback.Level.EASY.ordinal
            chooser.listView.performItemClick(
                chooser.listView.getChildAt(easyIndex),
                easyIndex,
                chooser.listView.getItemIdAtPosition(easyIndex)
            )

            val confirmation = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(confirmation.isShowing)
            val message = confirmation.findViewById<android.widget.TextView>(android.R.id.message)
                ?.text?.toString().orEmpty()
            assertTrue(message.contains("Số hiệp"))
            assertTrue(message.contains("EXP không thay đổi"))

            @Suppress("UNCHECKED_CAST")
            val efforts = field("effortLevelsByMove").get(activity) as Map<Int, List<WorkoutEffortFeedback.Level?>>
            assertEquals("Selection alone must not overwrite the stored check-in",
                WorkoutEffortFeedback.Level.HARD, efforts[index]?.last())

            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            @Suppress("UNCHECKED_CAST")
            val updated = field("effortLevelsByMove").get(activity) as Map<Int, List<WorkoutEffortFeedback.Level?>>
            @Suppress("UNCHECKED_CAST")
            val completed = field("completedSetsByMove").get(activity) as Map<Int, Int>
            assertEquals(WorkoutEffortFeedback.Level.EASY, updated[index]?.last())
            assertEquals(1, completed[index])
            assertFalse(field("resting").getBoolean(activity))

            val checkpoint = RuntimeEnvironment.getApplication()
                .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            assertTrue(checkpoint.getString("effortLog", "").orEmpty().contains("${index}:EASY"))
        } finally {
            activity.finish()
        }
    }

    @Test fun safetyStoppedSessionCannotCorrectAnEffortRating() {
        val (activity, index) = openRatedWorkout()
        try {
            field("pausedForSafety").setBoolean(activity, true)
            GuidedWorkoutActivity::class.java.getDeclaredMethod("showLatestEffortCorrectionDialog")
                .apply { isAccessible = true }
                .invoke(activity)

            @Suppress("UNCHECKED_CAST")
            val efforts = field("effortLevelsByMove").get(activity) as Map<Int, List<WorkoutEffortFeedback.Level?>>
            assertEquals(WorkoutEffortFeedback.Level.HARD, efforts[index]?.last())
            assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        } finally {
            activity.finish()
        }
    }
}
