package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessCoachSpokenDurationReplyTest {
    private fun durationQuestion(): FitnessNaturalRequest.Result =
        FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("giãn cơ, đang tập đều")
        )

    @Test fun bareVietnameseSpokenNumberUsesTheDurationQuestionContext() {
        val previous = durationQuestion()

        listOf(
            "mười lăm" to 15,
            "hai mươi lăm" to 25,
            "ba mươi" to 30,
            "một trăm hai mươi" to 120
        ).forEach { (answer, expected) ->
            val reply = FitnessCoachConversation.parseReply(previous, answer)
            assertEquals(answer, expected, reply.minutes)
            assertEquals(answer, expected, FitnessCoachConversation.merge(previous, reply).minutes)
            assertTrue(answer, reply.reply.contains("$expected phút"))
            assertFalse(answer, reply.reply.contains("chưa hiểu"))
        }
    }

    @Test fun numericAndExplicitUnitAnswersKeepWorking() {
        val previous = durationQuestion()
        assertEquals(15, FitnessCoachConversation.parseReply(previous, "15").minutes)
        assertEquals(25, FitnessCoachConversation.parseReply(previous, "hai mươi lăm phút").minutes)
        assertEquals(30, FitnessCoachConversation.parseReply(previous, "nửa tiếng").minutes)
    }

    @Test fun ambiguousOrOutOfRangeSpokenAnswersAreNotAcceptedAsDuration() {
        val previous = durationQuestion()
        assertNull(FitnessCoachConversation.parseReply(previous, "bốn").minutes)
        assertNull(FitnessCoachConversation.parseReply(previous, "một trăm hai mươi mốt").minutes)
        assertNull(FitnessCoachConversation.parseReply(previous, "mười lăm rưỡi").minutes)
        assertNull(FitnessCoachConversation.parseReply(previous, "không").minutes)
    }

    @Test fun bareNumberIsNotReinterpretedWhenChibiIsAskingSomethingElse() {
        val previous = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("giãn cơ 15 phút")
        )
        assertTrue(FitnessCoachFollowUp.nextQuestion(previous).contains("trình độ"))
        val reply = FitnessCoachConversation.parseReply(previous, "mười lăm")
        assertNull(reply.minutes)
        assertNull(reply.level)
    }

    @Test fun spokenDurationCannotClearAnEarlierPainStop() {
        val previous = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("đang đau khớp")
        )
        val reply = FitnessCoachConversation.parseReply(previous, "mười lăm")
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).impact)
        assertNull(reply.minutes)
    }
}
