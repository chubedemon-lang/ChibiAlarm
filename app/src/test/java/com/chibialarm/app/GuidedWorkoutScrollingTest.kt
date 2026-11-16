package com.chibialarm.app

import android.app.AlertDialog
import android.content.Intent
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.Shadows.shadowOf
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
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

    private fun findTagged(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTagged(view.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    @Test fun stepByStepDialogShowsTheCurrentMoveAnimationAndPausesBackgroundMotion() {
        val application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("chibi_workout_checkpoint_guest", 0).edit().clear().commit()
        val intent = Intent(application, GuidedWorkoutActivity::class.java)
            .putExtra("program", "starter")
            .putExtra("extension_equipment", 3)
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java, intent).setup().get()

        // The starter program begins with a warm-up; the explicit chair choice keeps its chair-squat.
        GuidedWorkoutActivity::class.java.getDeclaredField("index").apply {
            isAccessible = true
            setInt(activity, 1)
        }
        GuidedWorkoutActivity::class.java.getDeclaredMethod("displayStep").apply {
            isAccessible = true
            invoke(activity)
        }
        val backgroundPose = GuidedWorkoutActivity::class.java.getDeclaredField("poseView").let {
            it.isAccessible = true
            it.get(activity) as WorkoutPoseView
        }

        GuidedWorkoutActivity::class.java.getDeclaredMethod("showStepByStepGuide").apply {
            isAccessible = true
            invoke(activity)
        }
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val decor = dialog.window!!.decorView
        val preview = findTagged(decor, "workout-step-pose-preview") as? WorkoutPoseView
        val caption = findTagged(decor, "workout-step-phase-caption") as? TextView

        assertNotNull("The walkthrough must show an exercise-specific illustration", preview)
        assertNotNull("The animated phase must have a visible caption", caption)
        assertEquals(WorkoutPoseGuide.Pose.SQUAT, preview!!.pose)
        assertTrue(WorkoutPoseMotion.hasMotion(preview.pose))
        assertTrue(caption!!.text.toString().contains("Hình minh họa"))
        assertFalse("The background animation should pause while the modal preview is active",
            backgroundPose.motionEnabled)

        dialog.dismiss()
        // Dialog dispatches its dismissal message on the main looper.
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("The background illustration should be restored after dismissal",
            backgroundPose.motionEnabled)
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
