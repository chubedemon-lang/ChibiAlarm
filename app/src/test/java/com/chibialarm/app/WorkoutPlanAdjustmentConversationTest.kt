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
}
