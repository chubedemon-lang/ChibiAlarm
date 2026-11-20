package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutProgressStatusTest {
    @Before
    fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun invoke(activity: GuidedWorkoutActivity, methodName: String, vararg args: Any?) {
        val method = when (args.size) {
            0 -> GuidedWorkoutActivity::class.java.getDeclaredMethod(methodName)
            1 -> GuidedWorkoutActivity::class.java.getDeclaredMethod(
                methodName,
                args[0]!!::class.java
            )
            else -> throw AssertionError("Unexpected test helper arity")
        }
        method.isAccessible = true
        method.invoke(activity, *args)
    }

    private fun booleanField(activity: GuidedWorkoutActivity, name: String): Boolean =
        GuidedWorkoutActivity::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .getBoolean(activity)

    private fun statusDialog(): AlertDialog =
        ShadowAlertDialog.getLatestAlertDialog() as AlertDialog

    private fun dialogMessage(dialog: AlertDialog): String =
        dialog.window?.decorView?.findViewById<TextView>(android.R.id.message)
            ?.text?.toString().orEmpty()

    @Test
    fun statusCommandPausesTheClockAndShowsReadableAnswerWithoutTts() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            invoke(activity, "resume")
            assertFalse(booleanField(activity, "paused"))

            invoke(activity, "handleCoachCommand", "đọc tiến độ")

            assertTrue(booleanField(activity, "paused"))
            val dialog = statusDialog()
            assertTrue(dialog.isShowing)
            val message = dialogMessage(dialog)
            assertTrue(message.contains("Tiến độ buổi tập"))
            assertTrue(message.contains("Đang ở bước"))
            assertTrue(message.contains("Số hiệp là xác nhận của bạn"))

            val replay = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            assertEquals("Nghe lại toàn bộ câu trả lời của Chibi", replay.contentDescription)
            replay.performClick()
            assertTrue(dialog.isShowing)
        } finally {
            activity.finish()
        }
    }

    @Test
    fun safetyStatusShowsNoNextMoveOrCompletionEstimate() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            GuidedWorkoutActivity::class.java.getDeclaredField("pausedForSafety")
                .apply { isAccessible = true }
                .setBoolean(activity, true)

            invoke(activity, "handleCoachCommand", "đọc tiến độ")

            val dialog = statusDialog()
            val message = dialogMessage(dialog)
            assertTrue(message.contains("đang khóa an toàn"))
            assertFalse(message.contains("Động tác tiếp theo"))
            assertFalse(message.contains("Ước tính phần còn lại"))
            assertFalse(message.contains("hiệp tùy chọn"))
        } finally {
            activity.finish()
        }
    }
}
