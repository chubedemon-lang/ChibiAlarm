package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutSetReportVoiceTest {
    @Before
    fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun omittedUnitReportUsesActiveMoveAndStillRequiresConfirmation() {
        val app = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(
            GuidedWorkoutActivity::class.java,
            Intent(app, GuidedWorkoutActivity::class.java)
                .putExtra("program", "full")
                .putExtra("minutes", 30)
        ).setup().get()

        fun field(name: String) =
            GuidedWorkoutActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

        try {
            @Suppress("UNCHECKED_CAST")
            val moves = field("moves").get(activity) as List<TrainingMove>
            val moveIndex = moves.indices.first { position ->
                WorkoutSetTracker.parse(moves[position].dose, moves[position].restSeconds) != null
            }
            val plan = WorkoutSetTracker.parse(
                moves[moveIndex].dose,
                moves[moveIndex].restSeconds
            )!!
            field("index").setInt(activity, moveIndex)
            // A small active-time checkpoint avoids relying on wall-clock sleeps in Robolectric.
            field("accumulated").setLong(activity, 1L)

            GuidedWorkoutActivity::class.java.getDeclaredMethod(
                "handleCoachCommand",
                String::class.java
            ).apply { isAccessible = true }
                .invoke(activity, "Tao vừa làm được sáu")

            val confirmation = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(confirmation.isShowing)
            val message = confirmation.window?.decorView
                ?.findViewById<TextView>(android.R.id.message)
                ?.text?.toString().orEmpty()
            assertTrue(message.contains("6 ${plan.unit}"))

            @Suppress("UNCHECKED_CAST")
            val completed = field("completedSetsByMove").get(activity) as MutableMap<Int, Int>
            assertEquals(0, completed[moveIndex] ?: 0)

            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            assertEquals(
                "completed=${completed[moveIndex]}, values=${(field("actualSetValuesByMove").get(activity) as Map<Int, List<Int>>)[moveIndex]}, index=${field("index").getInt(activity)}",
                1,
                completed[moveIndex] ?: 0
            )
            @Suppress("UNCHECKED_CAST")
            val values = field("actualSetValuesByMove").get(activity) as Map<Int, List<Int>>
            assertEquals(listOf(6), values[moveIndex])
        } finally {
            activity.finish()
        }
    }
    @Test
    fun spokenCorrectionChangesOnlyTheLatestSelfReportedValueAfterConfirmation() {
        val app = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(
            GuidedWorkoutActivity::class.java,
            Intent(app, GuidedWorkoutActivity::class.java)
                .putExtra("program", "full")
                .putExtra("minutes", 30)
        ).setup().get()

        fun field(name: String) =
            GuidedWorkoutActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

        try {
            @Suppress("UNCHECKED_CAST")
            val moves = field("moves").get(activity) as List<TrainingMove>
            val moveIndex = moves.indices.first { position ->
                WorkoutSetTracker.parse(moves[position].dose, moves[position].restSeconds)?.unit == "lần"
            }
            val plan = WorkoutSetTracker.parse(
                moves[moveIndex].dose,
                moves[moveIndex].restSeconds
            )!!
            field("index").setInt(activity, moveIndex)
            @Suppress("UNCHECKED_CAST")
            val completed = field("completedSetsByMove").get(activity) as MutableMap<Int, Int>
            completed[moveIndex] = 1
            @Suppress("UNCHECKED_CAST")
            val values = field("actualSetValuesByMove").get(activity) as MutableMap<Int, List<Int>>
            values[moveIndex] = listOf(6)
            field("accumulated").setLong(activity, 1L)

            GuidedWorkoutActivity::class.java.getDeclaredMethod(
                "handleCoachCommand",
                String::class.java
            ).apply { isAccessible = true }
                .invoke(activity, "Không phải, tám")

            val confirmation = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(confirmation.isShowing)
            val message = confirmation.window?.decorView
                ?.findViewById<TextView>(android.R.id.message)
                ?.text?.toString().orEmpty()
            assertTrue(message.contains("6 lần → 8 lần"))
            assertEquals(listOf(6), values[moveIndex])
            assertEquals(1, completed[moveIndex])

            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            assertEquals(
                "values=${values[moveIndex]}, completed=${completed[moveIndex]}, index=${field("index").getInt(activity)}",
                listOf(8),
                values[moveIndex]
            )
            assertEquals(1, completed[moveIndex])
            assertEquals("lần", plan.unit)
        } finally {
            activity.finish()
        }
    }

}
