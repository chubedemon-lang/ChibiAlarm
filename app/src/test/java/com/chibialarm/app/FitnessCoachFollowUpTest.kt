package com.chibialarm.app

import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessCoachFollowUpTest {
    @Test fun missingGoalPromptsGoal() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("15 phút không dụng cụ"))
        assertTrue(question.contains("sức mạnh"))
    }

    @Test fun missingDurationPromptsTime() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("giãn cơ không dụng cụ"))
        assertTrue(question.contains("bao nhiêu phút"))
    }

    @Test fun injuryTakesPriority() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("đang đau ngực, giãn cơ 15 phút"))
        assertTrue(question.contains("đừng cố tập"))
    }

    @Test fun missingEquipmentPromptsEquipment() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("giãn cơ 15 phút"))
        assertTrue(question.contains("thảm"))
    }
}
