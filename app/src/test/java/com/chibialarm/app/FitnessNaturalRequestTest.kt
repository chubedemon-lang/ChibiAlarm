package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class FitnessNaturalRequestTest {
    @Test fun parsesVietnamesePreferencesWithoutNetwork() {
        val result = FitnessNaturalRequest.parse("Hôm nay hơi mệt, muốn giãn cơ 15 phút, không dụng cụ")
        assertEquals(2, result.goal)
        assertEquals(0, result.energy)
        assertEquals(15, result.minutes)
        assertEquals(0, result.equipment)
        assertNull(result.impact)
        assertNull(result.level)
    }

    @Test fun parsesTrainingExperienceLevels() {
        assertEquals(0, FitnessNaturalRequest.parse("Mới bắt đầu, muốn tập nhẹ").level)
        assertEquals(1, FitnessNaturalRequest.parse("Tôi đang tập đều mỗi tuần").level)
        assertEquals(2, FitnessNaturalRequest.parse("Tôi có kinh nghiệm, muốn nâng cao").level)
        assertEquals(2, FitnessNaturalRequest.parse("advanced").level)
    }

    @Test fun beginnerPhrasesAreNotMistakenForPain() {
        for (phrase in listOf(
            "Mới bắt đầu, muốn tập nhẹ",
            "Bắt đầu tập 15 phút",
            "Mới bắt đầu, không dụng cụ"
        )) {
            val result = FitnessNaturalRequest.parse(phrase)
            assertEquals("Failed for $phrase", 0, result.level)
            assertNull("Failed for $phrase", result.impact)
        }
    }

    @Test fun injuryTakesPriorityOverExerciseGoals() {
        val result = FitnessNaturalRequest.parse("Tôi bị đau khớp nhưng muốn tập sức mạnh 30 phút")
        assertEquals(2, result.impact)
        assertTrue(result.reply.contains("Không bắt đầu tập"))
    }

    @Test fun unknownInputDoesNotOverrideSelectedPreferences() {
        val result = FitnessNaturalRequest.parse("Xin chào Chibi")
        assertNull(result.goal)
        assertNull(result.minutes)
        assertNull(result.equipment)
        assertNull(result.impact)
    }

    @Test fun ignoresOutOfRangeDuration() {
        assertNull(FitnessNaturalRequest.parse("tập 999 phút").minutes)
        assertEquals(10, FitnessNaturalRequest.parse("tap 10 phut").minutes)
    }

    @Test fun handlesLowImpactWithoutInjury() {
        assertEquals(1, FitnessNaturalRequest.parse("Không nhảy, tập nhẹ nhàng").impact)
    }

    @Test fun understandsWeightLossAndEnduranceGoals() {
        assertEquals(1, FitnessNaturalRequest.parse("Tao muốn giảm cân 20 phút").goal)
        assertEquals(1, FitnessNaturalRequest.parse("Tập tăng sức bền").goal)
        assertEquals(1, FitnessNaturalRequest.parse("Giảm mỡ không dụng cụ").goal)
    }

    @Test fun weightLossRequestStillHonorsInjurySafety() {
        val result = FitnessNaturalRequest.parse("Muốn giảm cân nhưng đang đau khớp")
        assertEquals(1, result.goal)
        assertEquals(2, result.impact)
    }

    @Test fun safetySymptomsOverrideRequestedWorkout() {
        for (symptom in listOf("đau gối", "đau lưng", "buồn nôn", "muốn ngất", "tê tay", "tê chân")) {
            val result = FitnessNaturalRequest.parse("Tôi $symptom nhưng muốn tập sức mạnh 20 phút")
            assertEquals("Failed for $symptom", 2, result.impact)
            assertTrue(result.reply.contains("Không bắt đầu tập"))
        }
    }

    @Test fun ordinaryStartRequestDoesNotTriggerPainWarning() {
        assertNull(FitnessNaturalRequest.parse("Bắt đầu tập 15 phút").impact)
    }

    @Test fun recognizesKneePainWhenTheBodyPartComesFirst() {
        for (phrase in listOf("đầu gối đau khi squat", "đầu gối đang nhức", "khớp gối bị đau")) {
            assertEquals("Failed for $phrase", 2, FitnessNaturalRequest.parse(phrase).impact)
        }
    }

    @Test fun recognizesPainWithVietnameseLocationConnectors() {
        for (phrase in listOf("đau ở đầu gối", "đau đầu gối", "đau ở lưng dưới", "cổ tay bị nhức")) {
            assertEquals("Failed for $phrase", 2, FitnessNaturalRequest.parse(phrase).impact)
        }
    }

    @Test fun aBodyPartNameAloneIsNotMistakenForPain() {
        assertNull(FitnessNaturalRequest.parse("hôm nay muốn tập nhẹ cho đầu gối").impact)
    }

    @Test fun respectsExplicitlyNegatedPainButStillFindsAnotherPainReport() {
        assertNull(FitnessNaturalRequest.parse("không đau đầu gối").impact)
        assertNull(FitnessNaturalRequest.parse("đầu gối không đau").impact)
        assertNull(FitnessNaturalRequest.parse("không bị đau ở đầu gối").impact)
        assertNull(FitnessNaturalRequest.parse("đầu gối không bị đau").impact)
        assertNull(FitnessNaturalRequest.parse("không bị đau").impact)
        assertEquals(2, FitnessNaturalRequest.parse("không đau đầu gối nhưng đau lưng").impact)
    }

    @Test fun parsesCommonSpokenVietnameseDurations() {
        assertEquals(15, FitnessNaturalRequest.parse("mười lăm phút").minutes)
        assertEquals(25, FitnessNaturalRequest.parse("hai mươi lăm phút").minutes)
        assertEquals(60, FitnessNaturalRequest.parse("một tiếng").minutes)
        assertEquals(30, FitnessNaturalRequest.parse("nửa tiếng").minutes)
        assertEquals(15, FitnessNaturalRequest.parse("15p").minutes)
    }

    @Test fun spokenDurationParserStillRejectsOutOfRangeAndAmbiguousValues() {
        assertNull(FitnessNaturalRequest.parse("một trăm ba mươi phút").minutes)
        assertNull(FitnessNaturalRequest.parse("mười lăm phút rưỡi").minutes)
    }

    @Test fun localizedDiscomfortBlocksOnlyWhenABodyRegionIsNamed() {
        for (phrase in listOf(
            "cổ tay hơi khó chịu khi chống đẩy",
            "khó chịu ở cổ tay",
            "cổ tay đang hơi khó chịu",
            "đầu gối bị khó chịu"
        )) {
            assertEquals("Failed for $phrase", 2, FitnessNaturalRequest.parse(phrase).impact)
        }
    }

    @Test fun negatedLocalizedDiscomfortDoesNotTriggerTheSafetyGate() {
        for (phrase in listOf(
            "không khó chịu ở cổ tay",
            "cổ tay không còn khó chịu",
            "cổ tay không bị khó chịu"
        )) {
            assertNull("Failed for $phrase", FitnessNaturalRequest.parse(phrase).impact)
        }
        assertNull(FitnessNaturalRequest.parse("hôm nay hơi khó chịu với lịch tập").impact)
    }

    @Test fun explicitWristLoadAvoidanceIsAPlanPreferenceNotAnInjuryReport() {
        for (phrase in listOf(
            "không chống tay",
            "tránh chống tay khi tập",
            "không tì cổ tay",
            "không chịu tải qua cổ tay",
            "hạn chế cổ tay"
        )) {
            val result = FitnessNaturalRequest.parse(phrase)
            assertEquals("Failed for $phrase", true, result.avoidWristLoad)
            assertNull("Failed for $phrase", result.impact)
        }
    }

    @Test fun activeWristPainStillUsesTheSafetyStopEvenWithAnAvoidancePreference() {
        val result = FitnessNaturalRequest.parse("không chống tay nhưng cổ tay đang đau")
        assertEquals(true, result.avoidWristLoad)
        assertEquals(2, result.impact)
        assertTrue(result.reply.contains("Không bắt đầu tập"))
    }



    @Test fun overheadReachAvoidanceIsAPlanPreferenceNotAShoulderPainDiagnosis() {
        for (phrase in listOf(
            "không đưa tay qua đầu",
            "tránh nâng tay quá đầu",
            "đừng đẩy tạ qua đầu",
            "hạn chế động tác quá đầu"
        )) {
            val result = FitnessNaturalRequest.parse(phrase)
            assertEquals("Failed for $phrase", true, result.avoidOverheadReach)
            assertNull("Preference is not a diagnosis: $phrase", result.impact)
        }
    }

    @Test fun explicitOverheadPermissionCanRelaxOnlyThatPlanPreference() {
        for (phrase in listOf(
            "có thể đưa tay qua đầu",
            "không cần tránh đưa tay qua đầu",
            "bỏ hạn chế đưa tay qua đầu"
        )) {
            val result = FitnessNaturalRequest.parse(phrase)
            assertEquals("Failed for $phrase", false, result.avoidOverheadReach)
            assertNull(result.impact)
        }
    }

    @Test fun explicitOverheadRestrictionWinsOverPermissionInTheSameUtterance() {
        val result = FitnessNaturalRequest.parse(
            "có thể đưa tay qua đầu nhưng vẫn không đưa tay qua đầu"
        )
        assertEquals(true, result.avoidOverheadReach)
    }

    @Test fun overheadPermissionNeverHidesActiveShoulderPain() {
        val result = FitnessNaturalRequest.parse(
            "có thể đưa tay qua đầu nhưng vai đang đau"
        )
        assertEquals(false, result.avoidOverheadReach)
        assertEquals(2, result.impact)
        assertTrue(result.reply.contains("Không bắt đầu tập"))
    }

    @Test fun floorPositionAvoidanceIsAPlanPreferenceNotAnInjuryReport() {
        for (phrase in listOf("không nằm sàn", "đừng quỳ sàn", "tránh xuống sàn", "đừng nằm hoặc quỳ sàn")) {
            val result = FitnessNaturalRequest.parse(phrase)
            assertEquals("Failed for $phrase", true, result.avoidFloorPositions)
            assertNull("Failed for $phrase", result.impact)
        }
    }

    @Test fun floorPreferenceCanCoexistWithWristPreference() {
        val result = FitnessNaturalRequest.parse("đừng chống tay và không nằm sàn")
        assertEquals(true, result.avoidWristLoad)
        assertEquals(true, result.avoidFloorPositions)
        assertNull(result.impact)
    }

    @Test fun equipmentNegationDoesNotBecomePositiveEquipment() {
        for (phrase in listOf(
            "không có tạ",
            "không còn tạ nữa",
            "bỏ tạ đi",
            "không có dây kháng lực",
            "không có dụng cụ",
            "tập tay không"
        )) {
            assertEquals("Failed for $phrase", 0, FitnessNaturalRequest.parse(phrase).equipment)
        }
    }

    @Test fun anAvailableEquipmentAlternativeWinsOverAnotherMissingOption() {
        assertEquals(2, FitnessNaturalRequest.parse("không có tạ, chỉ có dây kháng lực").equipment)
        assertEquals(2, FitnessNaturalRequest.parse("không có dây, có tạ đơn").equipment)
    }

    @Test fun matOnlyRemainsDistinctFromNoEquipment() {
        assertEquals(1, FitnessNaturalRequest.parse("có thảm, không có tạ").equipment)
        assertEquals(1, FitnessNaturalRequest.parse("không có dụng cụ khác, chỉ có thảm").equipment)
    }

    @Test fun explicitConsentCanClearMovementPreferencesWithoutDiagnosingPain() {
        val wrist = FitnessNaturalRequest.parse("có thể chống tay, không cần tránh cổ tay")
        assertEquals(false, wrist.avoidWristLoad)
        assertNull(wrist.impact)

        val floor = FitnessNaturalRequest.parse("có thể nằm sàn và quỳ sàn")
        assertEquals(false, floor.avoidFloorPositions)
        assertNull(floor.impact)

        assertEquals(false, FitnessNaturalRequest.parse("bỏ hạn chế cổ tay").avoidWristLoad)
        assertEquals(false, FitnessNaturalRequest.parse("bỏ hạn chế nằm sàn").avoidFloorPositions)
    }

    @Test fun clearingMovementPreferenceDoesNotHideActivePain() {
        val result = FitnessNaturalRequest.parse("bỏ hạn chế cổ tay nhưng cổ tay đang đau")
        assertEquals(false, result.avoidWristLoad)
        assertEquals(2, result.impact)
        assertTrue(result.reply.contains("Không bắt đầu tập"))
    }


    @Test fun wristOptOutIsNotShadowedByTheRestrictionPhraseItContains() {
        assertEquals(false, FitnessNaturalRequest.parse("bỏ hạn chế cổ tay").avoidWristLoad)
        assertEquals(false, FitnessNaturalRequest.parse("không cần tránh cổ tay").avoidWristLoad)
        assertEquals(
            true,
            FitnessNaturalRequest.parse("có thể chống tay nhưng vẫn hạn chế cổ tay").avoidWristLoad
        )
    }

    @Test fun floorOptOutIsNotShadowedByTheAvoidancePhraseItContains() {
        assertEquals(false, FitnessNaturalRequest.parse("không cần tránh nằm sàn").avoidFloorPositions)
        assertEquals(
            true,
            FitnessNaturalRequest.parse("có thể nằm sàn nhưng không quỳ sàn").avoidFloorPositions
        )
    }

}
