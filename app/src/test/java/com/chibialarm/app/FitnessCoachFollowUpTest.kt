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

    @Test fun equipmentPromptIncludesStableChairAsAnExplicitOption() {
        val parsed = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều")
        assertTrue(FitnessCoachFollowUp.nextQuestion(parsed).contains("ghế vững chắc"))
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
        assertEquals(FitnessCoachFollowUp.Question.KNEELING, FitnessCoachFollowUp.nextQuestionType(noOverheadRestriction))

        val noKneelingRestriction = FitnessCoachConversation.merge(
            noOverheadRestriction,
            FitnessCoachConversation.parseReply(noOverheadRestriction, "không")
        )
        assertEquals(false, noKneelingRestriction.avoidKneeling)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(noKneelingRestriction))

        val ready = FitnessCoachConversation.merge(
            noKneelingRestriction,
            FitnessCoachConversation.parseReply(noKneelingRestriction, "khỏe")
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
        assertEquals(FitnessCoachFollowUp.Question.KNEELING, FitnessCoachFollowUp.nextQuestionType(overhead))

        val kneeling = FitnessCoachConversation.merge(
            overhead,
            FitnessCoachConversation.parseReply(overhead, "có")
        )
        assertTrue(kneeling.avoidKneeling == true)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(kneeling))
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
        assertEquals(FitnessCoachFollowUp.Question.KNEELING, FitnessCoachFollowUp.nextQuestionType(noOverhead))

        val noKneeling = FitnessCoachConversation.merge(
            noOverhead,
            FitnessCoachConversation.parseReply(noOverhead, "không cần tránh quỳ gối")
        )
        assertFalse(noKneeling.avoidKneeling ?: true)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(noKneeling))
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
    @Test fun quickRepliesFollowTheCurrentQuestionAndNeverOfferASafetyBypass() {
        val goal = FitnessNaturalRequest.parse("xin chào")
        assertEquals(
            listOf("Sức mạnh", "Tim mạch", "Giãn cơ"),
            FitnessCoachFollowUp.quickReplies(goal).map { it.label }
        )

        val injured = FitnessNaturalRequest.parse("đau ngực, giãn cơ 15 phút")
        assertEquals(FitnessCoachFollowUp.Question.SAFETY_STOP, FitnessCoachFollowUp.nextQuestionType(injured))
        assertTrue(FitnessCoachFollowUp.quickReplies(injured).isEmpty())
    }

    @Test fun spokenPromptReadsTheSameGoalChoicesShownAsQuickReplyChips() {
        val parsed = FitnessNaturalRequest.parse("xin chào")
        val spoken = FitnessCoachFollowUp.spokenPrompt(
            "Chibi hỏi tiếp: Mày muốn tập gì hôm nay?",
            parsed
        )

        assertTrue(spoken.contains("Lựa chọn 1: Sức mạnh."))
        assertTrue(spoken.contains("Lựa chọn 2: Tim mạch."))
        assertTrue(spoken.contains("Lựa chọn 3: Giãn cơ."))
        assertTrue(spoken.contains("trả lời tự nhiên"))
        assertTrue(spoken.contains("chưa thay đổi giáo án"))
    }

    @Test fun spokenEquipmentPromptReadsEveryExplicitToolCombination() {
        val parsed = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều")
        val spoken = FitnessCoachFollowUp.spokenPrompt(
            FitnessCoachFollowUp.nextQuestion(parsed),
            parsed
        )

        listOf(
            "Không dụng cụ", "Thảm tập", "Tạ đơn", "Dây kháng lực",
            "Ghế vững", "Thảm + ghế", "Tạ + dây + thảm"
        ).forEach { label -> assertTrue("Missing spoken option: $label", spoken.contains(label)) }
        assertTrue(spoken.length <= 1_800)
    }

    @Test fun safetyStopAndCompletedProfilesDoNotAppendChoicePrompts() {
        val safety = FitnessNaturalRequest.parse("đau ngực, giãn cơ 15 phút")
        val safetyMessage = FitnessCoachFollowUp.nextQuestion(safety)
        assertEquals(safetyMessage, FitnessCoachFollowUp.spokenPrompt(safetyMessage, safety))
        assertFalse(FitnessCoachFollowUp.spokenPrompt(safetyMessage, safety).contains("Lựa chọn"))

        val complete = FitnessNaturalRequest.Result(
            goal = 2,
            level = 1,
            energy = 1,
            minutes = 20,
            equipment = 0,
            impact = 0,
            reply = "complete",
            avoidWristLoad = false,
            avoidFloorPositions = false,
            avoidOverheadReach = false,
            additionalEquipment = emptySet(),
            avoidKneeling = false
        )
        assertEquals(FitnessCoachFollowUp.Question.COMPLETE, FitnessCoachFollowUp.nextQuestionType(complete))
        val completeMessage = FitnessCoachFollowUp.nextQuestion(complete)
        assertEquals(completeMessage, FitnessCoachFollowUp.spokenPrompt(completeMessage, complete))
    }

    @Test fun spokenPromptBoundsLongCoachCopyWithoutDroppingItsChoiceList() {
        val parsed = FitnessNaturalRequest.parse("xin chào")
        val spoken = FitnessCoachFollowUp.spokenPrompt("x".repeat(2_000), parsed)
        assertTrue(spoken.length <= 1_800)
        assertTrue(spoken.contains("Lựa chọn 1: Sức mạnh."))
        assertTrue(spoken.contains("Lựa chọn 3: Giãn cơ."))
    }

    @Test fun quickReplyDurationIsParsedOnlyForTheDurationQuestion() {
        val previous = FitnessNaturalRequest.parse("giãn cơ, đang tập đều")
        val reply = FitnessCoachFollowUp.quickReplies(previous).single { it.utterance == "15 phút" }
        val merged = FitnessCoachConversation.merge(
            previous,
            FitnessCoachConversation.parseReply(previous, reply.utterance)
        )

        assertEquals(15, merged.minutes)
        assertEquals(2, merged.goal)
        assertEquals(1, merged.level)
        assertEquals(FitnessCoachFollowUp.Question.EQUIPMENT, FitnessCoachFollowUp.nextQuestionType(merged))
    }

    @Test fun equipmentQuickReplyDoesNotAssumeAnyOtherTool() {
        val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều")
        val reply = FitnessCoachFollowUp.quickReplies(previous).single { it.label == "Tạ đơn" }
        val merged = FitnessCoachConversation.merge(
            previous,
            FitnessCoachConversation.parseReply(previous, reply.utterance)
        )

        assertEquals(4, merged.equipment)
        assertEquals(emptySet<TrainingEquipment>(), merged.additionalEquipment)
        assertEquals(2, merged.goal)
        assertEquals(1, merged.level)
    }

    @Test fun combinedEquipmentQuickRepliesPreserveEveryExplicitTool() {
        val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều")

        val matAndChair = FitnessCoachFollowUp.quickReplies(previous).single { it.label == "Thảm + ghế" }
        val first = FitnessCoachConversation.merge(
            previous,
            FitnessCoachConversation.parseReply(previous, matAndChair.utterance)
        )
        assertEquals(1, first.equipment)
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), first.additionalEquipment)
        assertEquals(2, first.goal)
        assertEquals(1, first.level)
        assertEquals(15, first.minutes)

        val dumbbellBandMat = FitnessCoachFollowUp.quickReplies(previous).single { it.label == "Tạ + dây + thảm" }
        val second = FitnessCoachConversation.merge(
            previous,
            FitnessCoachConversation.parseReply(previous, dumbbellBandMat.utterance)
        )
        assertEquals(2, second.equipment)
        assertEquals(setOf(TrainingEquipment.MAT), second.additionalEquipment)
        assertEquals(2, second.goal)
        assertEquals(1, second.level)
        assertEquals(15, second.minutes)
    }

    @Test fun quickReplyForMovementLimitAdvancesToTheSpecificWristQuestion() {
        val previous = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều, không dụng cụ")
        val reply = FitnessCoachFollowUp.quickReplies(previous).single { it.label == "Tránh nhảy" }
        val merged = FitnessCoachConversation.merge(
            previous,
            FitnessCoachConversation.parseReply(previous, reply.utterance)
        )

        assertEquals(1, merged.impact)
        assertEquals(FitnessCoachFollowUp.Question.WRIST_LOAD, FitnessCoachFollowUp.nextQuestionType(merged))
    }

    @Test fun quickReplyWristAnswerChangesOnlyTheAskedRestriction() {
        val start = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều, không dụng cụ")
        val noJump = FitnessCoachConversation.merge(
            start,
            FitnessCoachConversation.parseReply(start, "không")
        )
        val reply = FitnessCoachFollowUp.quickReplies(noJump).single { it.label == "Cần tránh" }
        val merged = FitnessCoachConversation.merge(
            noJump,
            FitnessCoachConversation.parseReply(noJump, reply.utterance)
        )

        assertEquals(true, merged.avoidWristLoad)
        assertEquals(2, merged.goal)
        assertEquals(15, merged.minutes)
        assertEquals(0, merged.equipment)
        assertEquals(FitnessCoachFollowUp.Question.FLOOR_POSITIONS, FitnessCoachFollowUp.nextQuestionType(merged))
    }



    @Test fun kneelingRestrictionIsAskedSeparatelyAndSupportsTypedAndVoiceReplies() {
        val profile = FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều, không dụng cụ")
        val noJump = FitnessCoachConversation.merge(
            profile, FitnessCoachConversation.parseReply(profile, "không")
        )
        val noWrist = FitnessCoachConversation.merge(
            noJump, FitnessCoachConversation.parseReply(noJump, "không")
        )
        val noFloor = FitnessCoachConversation.merge(
            noWrist, FitnessCoachConversation.parseReply(noWrist, "không")
        )
        val noOverhead = FitnessCoachConversation.merge(
            noFloor, FitnessCoachConversation.parseReply(noFloor, "không")
        )

        assertEquals(FitnessCoachFollowUp.Question.KNEELING, FitnessCoachFollowUp.nextQuestionType(noOverhead))
        val replies = FitnessCoachFollowUp.quickReplies(noOverhead)
        assertEquals(listOf("Tránh quỳ gối", "Có thể quỳ gối"), replies.map { it.label })
        val spoken = FitnessCoachFollowUp.spokenPrompt(
            FitnessCoachFollowUp.nextQuestion(noOverhead), noOverhead
        )
        assertTrue(spoken.contains("Lựa chọn 1: Tránh quỳ gối."))
        assertTrue(spoken.contains("Lựa chọn 2: Có thể quỳ gối."))

        val avoid = FitnessCoachConversation.merge(
            noOverhead, FitnessCoachConversation.parseReply(noOverhead, "có")
        )
        assertTrue(avoid.avoidKneeling == true)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(avoid))

        val allow = FitnessCoachConversation.merge(
            noOverhead, FitnessCoachConversation.parseReply(noOverhead, "có thể quỳ gối")
        )
        assertEquals(false, allow.avoidKneeling)
        assertEquals(15, allow.minutes)
        assertEquals(2, allow.goal)
        assertEquals(1, allow.level)
        assertEquals(0, allow.equipment)
    }

    @Test fun energyQuickRepliesIncludeNaturalFatigueWordingButNeverAppearForSafetyStop() {
        val energyProfile = FitnessNaturalRequest.Result(
            goal = 2,
            level = 1,
            minutes = 20,
            equipment = 0,
            impact = 0,
            reply = "profile",
            avoidWristLoad = false,
            avoidFloorPositions = false,
            avoidOverheadReach = false,
            additionalEquipment = emptySet(),
            avoidKneeling = false
        )
        val replies = FitnessCoachFollowUp.quickReplies(energyProfile)
        assertTrue(replies.any { it.label == "Hơi đuối" && it.utterance == "hơi đuối" })
        assertTrue(replies.any { it.label == "Mỏi cơ" && it.utterance == "mỏi cơ" })

        val safetyProfile = energyProfile.copy(impact = 2)
        assertTrue(FitnessCoachFollowUp.quickReplies(safetyProfile).isEmpty())
    }

}
