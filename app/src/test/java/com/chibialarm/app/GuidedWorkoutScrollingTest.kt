package com.chibialarm.app

import android.widget.Button
import android.widget.ScrollView
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutScrollingTest {
    @Test fun allWorkoutActionsRemainInsideScrollablePage() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val page = content.getChildAt(0)
        assertTrue("The workout screen must scroll on small devices", page is ScrollView)
        val buttons = mutableListOf<String>()
        fun visit(view: View) {
            if (view is Button) buttons.add(view.text.toString())
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(page)
        assertTrue(buttons.any { it.contains("Nói với Chibi") })
        assertTrue(buttons.any { it.contains("Nhập yêu cầu") })
        assertTrue(buttons.any { it.contains("Điều chỉnh buổi tập") })
        assertTrue(buttons.any { it.contains("Kết thúc buổi tập") })
        activity.finish()
    }

    @Test fun spokenPainPersistsSafetyLockForRestore() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        val method = GuidedWorkoutActivity::class.java.getDeclaredMethod(
            "handleCoachCommand", String::class.java
        )
        method.isAccessible = true
        method.invoke(activity, "tôi đau chân")
        val checkpoint = activity.getSharedPreferences("chibi_workout_checkpoint_guest", 0)
        assertTrue("Safety pause must survive process death", checkpoint.getBoolean("safety", false))
        activity.finish()
    }
}
