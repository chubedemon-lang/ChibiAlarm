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

    @Test fun ambiguousDurationReplyAsksForOneExactTimeInsteadOfChoosingAnEndpoint() {
        val previous = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("giãn cơ, đang tập đều")
        )
        val phrases = listOf(
            "15 đến 20 phút",
            "15-20 phút",
            "một tiếng 30 phút",
            "tối đa 20 phút",
            "15 phút rưỡi"
        )
        for (phrase in phrases) {
            val reply = FitnessCoachConversation.parseReply(previous, phrase)
            val merged = FitnessCoachConversation.merge(previous, reply)
            assertNull("Must not silently choose a duration for $phrase", merged.minutes)
            assertTrue("Must explain the clarification for $phrase", reply.reply.contains("một thời lượng cụ thể"))
            assertEquals(
                FitnessCoachFollowUp.Question.DURATION,
                FitnessCoachFollowUp.nextQuestionType(merged)
            )
        }
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
        assertEquals(5, FitnessCoachConversation.merge(previous, reply).equipment)
    }
    @Test fun shortDumbbellAnswerUsesOnlyTheDumbbellSelection() {
        val previous = FitnessCoachConversation.parseReply(
            FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều"),
            "tạ đơn"
        )
        assertEquals(4, previous.equipment)
        assertTrue(previous.reply.contains("không giả định"))
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

    @Test fun shortStableChairAnswerUsesEquipmentQuestion() {
        val previous = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("giãn cơ 15 phút, đang tập đều")
        )
        val reply = FitnessCoachConversation.parseReply(previous, "ghế.")
        val merged = FitnessCoachConversation.merge(previous, reply)

        assertEquals(3, merged.equipment)
        assertEquals(emptySet<TrainingEquipment>(), merged.additionalEquipment)
        assertTrue(reply.reply.contains("ghế vững chắc"))
    }

    @Test fun stableChairAnswerCannotOverrideSafetyWarning() {
        val previous = FitnessNaturalRequest.parse("đang đau khớp")
        val reply = FitnessCoachConversation.parseReply(previous, "ghế")

        assertNull(reply.equipment)
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).impact)
    }

    @Test fun stableChairAnswerOutsideEquipmentQuestionIsNotGuessed() {
        val previous = FitnessNaturalRequest.parse(
            "giãn cơ 15 phút không dụng cụ, đang tập đều"
        )
        val reply = FitnessCoachConversation.parseReply(previous, "ghế")

        assertNull(reply.equipment)
        assertEquals(0, FitnessCoachConversation.merge(previous, reply).equipment)
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

    @Test fun naturalNoMovementLimitPhrasesAdvanceOnlyTheAskedFollowUp() {
        val start = FitnessNaturalRequest.parse("giãn cơ 15 phút không dụng cụ, đang tập đều")
        for (answer in listOf(
            "không có hạn chế",
            "không bị hạn chế",
            "không có hạn chế vận động",
            "vận động bình thường",
            "Không có hạn chế vận động.",
            "Vận động bình thường!"
        )) {
            val reply = FitnessCoachConversation.parseReply(start, answer)
            val merged = FitnessCoachConversation.merge(start, reply)
            assertEquals("Failed for: $answer", 0, merged.impact)
            assertEquals(
                "Failed for: $answer",
                FitnessCoachFollowUp.Question.WRIST_LOAD,
                FitnessCoachFollowUp.nextQuestionType(merged)
            )
        }
    }

    @Test fun noMovementLimitAnswerPreservesAnEarlierSpecificWristRestriction() {
        val start = FitnessNaturalRequest.parse(
            "giãn cơ 15 phút không dụng cụ, đang tập đều, không chống tay"
        )
        assertEquals(true, start.avoidWristLoad)
        assertEquals(FitnessCoachFollowUp.Question.MOVEMENT_LIMITS,
            FitnessCoachFollowUp.nextQuestionType(start))

        val reply = FitnessCoachConversation.parseReply(start, "không bị hạn chế vận động")
        val merged = FitnessCoachConversation.merge(start, reply)

        assertEquals(0, merged.impact)
        assertEquals(true, merged.avoidWristLoad)
        assertEquals(FitnessCoachFollowUp.Question.FLOOR_POSITIONS,
            FitnessCoachFollowUp.nextQuestionType(merged))
    }

    @Test fun affirmativeMovementLimitPhraseIsScopedToTheMovementQuestion() {
        val start = FitnessNaturalRequest.parse("giãn cơ 15 phút không dụng cụ, đang tập đều")
        val reply = FitnessCoachConversation.parseReply(start, "có hạn chế vận động")
        val merged = FitnessCoachConversation.merge(start, reply)

        assertEquals(1, merged.impact)
        assertEquals(FitnessCoachFollowUp.Question.WRIST_LOAD,
            FitnessCoachFollowUp.nextQuestionType(merged))
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



    @Test fun spokenOverheadRestrictionSurvivesLaterConversationTurnsAndCanBeRelaxedExplicitly() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("không đưa tay qua đầu")
        )
        assertEquals(true, first.avoidOverheadReach)

        val second = FitnessCoachConversation.merge(
            first,
            FitnessNaturalRequest.parse("sức mạnh 30 phút")
        )
        assertEquals(true, second.avoidOverheadReach)
        assertEquals(0, second.goal)
        assertEquals(30, second.minutes)

        val relaxed = FitnessCoachConversation.merge(
            second,
            FitnessNaturalRequest.parse("có thể đưa tay qua đầu")
        )
        assertEquals(false, relaxed.avoidOverheadReach)
        assertEquals(0, relaxed.goal)
        assertEquals(30, relaxed.minutes)
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
        assertEquals(4, first.equipment)

        val reply = FitnessCoachConversation.parseReply(first, "không còn tạ nữa")
        val updated = FitnessCoachConversation.merge(first, reply)

        assertEquals(0, updated.equipment)
        assertEquals(0, updated.goal)
        assertEquals(0, updated.level)
        assertEquals(30, updated.minutes)
    }

    @Test fun userCanRelaxAPlanPreferenceOnALaterTurnWithoutLosingOtherContext() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("tăng cơ 30 phút, không chống tay, không nằm sàn")
        )
        assertEquals(true, first.avoidWristLoad)
        assertEquals(true, first.avoidFloorPositions)

        val second = FitnessCoachConversation.merge(
            first,
            FitnessNaturalRequest.parse("có thể chống tay và nằm sàn")
        )
        assertEquals(false, second.avoidWristLoad)
        assertEquals(false, second.avoidFloorPositions)
        assertEquals(0, second.goal)
        assertEquals(30, second.minutes)
    }

    @Test fun combinedPermissionPhraseRelaxesWristAndFloorPreferences() {
        val previous = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("không chống tay, không nằm sàn")
        )
        val next = FitnessNaturalRequest.parse("có thể chống tay và nằm sàn")
        val updated = FitnessCoachConversation.merge(previous, next)

        assertEquals(false, updated.avoidWristLoad)
        assertEquals(false, updated.avoidFloorPositions)
    }

    @Test fun combinedPermissionPhraseSupportsReverseOrder() {
        val previous = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("không chống tay, không nằm sàn")
        )
        val next = FitnessNaturalRequest.parse("có thể nằm sàn và chống tay")
        val updated = FitnessCoachConversation.merge(previous, next)

        assertEquals(false, updated.avoidWristLoad)
        assertEquals(false, updated.avoidFloorPositions)
    }

    @Test fun explicitMovementRestrictionWinsOverPermissionInSameUtterance() {
        val parsed = FitnessNaturalRequest.parse(
            "có thể chống tay nhưng không chống tay; có thể nằm sàn nhưng không quỳ sàn"
        )

        assertEquals(true, parsed.avoidWristLoad)
        assertEquals(true, parsed.avoidFloorPositions)
    }

    @Test fun relaxingMovementPreferenceNeverClearsAnEarlierPainStop() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("đau cổ tay, không chống tay")
        )
        val second = FitnessCoachConversation.merge(
            first,
            FitnessNaturalRequest.parse("bỏ hạn chế cổ tay, 20 phút")
        )
        assertEquals(false, second.avoidWristLoad)
        assertEquals(2, second.impact)
    }

    @Test fun contextualOverheadAnswerUpdatesOnlyThatPreference() {
        val start = FitnessNaturalRequest.parse(
            "giãn cơ 15 phút, đang tập đều, không dụng cụ"
        )
        val noJump = FitnessCoachConversation.merge(
            start,
            FitnessCoachConversation.parseReply(start, "không")
        )
        val noWrist = FitnessCoachConversation.merge(
            noJump,
            FitnessCoachConversation.parseReply(noJump, "không")
        )
        val noFloor = FitnessCoachConversation.merge(
            noWrist,
            FitnessCoachConversation.parseReply(noWrist, "không")
        )

        assertEquals(FitnessCoachFollowUp.Question.OVERHEAD_REACH,
            FitnessCoachFollowUp.nextQuestionType(noFloor))

        val yes = FitnessCoachConversation.parseReply(noFloor, "có")
        val restricted = FitnessCoachConversation.merge(noFloor, yes)
        assertEquals(true, restricted.avoidOverheadReach)
        assertEquals(2, restricted.goal)
        assertEquals(15, restricted.minutes)
        assertEquals(0, restricted.equipment)
        assertEquals(FitnessCoachFollowUp.Question.KNEELING,
            FitnessCoachFollowUp.nextQuestionType(restricted))
        val noKneeling = FitnessCoachConversation.merge(
            restricted,
            FitnessCoachConversation.parseReply(restricted, "không")
        )
        assertEquals(false, noKneeling.avoidKneeling)
        assertEquals(FitnessCoachFollowUp.Question.ENERGY,
            FitnessCoachFollowUp.nextQuestionType(noKneeling))

        val explicitPermission = FitnessCoachConversation.merge(
            restricted,
            FitnessNaturalRequest.parse("có thể đưa tay qua đầu")
        )
        assertEquals(false, explicitPermission.avoidOverheadReach)
        assertEquals(2, explicitPermission.goal)
        assertEquals(15, explicitPermission.minutes)
    }

    @Test fun addingMatOnALaterTurnPreservesThePreviouslySelectedDumbbell() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("tăng cơ 30 phút, đang tập đều, có tạ đơn")
        )
        assertEquals(4, first.equipment)

        val reply = FitnessCoachConversation.parseReply(first, "À, tao có thêm thảm tập")
        val updated = FitnessCoachConversation.merge(first, reply)

        assertEquals(4, updated.equipment)
        assertEquals(setOf(TrainingEquipment.MAT), updated.additionalEquipment)
        assertEquals(0, updated.goal)
        assertEquals(1, updated.level)
        assertEquals(30, updated.minutes)
        assertTrue(reply.reply.contains("giữ các lựa chọn trước đó"))
    }

    @Test fun addingResistanceBandOnALaterTurnKeepsTheDumbbellAsASeparateTool() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("tăng cơ 30 phút, có tạ đơn")
        )
        val reply = FitnessCoachConversation.parseReply(first, "Ngoài ra có thêm dây kháng lực")
        val updated = FitnessCoachConversation.merge(first, reply)

        assertEquals(4, updated.equipment)
        assertEquals(setOf(TrainingEquipment.RESISTANCE_BAND), updated.additionalEquipment)
    }

    @Test fun additiveEquipmentWordingDoesNotOverrideAnExplicitRemoval() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("tăng cơ 30 phút, có tạ đơn")
        )
        val reply = FitnessCoachConversation.parseReply(
            first,
            "Có thêm thảm tập nhưng không có tạ nữa"
        )
        val updated = FitnessCoachConversation.merge(first, reply)

        assertEquals(1, updated.equipment)
        assertEquals(emptySet<TrainingEquipment>(), updated.additionalEquipment)
    }

    @Test fun multipleToolsSurviveUnrelatedTurnsAndExplicitNoEquipmentClearsThem() {
        val first = FitnessCoachConversation.merge(
            null,
            FitnessNaturalRequest.parse("giãn cơ 20 phút, có thảm tập và ghế vững chắc")
        )
        assertEquals(1, first.equipment)
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), first.additionalEquipment)

        val second = FitnessCoachConversation.merge(
            first,
            FitnessNaturalRequest.parse("hôm nay hơi mệt")
        )
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), second.additionalEquipment)

        val cleared = FitnessCoachConversation.merge(
            second,
            FitnessNaturalRequest.parse("không dụng cụ")
        )
        assertEquals(0, cleared.equipment)
        assertEquals(emptySet<TrainingEquipment>(), cleared.additionalEquipment)
    }


    @Test fun colloquialLowEnergyRepliesAreUnderstoodOnlyWhenTheCoachAsksAboutEnergy() {
        val previous = FitnessNaturalRequest.Result(
            goal = 2,
            level = 1,
            minutes = 20,
            equipment = 0,
            impact = 0,
            reply = "profile",
            avoidWristLoad = false,
            avoidFloorPositions = false,
            avoidOverheadReach = false,
            avoidKneeling = false,
            additionalEquipment = emptySet()
        )
        assertEquals(FitnessCoachFollowUp.Question.ENERGY, FitnessCoachFollowUp.nextQuestionType(previous))

        listOf("hơi đuối", "đuối sức", "hết pin", "mỏi cơ", "cơ bắp mỏi").forEach { phrase ->
            val reply = FitnessCoachConversation.parseReply(previous, phrase)
            val merged = FitnessCoachConversation.merge(previous, reply)
            assertEquals("Failed for: $phrase", 0, merged.energy)
            assertEquals(2, merged.goal)
            assertEquals(20, merged.minutes)
            assertEquals(0, merged.equipment)
            assertTrue(reply.reply.contains("buổi tập nhẹ"))
        }
    }

    @Test fun negativeSorenessReplyDoesNotBecomeLowEnergyOrClearTheExistingProfile() {
        val previous = FitnessNaturalRequest.Result(
            goal = 2,
            level = 1,
            minutes = 20,
            equipment = 0,
            impact = 0,
            reply = "profile",
            avoidWristLoad = false,
            avoidFloorPositions = false,
            avoidOverheadReach = false,
            additionalEquipment = emptySet()
        )
        val reply = FitnessCoachConversation.parseReply(previous, "không mỏi")
        assertNull(reply.energy)
        assertEquals(2, FitnessCoachConversation.merge(previous, reply).goal)
    }



    @Test fun kneelingPreferenceSurvivesUnrelatedConversationTurnsAndCanBeRelaxed() {
        val first = FitnessCoachConversation.merge(
            null, FitnessNaturalRequest.parse("không quỳ gối")
        )
        assertEquals(true, first.avoidKneeling)

        val later = FitnessCoachConversation.merge(
            first, FitnessNaturalRequest.parse("giãn cơ 20 phút")
        )
        assertEquals(true, later.avoidKneeling)
        assertEquals(2, later.goal)
        assertEquals(20, later.minutes)

        val relaxed = FitnessCoachConversation.merge(
            later, FitnessNaturalRequest.parse("có thể quỳ gối")
        )
        assertEquals(false, relaxed.avoidKneeling)
        assertEquals(2, relaxed.goal)
        assertEquals(20, relaxed.minutes)
    }

}
