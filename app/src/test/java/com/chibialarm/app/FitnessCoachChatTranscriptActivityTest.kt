package com.chibialarm.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FitnessCoachChatTranscriptActivityTest {
    @Test fun rawConversationTextIsDroppedOnRotationAndNeverWrittenToPreferences() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val field = FitnessActivity::class.java.getDeclaredField("coachTranscript").apply {
            isAccessible = true
        }
        val transcript = field.get(activity) as FitnessCoachChatTranscript
        transcript.addUser("Tôi đang đau đầu gối")
        transcript.addChibi("Hãy dừng tập và hỏi chuyên gia y tế.")

        val preferences = activity.getSharedPreferences("chibi_fitness_offline", Context.MODE_PRIVATE)
        assertFalse(preferences.all.values.any { it.toString().contains("đau đầu gối") })

        controller.recreate()
        val restored = field.get(controller.get()) as FitnessCoachChatTranscript
        assertEquals("", restored.render())
        controller.pause().stop().destroy()
    }

    @Test fun clearingVisibleTranscriptDoesNotEraseStructuredSafetySelection() {
        val controller = Robolectric.buildActivity(FitnessActivity::class.java).setup()
        val activity = controller.get()
        val conversationField = FitnessActivity::class.java.getDeclaredField("conversation").apply {
            isAccessible = true
        }
        conversationField.set(activity, FitnessNaturalRequest.parse("đang đau khớp"))
        val transcriptField = FitnessActivity::class.java.getDeclaredField("coachTranscript").apply {
            isAccessible = true
        }
        val transcript = transcriptField.get(activity) as FitnessCoachChatTranscript
        transcript.addUser("đang đau khớp")
        FitnessActivity::class.java.getDeclaredMethod("renderCoachTranscript")
            .apply { isAccessible = true }.invoke(activity)
        val clearButton = FitnessActivity::class.java.getDeclaredField("coachTranscriptClearButton")
            .apply { isAccessible = true }.get(activity) as android.widget.Button
        clearButton.performClick()

        assertEquals(2, conversationField.get(activity).let { it as FitnessNaturalRequest.Result }.impact)
        assertEquals("", transcript.render())
        controller.pause().stop().destroy()
    }
}
