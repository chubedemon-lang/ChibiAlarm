package com.chibialarm.app

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutStepSpeechTest {
    @Before fun clearGuestCheckpoint() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("chibi_workout_checkpoint_guest", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun findTagged(root: View, tag: String): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findTagged(root.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    @Test fun progressReplyUsesTheActiveSessionAndSelfConfirmedSetCounts() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            fun field(name: String) = GuidedWorkoutActivity::class.java.getDeclaredField(name).apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val moves = field("moves").get(activity) as List<TrainingMove>
            val selected = moves.indices.firstOrNull { position ->
                val plan = WorkoutSetTracker.parse(moves[position].dose, moves[position].restSeconds)
                plan != null && plan.requiredSets >= 2
            } ?: throw AssertionError("Starter plan needs a set-based move with at least two required sets")
            val plan = WorkoutSetTracker.parse(moves[selected].dose, moves[selected].restSeconds)!!
            field("index").setInt(activity, selected)
            @Suppress("UNCHECKED_CAST")
            val completed = field("completedSetsByMove").get(activity) as MutableMap<Int, Int>
            completed[selected] = 1

            val method = GuidedWorkoutActivity::class.java.getDeclaredMethod("currentWorkoutProgressReply")
                .apply { isAccessible = true }
            val reply = method.invoke(activity) as String

            assertTrue(reply.contains("Tiến độ buổi tập"))
            assertTrue(reply.contains("Đang ở bước " + (selected + 1) + ": " + moves[selected].name))
            assertTrue(reply.contains("Bạn đã tự xác nhận 1 trên " + plan.maximumSets + " hiệp"))
            assertTrue(reply.contains("Còn " + (plan.requiredSets - 1) + " hiệp bắt buộc"))
            assertTrue(reply.contains("không phải cảm biến tự đếm"))
        } finally {
            activity.finish()
        }
    }

    @Test fun automaticStepNarrationIsOptInAndCanBeTurnedOff() {
        val activity = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup().get()
        try {
            GuidedWorkoutActivity::class.java.getDeclaredMethod("showStepByStepGuide")
                .apply { isAccessible = true }
                .invoke(activity)
            shadowOf(Looper.getMainLooper()).idle()

            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            val root = dialog.window!!.decorView
            val toggle = findTagged(root, "workout-step-auto-speak") as CheckBox
            val label = findTagged(root, "workout-step-label") as TextView
            val voiceButton = findTagged(root, "workout-step-voice-control") as Button

            assertFalse("Auto narration must be off until the user opts in", toggle.isChecked)
            assertTrue(label.text.toString().startsWith("Bước 1/"))
            assertTrue("The first exercise must expose a next step", dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertTrue(voiceButton.contentDescription.toString().contains("quay về bước đầu"))
            assertTrue(voiceButton.text.toString().contains("bước đầu"))
            assertTrue(voiceButton.contentDescription.toString().contains("cách tập dễ hơn"))
            assertTrue(voiceButton.contentDescription.toString().contains("lưu ý an toàn"))
            assertTrue(voiceButton.contentDescription.toString().contains("hỏi cách tập"))
            assertTrue(voiceButton.contentDescription.toString().contains("hỏi dụng cụ"))
            assertTrue(voiceButton.contentDescription.toString().contains("nghỉ bao lâu"))
            assertTrue(voiceButton.contentDescription.toString().contains("đọc tiến độ buổi tập"))
            assertTrue(voiceButton.text.toString().contains("đọc tiến độ buổi tập"))
            assertTrue(voiceButton.contentDescription.toString().contains("đọc lại câu trả lời"))
            assertTrue(voiceButton.contentDescription.toString().contains("bật hoặc tắt tự đọc"))
            assertTrue(voiceButton.text.toString().contains("đọc lại câu trả lời"))
            assertTrue(voiceButton.text.toString().contains("bật tự đọc"))
            assertTrue(voiceButton.text.toString().contains("tắt tự đọc"))

            voiceButton.performClick()
            val consent = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertTrue(consent.getButton(AlertDialog.BUTTON_POSITIVE).text.toString().contains("nghe"))
            val disclosure = consent.findViewById<TextView>(android.R.id.message)
            assertTrue(disclosure.text.toString().contains("Android sẽ hỏi quyền micro"))
            assertTrue(disclosure.text.toString().contains("không lưu bản ghi"))
            consent.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Cancelling voice consent must leave the guide open", dialog.isShowing)

            toggle.performClick()
            assertTrue(toggle.isChecked)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(label.text.toString().startsWith("Bước 2/"))
            assertTrue("Opt-in remains active while navigating", toggle.isChecked)

            toggle.performClick()
            assertFalse(toggle.isChecked)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(label.text.toString().startsWith("Bước 3/"))
            assertFalse(toggle.isChecked)
            dialog.dismiss()
        } finally {
            activity.finish()
        }
    }
}
