package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSpeechTextTest {
    @Test fun shortAnswerRemainsOneUtterance() {
        val answer = "Squat không tạ. Nghỉ giữa hiệp đề xuất: 60 giây."

        assertEquals(listOf(answer), WorkoutSpeechText.chunks(answer))
    }

    @Test fun longAnswerPrefersSentenceBoundariesAndKeepsEveryWord() {
        val answer = "Bước một: đứng vững và đặt chân rộng bằng vai. " +
            "Bước hai: đẩy hông nhẹ về sau, giữ lưng tự nhiên. " +
            "Bước ba: đứng lên có kiểm soát và thở đều."
        val chunks = WorkoutSpeechText.chunks(answer, maxChars = 64)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 64 })
        assertEquals(answer, chunks.joinToString(" "))
    }

    @Test fun longAnswerFallsBackToWordBoundariesWithoutSplittingWords() {
        val answer = "Đứng vững, thở đều, di chuyển chậm và dừng nếu khó chịu."
        val chunks = WorkoutSpeechText.chunks(answer, maxChars = 40)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 40 })
        assertEquals(answer, chunks.joinToString(" "))
    }

    @Test fun whitespaceAndLineBreaksAreNormalizedWithoutLosingContent() {
        val chunks = WorkoutSpeechText.chunks("Bước một.\n\n  Bước hai.\tBước ba.", maxChars = 40)

        assertEquals("Bước một. Bước hai. Bước ba.", chunks.joinToString(" "))
    }

    @Test fun blankAnswerProducesNoUtterance() {
        assertTrue(WorkoutSpeechText.chunks(" \n\t ").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeTinyChunkBounds() {
        WorkoutSpeechText.chunks("Một câu trả lời.", maxChars = 10)
    }
}
