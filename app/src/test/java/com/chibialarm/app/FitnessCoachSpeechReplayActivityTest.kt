package com.chibialarm.app

import android.content.Context
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FitnessCoachSpeechReplayActivityTest {
    @Test fun latestReplyCanBeReplayedWithoutPersistingIt() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val response = "Chibi đã ghi nhận mục tiêu giãn cơ và sẽ hỏi thêm về dụng cụ."

        FitnessActivity::class.java.getDeclaredMethod("sayFollowUp", String::class.java)
            .apply { isAccessible = true }
            .invoke(activity, response)

        val memory = FitnessActivity::class.java.getDeclaredField("coachSpeechMemory")
            .apply { isAccessible = true }
            .get(activity) as FitnessCoachSpeechMemory
        assertEquals(response, memory.replayText(voiceEnabled = true, engineReady = true))

        val replayButton = FitnessActivity::class.java.getDeclaredField("speechReplayButton")
            .apply { isAccessible = true }
            .get(activity) as Button
        assertEquals("🔁 Đọc lại lời Chibi", replayButton.text.toString())
        assertTrue(replayButton.isEnabled)

        val preferences = activity.getSharedPreferences("chibi_fitness_offline", Context.MODE_PRIVATE)
        assertFalse(preferences.all.values.any { it.toString().contains(response) })
        controller.pause().stop().destroy()
    }

    @Test fun clearingTranscriptAlsoClearsTheReplayableReply() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        FitnessActivity::class.java.getDeclaredMethod("sayFollowUp", String::class.java)
            .apply { isAccessible = true }
            .invoke(activity, "Câu trả lời tạm thời.")

        val transcript = FitnessActivity::class.java.getDeclaredField("coachTranscript")
            .apply { isAccessible = true }
            .get(activity) as FitnessCoachChatTranscript
        transcript.addUser("Muốn giãn cơ 15 phút.")
        FitnessActivity::class.java.getDeclaredMethod("renderCoachTranscript")
            .apply { isAccessible = true }
            .invoke(activity)

        val clearButton = FitnessActivity::class.java.getDeclaredField("coachTranscriptClearButton")
            .apply { isAccessible = true }
            .get(activity) as Button
        clearButton.performClick()

        val memory = FitnessActivity::class.java.getDeclaredField("coachSpeechMemory")
            .apply { isAccessible = true }
            .get(activity) as FitnessCoachSpeechMemory
        val replayButton = FitnessActivity::class.java.getDeclaredField("speechReplayButton")
            .apply { isAccessible = true }
            .get(activity) as Button
        assertFalse(memory.hasReply())
        assertFalse(replayButton.isEnabled)
        controller.pause().stop().destroy()
    }
}
