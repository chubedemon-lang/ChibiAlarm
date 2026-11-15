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
            equipmentSelection = 2
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Sức mạnh với tạ hoặc dây"))
        assertTrue(answer.contains("dây kháng lực"))
    }

    @Test fun gentleRequestPrioritizesBeginnerFriendlyPrograms() {
        val answer = WorkoutPlanAdjustmentConversation.answer("Hôm nay mệt, có bài nào dễ hơn?")

        assertNotNull(answer)
        assertTrue(answer!!.indexOf("Bắt đầu nhẹ nhàng") < answer.indexOf("Thử thách"))
    }

    @Test fun normalPlanRequestIsNotIntercepted() {
        assertNull(
            WorkoutPlanAdjustmentConversation.answer(
                "Hôm nay tao muốn tập squat 20 phút không dụng cụ"
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
