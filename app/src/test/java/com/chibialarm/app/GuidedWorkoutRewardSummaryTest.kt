package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutRewardSummaryTest {
    @Before
    fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun visibleText(view: View): String = buildString {
        if (view is TextView) append(view.text).append('\n')
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                append(visibleText(view.getChildAt(index)))
            }
        }
    }

    @Test
    fun rewardDialogShowsConfirmedSelfReportedEffortWithAccuracyDisclaimer() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            val summary = "Squat: hiệp 1: Khó, cần nghỉ thêm; Plank: hiệp 1: Vừa sức"
            val method = GuidedWorkoutActivity::class.java.getDeclaredMethod(
                "showWorkoutReward",
                WorkoutRewardDisplay::class.java,
                String::class.java
            ).apply { isAccessible = true }

            method.invoke(activity, WorkoutRewardDisplayFactory.create(15, 120), summary)

            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val text = visibleText(dialog.window!!.decorView)
            assertTrue(dialog.isShowing)
            assertTrue(text.contains("Cảm nhận các hiệp bạn đã tự ghi"))
            assertTrue(text.contains("Squat: hiệp 1: Khó, cần nghỉ thêm"))
            assertTrue(text.contains("Plank: hiệp 1: Vừa sức"))
            assertTrue(text.contains("không phải phép đo thể lực hay khả năng hồi phục"))
        } finally {
            activity.finish()
        }
    }

    @Test
    fun rewardDialogOmitsEffortSectionWhenNoCheckInsWereRecorded() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            val method = GuidedWorkoutActivity::class.java.getDeclaredMethod(
                "showWorkoutReward",
                WorkoutRewardDisplay::class.java,
                String::class.java
            ).apply { isAccessible = true }

            method.invoke(activity, WorkoutRewardDisplayFactory.create(0, 0), "")

            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val text = visibleText(dialog.window!!.decorView)
            assertTrue(dialog.isShowing)
            assertFalse(text.contains("Cảm nhận các hiệp bạn đã tự ghi"))
        } finally {
            activity.finish()
        }
    }
}
