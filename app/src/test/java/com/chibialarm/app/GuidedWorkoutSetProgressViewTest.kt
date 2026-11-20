package com.chibialarm.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutSetProgressViewTest {
    @Before fun clearCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun screenBarTracksRequiredSetsAndLabelsOptionalSetsSeparately() {
        val app = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(
            GuidedWorkoutActivity::class.java,
            Intent(app, GuidedWorkoutActivity::class.java)
                .putExtra("program", "full")
                .putExtra("minutes", 30)
        ).setup().get()

        try {
            @Suppress("UNCHECKED_CAST")
            val moves = GuidedWorkoutActivity::class.java.getDeclaredField("moves")
                .apply { isAccessible = true }.get(activity) as List<TrainingMove>
            val selected = moves.indices.first { position ->
                WorkoutSetTracker.parse(moves[position].dose, moves[position].restSeconds) != null
            }
            val plan = WorkoutSetTracker.parse(moves[selected].dose, moves[selected].restSeconds)!!
            GuidedWorkoutActivity::class.java.getDeclaredField("index")
                .apply { isAccessible = true }.setInt(activity, selected)

            @Suppress("UNCHECKED_CAST")
            val completed = GuidedWorkoutActivity::class.java.getDeclaredField("completedSetsByMove")
                .apply { isAccessible = true }.get(activity) as MutableMap<Int, Int>
            completed[selected] = 1

            GuidedWorkoutActivity::class.java.getDeclaredMethod("renderSetControls")
                .apply { isAccessible = true }.invoke(activity)

            val bar = GuidedWorkoutActivity::class.java.getDeclaredField("setProgressBar")
                .apply { isAccessible = true }.get(activity) as ProgressBar
            val label = GuidedWorkoutActivity::class.java.getDeclaredField("setProgress")
                .apply { isAccessible = true }.get(activity) as TextView

            assertEquals(View.VISIBLE, bar.visibility)
            assertEquals(plan.requiredSets, bar.max)
            assertEquals(1.coerceAtMost(plan.requiredSets), bar.progress)
            assertTrue(bar.contentDescription.toString().contains("hiệp bắt buộc"))
            assertTrue(label.text.toString().contains("Bắt buộc:"))

            if (plan.maximumSets > plan.requiredSets) {
                completed[selected] = plan.requiredSets + 1
                GuidedWorkoutActivity::class.java.getDeclaredMethod("renderSetControls")
                    .apply { isAccessible = true }.invoke(activity)
                assertEquals(plan.requiredSets, bar.progress)
                assertTrue(label.text.toString().contains("Tùy chọn: 1/"))
            }
        } finally {
            activity.finish()
        }
    }
}
