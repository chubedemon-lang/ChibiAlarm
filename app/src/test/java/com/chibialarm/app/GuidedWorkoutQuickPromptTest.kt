package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(org.robolectric.RobolectricTestRunner::class)
class GuidedWorkoutQuickPromptTest {
    private fun field(name: String) =
        GuidedWorkoutActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun findButton(root: View, label: String): Button? {
        if (root is Button && root.text.toString() == label) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findButton(root.getChildAt(index), label)?.let { return it }
            }
        }
        return null
    }

    private fun findInput(root: View): EditText? {
        if (root is EditText) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findInput(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun openCoach(activity: GuidedWorkoutActivity): AlertDialog {
        GuidedWorkoutActivity::class.java.getDeclaredMethod("requestTypedCommand")
            .apply { isAccessible = true }
            .invoke(activity)
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    @Before fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun upcomingExercisePromptOnlyFillsTheInputUntilTheUserSubmits() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            @Suppress("UNCHECKED_CAST")
            val moves = field("moves").get(activity) as List<TrainingMove>
            assertTrue("The starter plan must contain an upcoming move", moves.size > 1)
            field("index").setInt(activity, 0)
            field("resting").setBoolean(activity, false)
            field("pausedForSafety").setBoolean(activity, false)
            field("completed").setBoolean(activity, false)

            val dialog = openCoach(activity)
            val root = dialog.window!!.decorView
            val prompt = findButton(root, "Bài tiếp theo")
            val input = findInput(root)

            assertNotNull("The next-exercise prompt should be visible before the final move", prompt)
            assertNotNull(input)
            prompt!!.performClick()
            assertEquals("Bài tiếp theo tập thế nào?", input!!.text.toString())
            assertTrue("Choosing a prompt must not submit the question", dialog.isShowing)
        } finally {
            activity.finish()
        }
    }

    @Test fun upcomingExercisePromptIsHiddenOnTheFinalMoveAndDuringSafetyStop() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            @Suppress("UNCHECKED_CAST")
            val moves = field("moves").get(activity) as List<TrainingMove>
            assertTrue(moves.isNotEmpty())
            field("index").setInt(activity, moves.lastIndex)
            field("resting").setBoolean(activity, false)
            field("pausedForSafety").setBoolean(activity, false)
            field("completed").setBoolean(activity, false)

            val finalMoveDialog = openCoach(activity)
            assertFalse(findButton(finalMoveDialog.window!!.decorView, "Bài tiếp theo") != null)
            finalMoveDialog.dismiss()

            field("index").setInt(activity, 0)
            field("pausedForSafety").setBoolean(activity, true)
            val safetyDialog = openCoach(activity)
            assertFalse(findButton(safetyDialog.window!!.decorView, "Bài tiếp theo") != null)
            safetyDialog.dismiss()
        } finally {
            activity.finish()
        }
    }

    @Test fun effortPromptsUseOnlyTheLatestConfirmedCheckInAndOnlyFillTheInput() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            field("index").setInt(activity, 0)
            field("resting").setBoolean(activity, true)
            field("pausedForSafety").setBoolean(activity, false)
            field("completed").setBoolean(activity, false)

            @Suppress("UNCHECKED_CAST")
            val completedSets = field("completedSetsByMove").get(activity) as MutableMap<Int, Int>
            completedSets[0] = 1

            @Suppress("UNCHECKED_CAST")
            val effortLevels = field("effortLevelsByMove").get(activity) as MutableMap<Int, List<WorkoutEffortFeedback.Level?>>
            effortLevels[0] = listOf(WorkoutEffortFeedback.Level.HARD)

            val dialog = openCoach(activity)
            val root = dialog.window!!.decorView
            val prompt = findButton(root, "Nghỉ theo cảm nhận")
            val historyPrompt = findButton(root, "Xem cảm nhận")
            val input = findInput(root)

            assertNotNull("The latest confirmed effort check-in should expose a rest explanation", prompt)
            assertNotNull("A completed set should expose its recorded effort history", historyPrompt)
            assertNotNull(input)
            prompt!!.performClick()
            assertEquals("Chibi đề xuất nghỉ theo mức độ gắng sức", input!!.text.toString())
            assertTrue("A quick prompt must not submit or start the rest timer", dialog.isShowing)
        } finally {
            activity.finish()
        }
    }

}
