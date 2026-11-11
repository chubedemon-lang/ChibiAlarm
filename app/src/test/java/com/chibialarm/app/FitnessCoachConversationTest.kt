package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class FitnessCoachConversationTest {
    @Test fun accumulatesAcrossTurnsWithoutErasingPreviousAnswers() {
        val first = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều"))
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
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ, đang tập đều"))
        val reply = FitnessCoachConversation.parseReply(previous, "15")
        assertEquals(15, FitnessCoachConversation.merge(previous, reply).minutes)
    }
    @Test fun bareNoOnlyMeansNoEquipmentWhenAskedAboutEquipment() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều"))
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
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ, đang tập đều"))
        assertNull(FitnessCoachConversation.parseReply(previous, "999").minutes)
    }
    @Test fun shortMatAnswerUsesEquipmentQuestion() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều"))
        val reply = FitnessCoachConversation.parseReply(previous, "thảm")
        assertEquals(1, FitnessCoachConversation.merge(previous, reply).equipment)
    }
    @Test fun shortResistanceBandAnswerUsesEquipmentQuestion() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều"))
        val reply = FitnessCoachConversation.parseReply(previous, "dây kháng lực")
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).equipment)
    }
    @Test fun shortEquipmentAnswerCannotOverrideSafetyWarning() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("đang đau khớp"))
        val reply = FitnessCoachConversation.parseReply(previous, "tạ")
        assertNull(reply.equipment)
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).impact)
    }
    @Test fun equipmentReplyWithoutEquipmentQuestionIsNotGuessed() {
        val previous = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("giãn cơ, đang tập đều"))
        assertNull(FitnessCoachConversation.parseReply(previous, "thảm").equipment)
    }
    @Test fun shortDurationGetsAcknowledgedInsteadOfGenericFailure() {
        val previous = FitnessNaturalRequest.parse("giãn cơ, đang tập đều")
        val reply = FitnessCoachConversation.parseReply(previous, "15")
        assertEquals(15, reply.minutes)
        assertTrue(reply.reply.contains("15 phút"))
        assertFalse(reply.reply.contains("chưa hiểu"))
    }
    @Test fun contextualEquipmentReplyIsAcknowledged() {
        val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều")
        val reply = FitnessCoachConversation.parseReply(previous, "thảm")
        assertEquals(1, reply.equipment)
        assertTrue(reply.reply.contains("thảm"))
    }

    @Test fun shortNoRestrictionsAnswerOnlyAfterSafetyQuestion() {
        val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút không dụng cụ, đang tập đều")
        val reply = FitnessCoachConversation.parseReply(previous, "không")
        assertEquals(0, reply.impact)
        assertTrue(reply.reply.contains("hạn chế"))
    }

    @Test fun lowImpactShortAnswerIsRemembered() {
        val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút không dụng cụ, đang tập đều")
        val reply = FitnessCoachConversation.parseReply(previous, "tránh nhảy")
        assertEquals(1, FitnessCoachConversation.merge(previous, reply).impact)
    }

    @Test fun energyAnswerWorksAfterSafetyPreference() {
        val previous = FitnessCoachConversation.merge(
            FitnessNaturalRequest.parse("giãn cơ 15 phút không dụng cụ, đang tập đều"),
            FitnessNaturalRequest.parse("tránh nhảy")
        )
        val reply = FitnessCoachConversation.parseReply(previous, "hơi mệt")
        assertEquals(0, FitnessCoachConversation.merge(previous, reply).energy)
    }

    @Test fun shortNoCannotDismissExistingInjury() {
        val previous = FitnessNaturalRequest.parse("đang đau khớp")
        val reply = FitnessCoachConversation.parseReply(previous, "không")
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).impact)
    }

    @Test fun contextualBeginnerLevelDoesNotOverwriteChosenGoal() {
        val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút")
        assertTrue(FitnessCoachFollowUp.nextQuestion(previous).contains("trình độ"))
        val reply = FitnessCoachConversation.parseReply(previous, "mới bắt đầu")
        val merged = FitnessCoachConversation.merge(previous, reply)
        assertEquals(2, merged.goal)
        assertEquals(0, merged.level)
        assertTrue(reply.reply.contains("mới bắt đầu"))
    }

    @Test fun laterExperienceAnswerUpdatesOnlyTheLevel() {
        val previous = FitnessNaturalRequest.parse("tăng sức mạnh 20 phút, mới bắt đầu")
        val reply = FitnessCoachConversation.parseReply(previous, "đang tập đều")
        val merged = FitnessCoachConversation.merge(previous, reply)
        assertEquals(0, merged.goal)
        assertEquals(1, merged.level)
    }

    @Test fun shortStrengthGoalIsAcknowledgedInContext() {
        val previous = FitnessNaturalRequest.parse("xin chào")
        val reply = FitnessCoachConversation.parseReply(previous, "sức mạnh")
        assertEquals(0, FitnessCoachConversation.merge(previous, reply).goal)
        assertTrue(reply.reply.contains("sức mạnh"))
    }

    @Test fun shortCardioGoalIsAcknowledgedInContext() {
        val previous = FitnessNaturalRequest.parse("xin chào")
        val reply = FitnessCoachConversation.parseReply(previous, "tim mạch")
        assertEquals(1, reply.goal)
        assertTrue(reply.reply.contains("tim mạch"))
    }

    @Test fun goalAnswerCannotBypassPainWarning() {
        val previous = FitnessNaturalRequest.parse("đang đau khớp")
        val reply = FitnessCoachConversation.parseReply(previous, "sức mạnh")
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).impact)
        assertFalse(reply.reply.contains("Đã ghi nhận mục tiêu"))
    }

    @Test fun contextualReplyNormalizationDoesNotDependOnDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút")
            val reply = FitnessCoachConversation.parseReply(previous, "MỚI BẮT ĐẦU")
            assertEquals(0, reply.level)
            assertTrue(reply.reply.contains("mới bắt đầu"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun wristLoadPreferenceSurvivesLaterConversationTurns() {
        val first = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("không chống tay"))
        val second = FitnessCoachConversation.merge(first, FitnessNaturalRequest.parse("sức mạnh 30 phút"))
        assertEquals(true, second.avoidWristLoad)
        assertEquals(0, second.goal)
        assertEquals(30, second.minutes)
    }



    @Test fun floorPositionPreferenceSurvivesLaterConversationTurns() {
        val first = FitnessCoachConversation.merge(null, FitnessNaturalRequest.parse("không nằm sàn"))
        val second = FitnessCoachConversation.merge(first, FitnessNaturalRequest.parse("sức mạnh 30 phút"))
        assertEquals(true, second.avoidFloorPositions)
        assertEquals(0, second.goal)
        assertEquals(30, second.minutes)
    }

    @Test fun removingEquipmentOnALaterTurnPreservesTheRestOfThePlan() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("tăng cơ 30 phút, mới bắt đầu, có tạ")
        )
        assertEquals(2, first.equipment)

        val reply = FitnessCoachConversation.parseReply(first, "không còn tạ nữa")
        val updated = FitnessCoachConversation.merge(first, reply)

        assertEquals(0, updated.equipment)
        assertEquals(0, updated.goal)
        assertEquals(0, updated.level)
        assertEquals(30, updated.minutes)
    }

}
