package com.chibialarm.app

import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessCoachFollowUpTest {
    @Test fun missingGoalPromptsGoal() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("15 phút không dụng cụ"))
        assertTrue(question.contains("sức mạnh"))
    }

    @Test fun missingExperiencePromptsTrainingLevel() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("giãn cơ 15 phút"))
        assertTrue(question.contains("trình độ"))
        assertTrue(question.contains("mới bắt đầu"))
    }

    @Test fun missingDurationPromptsTime() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("giãn cơ không dụng cụ, đang tập đều"))
        assertTrue(question.contains("bao nhiêu phút"))
    }

    @Test fun injuryTakesPriority() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("đang đau ngực, giãn cơ 15 phút"))
        assertTrue(question.contains("đừng cố tập"))
    }

    @Test fun missingEquipmentPromptsEquipment() {
        val question = FitnessCoachFollowUp.nextQuestion(FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều"))
        assertTrue(question.contains("thảm"))
    }
}
