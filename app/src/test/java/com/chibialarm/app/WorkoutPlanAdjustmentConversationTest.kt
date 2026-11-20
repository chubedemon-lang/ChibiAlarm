package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutPlanAdjustmentConversationTest {
    @Test fun noEquipmentDoesNotRecommendTheWeightedProgram() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            "Tao không có dụng cụ, có bài nào thay thế?"
        )

        assertNotNull(answer)
        assertFalse(answer!!.contains("Sức mạnh với tạ hoặc dây"))
        assertTrue(answer.contains("giáo án và buổi đang tập chưa bị thay đổi"))
    }

    @Test fun noFloorPreferenceFiltersReviewedFloorExercises() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            "Tao không muốn nằm sàn, thay bài giúp tao"
        )

        assertNotNull(answer)
        assertFalse(answer!!.contains("Nâng hông trên sàn"))
        assertFalse(answer.contains("Bird-dog"))
        assertFalse(answer.contains("Plank khuỷu hoặc plank gối"))
    }

    @Test fun noWristSupportUsesTheReviewedForearmAlternative() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            "Không muốn chống tay, có bài nào thay thế?"
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Ép ngực tường bằng cẳng tay"))
        assertFalse(answer.contains("Chống đẩy tường"))
    }

    @Test fun missingDumbbellCanKeepASelectedResistanceBand() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Không có tạ, có bài thay thế không?",
            equipmentSelection = 2,
            programs = listOf(TrainingCatalog.byId("weighted"))
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Sức mạnh với tạ hoặc dây"))
        assertTrue(answer.contains("Kéo tạ đơn hoặc dây kháng lực"))
    }

    @Test fun gentleRequestPrioritizesBeginnerFriendlyPrograms() {
        val answer = WorkoutPlanAdjustmentConversation.answer("Hôm nay mệt, có bài nào dễ hơn?")

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bắt đầu nhẹ nhàng"))
        assertFalse(answer.contains("Thử thách"))
    }

    @Test fun spokenLowImpactRequestOnlyShowsReviewedLowImpactPrograms() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Không nhảy, đổi bài giúp tao",
            programs = listOf(
                TrainingCatalog.byId("advanced"),
                TrainingCatalog.byId("lowcardio")
            )
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Cardio không bật nhảy"))
        assertFalse(answer.contains("Thử thách nâng cao"))
        assertTrue(answer.contains("ít tác động, không nhảy"))
        assertTrue(answer.contains("buổi đang tập chưa bị thay đổi"))
    }

    @Test fun selectedLowImpactPreferenceSurvivesAChangeRequestThatDoesNotRepeatIt() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Đổi bài giúp tao",
            impact = 1,
            programs = listOf(
                TrainingCatalog.byId("advanced"),
                TrainingCatalog.byId("mobility")
            )
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Linh hoạt & giãn cơ"))
        assertFalse(answer.contains("Thử thách nâng cao"))
    }

    @Test fun lowImpactRequestFailsClosedWhenNoReviewedLowImpactProgramExists() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Không nhảy, đổi bài giúp tao",
            programs = listOf(TrainingCatalog.byId("advanced"))
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa tìm thấy chương trình đã duyệt ít tác động"))
        assertFalse(answer.contains("Phương án để xem trước"))
        assertFalse(answer.contains("Thử thách nâng cao"))
    }

    @Test fun selectedPainOrInjuryBlocksMidSessionSuggestionsEvenWhenNotRepeated() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Đổi bài giúp tao",
            impact = 2
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("không đề xuất đổi bài"))
        assertFalse(answer.contains("Phương án để xem trước"))
    }

    @Test fun normalPlanRequestIsNotIntercepted() {
        assertNull(
            WorkoutPlanAdjustmentConversation.answer(
                "Hôm nay tao muốn tập squat 20 phút không dụng cụ"
            )
        )
    }

    @Test fun remainingTimePrioritizesGoalAlignedProgramsAndShowsEstimates() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Tao chỉ còn 5 phút, ưu tiên cardio nhẹ",
            level = 1,
            goal = 1
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Mày còn 5 phút"))
        assertTrue(answer.contains("Cardio nhẹ 5–10 phút"))
        assertTrue(answer.contains("Ước tính:"))
        assertTrue(answer.contains("buổi đang tập chưa bị thay đổi"))
    }

    @Test fun spokenRemainingMinutesAndGoalAreUnderstoodWithoutChangingThePlan() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            "Tao chỉ còn năm phút, ưu tiên giãn cơ"
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Mày còn 5 phút"))
        assertTrue(answer.contains("Linh hoạt 5–10 phút"))
        assertTrue(answer.contains("bản xem trước"))
    }

    @Test fun aProgramLongerThanRemainingTimeIsShownWithAnExplicitWarning() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Tao chỉ còn 5 phút, có bài nào phù hợp?",
            programs = listOf(TrainingCatalog.byId("full"))
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("dài hơn thời gian còn lại"))
        assertTrue(answer.contains("không tự cắt hiệp bắt buộc"))
    }

    @Test fun levelChangesTheDisplayedTimeEstimate() {
        val program = TrainingCatalog.byId("full")
        val beginner = WorkoutPlanAdjustmentConversation.answer(
            input = "Tao còn 30 phút, có bài nào phù hợp?",
            programs = listOf(program),
            level = 0,
            goal = 0
        )
        val experienced = WorkoutPlanAdjustmentConversation.answer(
            input = "Tao còn 30 phút, có bài nào phù hợp?",
            programs = listOf(program),
            level = 2,
            goal = 0
        )

        assertNotNull(beginner)
        assertNotNull(experienced)
        assertTrue(beginner!!.contains("Ước tính: 19 phút 0 giây"))
        assertTrue(experienced!!.contains("Ước tính: 18 phút 0 giây"))
    }

    @Test fun painReportTakesPriorityOverRemainingTimeSuggestions() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            "Đau đầu gối, còn 5 phút, có bài nào nhẹ hơn?"
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Không bắt đầu tập khi đang có triệu chứng"))
        assertFalse(answer.contains("Phương án để xem trước"))
    }

    @Test fun negativeRemainingTimeIsNotTreatedAsAnAvailableBudget() {
        assertNull(
            WorkoutPlanAdjustmentConversation.answer(
                "Tao không còn 5 phút, có bài nào phù hợp?"
            )
        )
    }

    @Test fun noCompatibleMainMovementFailsClosed() {
        val floorMove = TrainingCatalog.allMoves.first { it.name == "Bird-dog" }
        val onlyFloorProgram = TrainingProgram(
            id = "floor-only",
            name = "Chỉ có bài dưới sàn",
            subtitle = "Test",
            category = "Test",
            moves = listOf(floorMove)
        )

        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Không muốn nằm sàn, đổi bài giúp tao",
            programs = listOf(onlyFloorProgram)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa tìm thấy chương trình còn động tác chính phù hợp"))
        assertFalse(answer.contains("Bird-dog"))
    }

    @Test fun effortHistoryFollowUpOffersOnlyCuratedAlternativeForTheNamedMove() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Tao muốn xem một buổi tập nhẹ hơn, không tự tăng số hiệp; ưu tiên thay thế cho động tác: Kéo tạ đơn hoặc dây kháng lực",
            equipmentSelection = 0
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Kéo tạ đơn hoặc dây kháng lực"))
        assertTrue(answer.contains("Siết bả vai đứng"))
        assertTrue(answer.contains("giáo án hoặc buổi đang tập"))
        assertFalse(answer.contains("Phương án để xem trước"))
        assertFalse(answer.contains("Chống đẩy tường"))
    }

    @Test fun targetedAlternativeRespectsMovementAndEquipmentFilters() {
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Ưu tiên thay thế cho động tác: Gập hông với tạ đơn hoặc dây",
            profile = WorkoutConstraintProfile(avoidFloorPositions = true),
            equipmentSelection = 0
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Gập hông không dụng cụ"))
        assertFalse(answer.contains("Nâng hông trên sàn"))
    }

    @Test fun targetedChairAlternativeRequiresTheUserToHaveAChair() {
        val request = "Ưu tiên thay thế cho động tác: Squat không tạ"
        val withoutChair = WorkoutPlanAdjustmentConversation.answer(
            input = request,
            equipmentSelection = 0
        )
        val withChair = WorkoutPlanAdjustmentConversation.answer(
            input = request,
            equipmentSelection = 3
        )

        assertNotNull(withoutChair)
        assertTrue(withoutChair!!.contains("chưa tìm thấy biến thể đã duyệt"))
        assertFalse(withoutChair.contains("Đứng lên – ngồi xuống"))
        assertNotNull(withChair)
        assertTrue(withChair!!.contains("Đứng lên – ngồi xuống"))
    }

    @Test fun ambiguousOrUnknownTargetDoesNotFallBackToAGenericProgramList() {
        val ambiguous = WorkoutPlanAdjustmentConversation.answer(
            "Ưu tiên thay thế cho động tác: Squat"
        )
        val unknown = WorkoutPlanAdjustmentConversation.answer(
            "Ưu tiên thay thế cho động tác: Động tác không có trong thư viện"
        )

        assertNotNull(ambiguous)
        assertTrue(ambiguous!!.contains("chưa đủ cụ thể"))
        assertTrue(ambiguous.contains("Squat không tạ"))
        assertTrue(ambiguous.contains("Squat chậm 3 giây"))
        assertNotNull(unknown)
        assertTrue(unknown!!.contains("chưa tìm thấy động tác"))
        assertFalse(ambiguous.contains("Phương án để xem trước"))
        assertFalse(unknown.contains("Phương án để xem trước"))
    }



    @Test fun spokenKneelingPreferenceFiltersKneelingMovesWithoutBanningSupineWork() {
        val program = TrainingProgram(
            "kneeling-preview", "Kneeling preview", "test", "test",
            listOf(
                TrainingCatalog.allMoves.first { it.name == "Bird-dog" },
                TrainingCatalog.allMoves.first { it.name == "Cat-cow nhẹ" },
                TrainingCatalog.allMoves.first { it.name == "Nâng hông trên sàn" }
            )
        )
        val answer = WorkoutPlanAdjustmentConversation.answer(
            input = "Không quỳ gối, đổi bài giúp tao",
            programs = listOf(program)
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Mở vai và ngực"))
        assertTrue(answer.contains("Dead bug cơ bụng"))
        assertTrue(answer.contains("Nâng hông trên sàn"))
        assertTrue(answer.contains("đã lọc động tác phải quỳ gối"))
        assertTrue(answer.contains("buổi đang tập chưa bị thay đổi"))
        assertFalse(answer.contains("Bird-dog"))
        assertFalse(answer.contains("Cat-cow nhẹ"))
    }



    @Test fun standaloneKneelingPreferenceFallsThroughToCoachProfileParser() {
        assertNull(
            WorkoutPlanAdjustmentConversation.answer(
                input = "không quỳ gối",
                programs = listOf(TrainingCatalog.byId("full"))
            )
        )
        assertNull(
            WorkoutPlanAdjustmentConversation.answer(
                input = "có thể quỳ gối",
                programs = listOf(TrainingCatalog.byId("full"))
            )
        )
    }

}
