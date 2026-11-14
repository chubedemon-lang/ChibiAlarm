package com.chibialarm.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutProgressionIntentTest {
    @Before fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun views(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(views(root.getChildAt(index)))
        }
    }

    @Test fun appliesOnlyTheValidatedConfirmedRepTarget() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, GuidedWorkoutActivity::class.java)
            .putExtra("program", "full")
            .putExtra("minutes", 30)
            .putExtra("progression_exercise", "Squat không tạ")
            .putExtra("progression_reps", 9)
        val controller = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent).setup()
        val activity = controller.get()
        val texts = views(activity.findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<TextView>()
            .map { it.text.toString() }

        assertTrue(texts.any {
            it.contains("Mục tiêu tiến triển đã xác nhận") && it.contains("9 lần mỗi hiệp")
        })
        controller.pause().stop().destroy()
    }

    @Test fun outOfRangeTargetIsIgnored() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, GuidedWorkoutActivity::class.java)
            .putExtra("program", "full")
            .putExtra("minutes", 30)
            .putExtra("progression_exercise", "Squat không tạ")
            .putExtra("progression_reps", 99)
        val controller = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent).setup()
        val texts = views(controller.get().findViewById<ViewGroup>(android.R.id.content))
            .filterIsInstance<TextView>()
            .map { it.text.toString() }

        assertFalse(texts.any { it.contains("Mục tiêu tiến triển đã xác nhận") })
        controller.pause().stop().destroy()
    }
}
