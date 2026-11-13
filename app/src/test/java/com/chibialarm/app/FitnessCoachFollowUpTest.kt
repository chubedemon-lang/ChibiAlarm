package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val noJumpRestriction = FitnessCoachConversation.merge(
            start,
            FitnessCoachConversation.parseReply(start, "không")
        )
        assertEquals(FitnessCoachFollowUp.Question.WRIST_LOAD, FitnessCoachFollowUp.nextQuestionType(noJumpRestriction))

        val noWristRestriction = FitnessCoachConversation.merge(
            noJumpRestriction,
            FitnessCoachConversation.parseReply(noJumpRestriction, "không")
        )
        assertEquals(FitnessCoachFollowUp.Question.FLOOR_POSITIONS, FitnessCoachFollowUp.nextQuestionType(noWristRestriction))

        val noFloorRestriction = FitnessCoachConversation.merge(
            noWristRestriction,
            FitnessCoachConversation.parseReply(noWristRestriction, "không")
        )
        assertEquals(FitnessCoachFollowUp.Question.OVERHEAD_REACH, FitnessCoachFollowUp.nextQuestionType(noFloorRestriction))

        val noOverheadRestriction = FitnessCoachConversation.merge(
            noFloorRestriction,
            FitnessCoachConversation.parseReply(noFloorRestriction, "không")
        )
        assertEquals(false, noOverheadRestriction.avoidOverheadReach)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(noOverheadRestriction))

        val ready = FitnessCoachConversation.merge(
            noOverheadRestriction,
            FitnessCoachConversation.parseReply(noOverheadRestriction, "khỏe")
        )
        assertEquals(FitnessCoachFollowUp.Question.COMPLETE, FitnessCoachFollowUp.nextQuestionType(ready))
    }

    @Test fun aWristRestrictionGivenEarlyIsKeptWhileTheCoachClarifiesJumping() {
        val start = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều, không dụng cụ")
        val partial = FitnessCoachConversation.merge(
            start,
            FitnessCoachConversation.parseReply(start, "không chống tay")
        )

        assertTrue(partial.avoidWristLoad == true)
        assertEquals(FitnessCoachFollowUp.Question.MOVEMENT_LIMITS, FitnessCoachFollowUp.nextQuestionType(partial))
        assertTrue(FitnessCoachFollowUp.nextQuestion(partial).contains("cổ tay"))
        assertTrue(FitnessCoachFollowUp.nextQuestion(partial).contains("nhảy"))

        val jumpPreference = FitnessCoachConversation.merge(
            partial,
            FitnessCoachConversation.parseReply(partial, "không")
        )
        assertEquals(FitnessCoachFollowUp.Question.FLOOR_POSITIONS, FitnessCoachFollowUp.nextQuestionType(jumpPreference))
        assertTrue(jumpPreference.avoidWristLoad == true)
    }

    @Test fun yesAnswersSetTheSpecificMovementRestrictionBeingAsked() {
        val start = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều, không dụng cụ")
        val noJump = FitnessCoachConversation.merge(start, FitnessCoachConversation.parseReply(start, "không"))
        val wrist = FitnessCoachConversation.merge(noJump, FitnessCoachConversation.parseReply(noJump, "có"))
        assertTrue(wrist.avoidWristLoad == true)
        assertEquals(FitnessCoachFollowUp.Question.FLOOR_POSITIONS, FitnessCoachFollowUp.nextQuestionType(wrist))

        val floor = FitnessCoachConversation.merge(wrist, FitnessCoachConversation.parseReply(wrist, "có"))
        assertTrue(floor.avoidFloorPositions == true)
        assertEquals(FitnessCoachFollowUp.Question.OVERHEAD_REACH, FitnessCoachFollowUp.nextQuestionType(floor))

        val overhead = FitnessCoachConversation.merge(
            floor,
            FitnessCoachConversation.parseReply(floor, "có")
        )
        assertTrue(overhead.avoidOverheadReach == true)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(overhead))
    }

    @Test fun explicitNoRestrictionPhrasesAreNotTurnedIntoRestrictions() {
        val start = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều, không dụng cụ")
        val noJump = FitnessCoachConversation.merge(start, FitnessCoachConversation.parseReply(start, "không"))
        val noWrist = FitnessCoachConversation.merge(
            noJump,
            FitnessCoachConversation.parseReply(noJump, "không có hạn chế cổ tay")
        )
        assertFalse(noWrist.avoidWristLoad ?: true)
        assertEquals(FitnessCoachFollowUp.Question.FLOOR_POSITIONS, FitnessCoachFollowUp.nextQuestionType(noWrist))

        val noFloor = FitnessCoachConversation.merge(
            noWrist,
            FitnessCoachConversation.parseReply(noWrist, "không cần tránh nằm sàn")
        )
        assertFalse(noFloor.avoidFloorPositions ?: true)
        assertEquals(FitnessCoachFollowUp.Question.OVERHEAD_REACH, FitnessCoachFollowUp.nextQuestionType(noFloor))

        val noOverhead = FitnessCoachConversation.merge(
            noFloor,
            FitnessCoachConversation.parseReply(noFloor, "không cần tránh đưa tay qua đầu")
        )
        assertFalse(noOverhead.avoidOverheadReach ?: true)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(noOverhead))
    }

    @Test fun safetyPromptAlwaysWinsOverMissingProfileFields() {
        val parsed = FitnessNaturalRequest.parse("đau gối, chưa biết tập gì, 15 phút")
        assertEquals(FitnessCoachFollowUp.Question.SAFETY_STOP, FitnessCoachFollowUp.nextQuestionType(parsed))
    }

    @Test fun anEarlierSafetyStopCannotBeClearedByLaterNoAnswers() {
        val injured = FitnessNaturalRequest.parse("đau gối, giãn cơ 15 phút, đang tập đều, không dụng cụ")
        val laterNo = FitnessCoachConversation.merge(
            injured,
            FitnessCoachConversation.parseReply(injured, "không")
        )
        assertEquals(2, laterNo.impact)
        assertEquals(FitnessCoachFollowUp.Question.SAFETY_STOP, FitnessCoachFollowUp.nextQuestionType(laterNo))
    }
}
