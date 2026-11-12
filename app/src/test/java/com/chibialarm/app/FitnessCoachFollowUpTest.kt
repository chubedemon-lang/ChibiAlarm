package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessCoachFollowUpTest {
    @Test fun missingGoalPromptsGoal() {
        val parsed = FitnessNaturalRequest.parse("15 phút không dụng cụ")
        assertEquals(FitnessCoachFollowUp.Question.GOAL, FitnessCoachFollowUp.nextQuestionType(parsed))
        assertTrue(FitnessCoachFollowUp.nextQuestion(parsed).contains("sức mạnh"))
    }

    @Test fun missingExperiencePromptsTrainingLevel() {
        val parsed = FitnessNaturalRequest.parse("giãn cơ 15 phút")
        assertEquals(FitnessCoachFollowUp.Question.LEVEL, FitnessCoachFollowUp.nextQuestionType(parsed))
        val question = FitnessCoachFollowUp.nextQuestion(parsed)
        assertTrue(question.contains("trình độ"))
        assertTrue(question.contains("mới bắt đầu"))
    }

    @Test fun missingDurationPromptsTime() {
        val parsed = FitnessNaturalRequest.parse("giãn cơ không dụng cụ, đang tập đều")
        assertEquals(FitnessCoachFollowUp.Question.DURATION, FitnessCoachFollowUp.nextQuestionType(parsed))
        assertTrue(FitnessCoachFollowUp.nextQuestion(parsed).contains("bao nhiêu phút"))
    }

    @Test fun injuryTakesPriority() {
        val parsed = FitnessNaturalRequest.parse("đang đau ngực, giãn cơ 15 phút")
        assertEquals(FitnessCoachFollowUp.Question.SAFETY_STOP, FitnessCoachFollowUp.nextQuestionType(parsed))
        assertTrue(FitnessCoachFollowUp.nextQuestion(parsed).contains("đừng cố tập"))
    }

    @Test fun missingEquipmentPromptsEquipment() {
        val parsed = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều")
        assertEquals(FitnessCoachFollowUp.Question.EQUIPMENT, FitnessCoachFollowUp.nextQuestionType(parsed))
        assertTrue(FitnessCoachFollowUp.nextQuestion(parsed).contains("thảm"))
    }

    @Test fun questionTypesFollowTheWholePersonalizationSequence() {
        val start = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều, không dụng cụ")
        assertEquals(FitnessCoachFollowUp.Question.MOVEMENT_LIMITS, FitnessCoachFollowUp.nextQuestionType(start))

        val withoutRestrictions = FitnessCoachConversation.merge(
            start,
            FitnessCoachConversation.parseReply(start, "không")
        )
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(withoutRestrictions))

        val ready = FitnessCoachConversation.merge(
            withoutRestrictions,
            FitnessCoachConversation.parseReply(withoutRestrictions, "khỏe")
        )
        assertEquals(FitnessCoachFollowUp.Question.COMPLETE, FitnessCoachFollowUp.nextQuestionType(ready))
    }

    @Test fun safetyPromptAlwaysWinsOverMissingProfileFields() {
        val parsed = FitnessNaturalRequest.parse("đau gối, chưa biết tập gì, 15 phút")
        assertEquals(FitnessCoachFollowUp.Question.SAFETY_STOP, FitnessCoachFollowUp.nextQuestionType(parsed))
    }
}
