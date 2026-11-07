package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class FitnessCoachConversationTest {
    @Test fun accumulatesAcrossTurnsWithoutErasingPreviousAnswers() {
        val first = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ 15 phút"))
        val second = FitnessCoachConversation.merge(first, FitnessNaturalRequest.parse("không dụng cụ"))
        assertEquals(2, second.goal)
        assertEquals(15, second.minutes)
        assertEquals(0, second.equipment)
        assertTrue(FitnessCoachFollowUp.nextQuestion(second).contains("hạn chế"))
    }
    @Test fun injuryWarningCannotBeClearedByLaterReply() {
        val first = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("đang đau khớp"))
        val second = FitnessCoachConversation.merge(first, FitnessNaturalRequest.parse("khỏe, 20 phút"))
        assertEquals(2, second.impact)
        assertTrue(FitnessCoachFollowUp.nextQuestion(second).contains("chuyên gia"))
    }
    @Test fun laterExplicitDurationCanReplaceEarlierDuration() {
        val first = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("10 phút"))
        val second = FitnessCoachConversation.merge(first, FitnessNaturalRequest.parse("20 phút"))
        assertEquals(20, second.minutes)
    }
    @Test fun shortDurationAnswerUsesLastQuestion() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ"))
        val reply = FitnessCoachConversation.parseReply(previous, "15")
        assertEquals(15, FitnessCoachConversation.merge(previous, reply).minutes)
    }
    @Test fun bareNoOnlyMeansNoEquipmentWhenAskedAboutEquipment() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ 15 phút"))
        val reply = FitnessCoachConversation.parseReply(previous, "không")
        assertEquals(0, FitnessCoachConversation.merge(previous, reply).equipment)
        assertNull(reply.impact)
    }
    @Test fun shortAnswerNeverClearsInjuryWarning() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("đang đau khớp"))
        val reply = FitnessCoachConversation.parseReply(previous, "không")
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).impact)
        assertNull(reply.equipment)
    }
    @Test fun outOfRangeBareNumberIsNotAccepted() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ"))
        assertNull(FitnessCoachConversation.parseReply(previous, "999").minutes)
    }
}
