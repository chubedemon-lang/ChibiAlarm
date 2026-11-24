package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.Spinner
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class FitnessActivityRoutineLaunchTest {
    @Before fun resetGuestFitnessState() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("chibi_fitness_offline", Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun field(name: String) =
        FitnessActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun open(program: TrainingProgram, minutes: Int = 30): FitnessActivity {
        val controller = Robolectric.buildActivity(
            FitnessActivity::class.java,
            Intent().putExtra("routine_program_id", program.id)
                .putExtra("routine_minutes", minutes)
        ).setup()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun message(dialog: AlertDialog): String =
        dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()

    private fun title(dialog: AlertDialog): String =
        dialog.findViewById<TextView>(android.R.id.alertTitle)?.text?.toString().orEmpty()

    @Test fun routineHandoffShowsReviewAndRestoresScheduledDuration() {
        val program = TrainingCatalog.programs.first { candidate ->
            candidate.moves.isNotEmpty() && candidate.moves.all { it.equipmentOptions.isEmpty() }
        }
        val activity = open(program, 45)
        try {
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            assertTrue(message(dialog).contains(program.name))
            assertTrue(message(dialog).contains("hạn chế vận động"))
            val length = field("length").get(activity) as Spinner
            assertEquals(45, WorkoutDurationChoices.minutes[length.selectedItemPosition])
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        } finally {
            activity.finish()
        }
    }

    @Test fun reminderHandoffStillRunsTheExistingInjuryGate() {
        val program = TrainingCatalog.programs.first { candidate ->
            candidate.moves.isNotEmpty() && candidate.moves.all { it.equipmentOptions.isEmpty() }
        }
        val activity = open(program)
        try {
            val impact = field("impactRestrictionSpinner").get(activity) as Spinner
            impact.setSelection(2)
            val review = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            val safety = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(safety.isShowing)
            assertEquals("🐰 Tạm dừng buổi tập", title(safety))
        } finally {
            activity.finish()
        }
    }

    @Test fun reminderHandoffRechecksEquipmentBeforeStarting() {
        val activity = open(TrainingCatalog.byId("weighted"))
        try {
            (field("recommendationEquipmentSpinner").get(activity) as Spinner).setSelection(0)
            val review = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            val equipment = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(equipment.isShowing)
            assertTrue(message(equipment).contains("dụng cụ"))
        } finally {
            activity.finish()
        }
    }
}
