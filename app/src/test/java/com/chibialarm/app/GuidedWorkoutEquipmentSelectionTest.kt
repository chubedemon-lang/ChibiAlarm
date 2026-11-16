package com.chibialarm.app

import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutEquipmentSelectionTest {
    @Before fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun exactDumbbellAndBandSelectionsSurviveTheGuidedWorkoutHandoff() {
        val app = RuntimeEnvironment.getApplication()
        for (selection in listOf(4, 5)) {
            val intent = Intent(app, GuidedWorkoutActivity::class.java)
                .putExtra("program", "weighted")
                .putExtra("level", 1)
                .putExtra("minutes", 30)
                .putExtra("extension_equipment", selection)

            val controller = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent).setup()
            val activity = controller.get()
            assertFalse("selection $selection must not be rejected as a chair-only plan", activity.isFinishing)

            val preferencesField = GuidedWorkoutActivity::class.java.getDeclaredField("extensionPreferences")
                .apply { isAccessible = true }
            val preferences = preferencesField.get(activity) as WorkoutExtensionPreferences
            assertEquals(selection, preferences.equipment)

            val movesField = GuidedWorkoutActivity::class.java.getDeclaredField("moves")
                .apply { isAccessible = true }
            val moves = movesField.get(activity) as List<TrainingMove>
            assertTrue(moves.any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
            controller.pause().stop().destroy()
        }
    }
}
