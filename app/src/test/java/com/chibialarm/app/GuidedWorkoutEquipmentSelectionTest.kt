package com.chibialarm.app

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutEquipmentSelectionTest {
    @Before fun clearGuestCheckpoint() {
        clearCheckpoint()
    }

    private fun clearCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun launch(selection: Int) =
        Robolectric.buildActivity(
            GuidedWorkoutActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), GuidedWorkoutActivity::class.java)
                .putExtra("program", "weighted")
                .putExtra("level", 1)
                .putExtra("minutes", 30)
                .putExtra("extension_equipment", selection)
        ).setup()

    private fun preferences(activity: GuidedWorkoutActivity): WorkoutExtensionPreferences {
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("extensionPreferences")
            .apply { isAccessible = true }
        return field.get(activity) as WorkoutExtensionPreferences
    }

    private fun moves(activity: GuidedWorkoutActivity): List<TrainingMove> {
        val field = GuidedWorkoutActivity::class.java.getDeclaredField("moves")
            .apply { isAccessible = true }
        return field.get(activity) as List<TrainingMove>
    }

    @Test fun exactDumbbellAndBandSelectionsSurviveTheGuidedWorkoutHandoff() {
        for (selection in listOf(4, 5)) {
            // Each assertion is a new-session handoff, not a checkpoint-restore scenario.
            clearCheckpoint()
            val controller = launch(selection)
            val activity = controller.get()

            assertFalse("selection $selection must not be rejected as a chair-only plan", activity.isFinishing)
            assertEquals(selection, preferences(activity).equipment)
            assertTrue(moves(activity).any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })

            controller.pause().stop().destroy()
        }
    }

    @Test fun pendingRestoreDoesNotOverwriteNewEquipmentAndStartOverKeepsIt() {
        val saved = launch(4)
        saved.pause().stop().destroy()

        val next = launch(5)
        val activity = next.get()
        assertEquals("new intent remains active while restore is pending", 5, preferences(activity).equipment)
        assertTrue(moves(activity).any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()

        assertEquals("Bắt đầu lại must keep the newly selected band", 5, preferences(activity).equipment)
        assertTrue(moves(activity).any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
        next.pause().stop().destroy()
    }

    @Test fun explicitRestoreUsesTheSavedEquipmentSnapshot() {
        val saved = launch(4)
        saved.pause().stop().destroy()

        val next = launch(5)
        val activity = next.get()
        assertEquals(5, preferences(activity).equipment)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals("Tiếp tục buổi tập đã lưu?", dialog.title)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        assertEquals("Khôi phục must restore the saved dumbbell selection", 4, preferences(activity).equipment)
        assertTrue(moves(activity).any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
        next.pause().stop().destroy()
    }
}
