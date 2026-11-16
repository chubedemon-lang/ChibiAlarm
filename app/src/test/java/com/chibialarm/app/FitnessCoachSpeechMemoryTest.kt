package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class FitnessCoachSpeechMemoryTest {
    @Test fun remembersOnlyTheLatestCoachReply() {
        val memory = FitnessCoachSpeechMemory()
        memory.remember("Đã ghi nhận mục tiêu giãn cơ.")
        memory.remember("Mày có thảm tập hay tạ?")
        assertEquals("Mày có thảm tập hay tạ?", memory.replayText(true, true))
    }

    @Test fun replayRequiresVoiceToBeEnabledAndEngineReady() {
        val memory = FitnessCoachSpeechMemory()
        memory.remember("Chibi đã ghi nhận.")
        assertNull(memory.replayText(voiceEnabled = false, engineReady = true))
        assertNull(memory.replayText(voiceEnabled = true, engineReady = false))
        assertEquals("Chibi đã ghi nhận.", memory.replayText(true, true))
    }

    @Test fun longRepliesAreBoundedAndPreferAWordBoundary() {
        val memory = FitnessCoachSpeechMemory(maxCharacters = 48)
        memory.remember("Chibi đã ghi nhận mục tiêu tập luyện và sẽ hỏi thêm về dụng cụ, thời gian cùng trình độ.")
        val replay = memory.replayText(true, true)!!
        assertTrue(replay.endsWith("…"))
        assertTrue(replay.length <= 48)
        assertFalse(replay.dropLast(1).endsWith(" "))
    }

    @Test fun blankReplyAndClearRemoveThePreviousAnswer() {
        val memory = FitnessCoachSpeechMemory()
        memory.remember("Một câu trả lời.")
        memory.remember("   ")
        assertFalse(memory.hasReply())
        memory.remember("Câu trả lời khác.")
        memory.clear()
        assertNull(memory.replayText(true, true))
    }
}
