package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessCoachChatTranscriptTest {
    @Test fun rendersUserAndCoachTurnsInOrder() {
        val transcript = FitnessCoachChatTranscript()
        transcript.addUser(" Muốn giãn cơ 15 phút ")
        transcript.addChibi("Đã ghi nhận. Mày có dụng cụ nào?")
        assertEquals(
            "Bạn: Muốn giãn cơ 15 phút\n\n🐰 Chibi: Đã ghi nhận. Mày có dụng cụ nào?",
            transcript.render()
        )
    }

    @Test fun ignoresBlankMessagesAndBoundsEachMessage() {
        val transcript = FitnessCoachChatTranscript(maxCharactersPerMessage = 32)
        transcript.addUser("   ")
        transcript.addChibi("x".repeat(80))
        assertEquals(1, transcript.messages().size)
        assertEquals(32, transcript.messages().single().text.length)
    }

    @Test fun keepsOnlyMostRecentMessages() {
        val transcript = FitnessCoachChatTranscript(maxMessages = 4)
        repeat(3) { index ->
            transcript.addUser("Câu hỏi $index")
            transcript.addChibi("Trả lời $index")
        }
        assertEquals(4, transcript.messages().size)
        assertFalse(transcript.render().contains("Câu hỏi 0"))
        assertTrue(transcript.render().contains("Câu hỏi 1"))
        assertTrue(transcript.render().contains("Trả lời 2"))
    }

    @Test fun returnedMessageListCannotMutateTranscript() {
        val transcript = FitnessCoachChatTranscript()
        transcript.addUser("Xin chào")
        val copy = transcript.messages()
        assertEquals(1, copy.size)
        assertEquals("Xin chào", transcript.messages().single().text)
    }

    @Test fun clearRemovesTransientText() {
        val transcript = FitnessCoachChatTranscript()
        transcript.addUser("Tôi đang đau đầu gối")
        transcript.addChibi("Tạm dừng tập và hỏi chuyên gia y tế.")
        transcript.clear()
        assertTrue(transcript.messages().isEmpty())
        assertEquals("", transcript.render())
    }
}
