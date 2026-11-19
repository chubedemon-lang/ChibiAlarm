package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessPlanProfileSummaryTest {
    @Test fun summaryShowsEveryPersonalizationDimensionBeforeStarting() {
        val summary = FitnessPlanProfileSummary.format(
            goalLabel = "Tăng sức mạnh",
            energyLabel = "Cảm thấy bình thường",
            levelLabel = "Đang tập đều",
            minutes = 25,
            equipmentSelection = 2,
            additionalEquipment = setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.MAT),
            impact = 0,
            constraints = WorkoutConstraintProfile()
        )

        assertTrue(summary.contains("Mục tiêu: Tăng sức mạnh"))
        assertTrue(summary.contains("Trình độ: Đang tập đều"))
        assertTrue(summary.contains("Năng lượng tự đánh giá: Cảm thấy bình thường"))
        assertTrue(summary.contains("Thời lượng mục tiêu: 25 phút"))
        assertTrue(summary.contains("Tạ đơn hoặc dây kháng lực"))
        assertTrue(summary.contains("Thảm tập"))
        assertTrue(summary.contains("không phải xác nhận y tế"))
    }

    @Test fun duplicatePrimaryEquipmentIsNotListedAsAnExtraTool() {
        val summary = FitnessPlanProfileSummary.format(
            goalLabel = "Cardio",
            energyLabel = "Bình thường",
            levelLabel = "Mới bắt đầu",
            minutes = 15,
            equipmentSelection = 2,
            additionalEquipment = setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND),
            impact = 1,
            constraints = WorkoutConstraintProfile()
        )

        assertTrue(summary.contains("Ưu tiên vận động: Ưu tiên bài ít tác động"))
        assertFalse(summary.contains("bổ sung: Tạ đơn"))
        assertFalse(summary.contains("bổ sung: Dây kháng lực"))
    }

    @Test fun profileSummaryNamesTheExactResistanceTool() {
        val dumbbell = FitnessPlanProfileSummary.format(
            goalLabel = "Tăng sức mạnh",
            energyLabel = "Bình thường",
            levelLabel = "Đang tập đều",
            minutes = 25,
            equipmentSelection = 4,
            additionalEquipment = emptySet(),
            impact = 0,
            constraints = WorkoutConstraintProfile()
        )
        val band = FitnessPlanProfileSummary.format(
            goalLabel = "Tăng sức mạnh",
            energyLabel = "Bình thường",
            levelLabel = "Đang tập đều",
            minutes = 25,
            equipmentSelection = 5,
            additionalEquipment = emptySet(),
            impact = 0,
            constraints = WorkoutConstraintProfile()
        )

        assertTrue(dumbbell.contains("Dụng cụ: Tạ đơn"))
        assertFalse(dumbbell.contains("Dây kháng lực"))
        assertTrue(band.contains("Dụng cụ: Dây kháng lực"))
        assertFalse(band.contains("Tạ đơn"))
    }

    @Test fun allMovementFiltersAreVisibleAndExplicitlySessionOnly() {
        val summary = FitnessPlanProfileSummary.format(
            goalLabel = "Linh hoạt",
            energyLabel = "Hơi mệt",
            levelLabel = "Mới bắt đầu",
            minutes = 10,
            equipmentSelection = 0,
            additionalEquipment = emptySet(),
            impact = 1,
            constraints = WorkoutConstraintProfile(
                avoidWristLoad = true,
                avoidFloorPositions = true,
                avoidOverheadReach = true,
                avoidJumping = true
            )
        )

        assertTrue(summary.contains("tránh chống tay/tải qua cổ tay"))
        assertTrue(summary.contains("tránh tư thế nằm hoặc quỳ sàn"))
        assertTrue(summary.contains("tránh đưa tay hoặc tải lên quá đầu"))
        assertTrue(summary.contains("tránh bật nhảy"))
        assertTrue(summary.contains("chỉ áp dụng cho buổi hiện tại"))
        assertTrue(summary.contains("không lưu thành hồ sơ sức khỏe"))
    }

    @Test fun painReportIsNeverPresentedAsAStartableRecommendation() {
        val summary = FitnessPlanProfileSummary.format(
            goalLabel = "Tăng sức mạnh",
            energyLabel = "Bình thường",
            levelLabel = "Có kinh nghiệm",
            minutes = 30,
            equipmentSelection = 0,
            additionalEquipment = emptySet(),
            impact = 2,
            constraints = WorkoutConstraintProfile()
        )

        assertTrue(summary.contains("Đã báo đau hoặc chấn thương — không tạo giáo án"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDurationIsRejected() {
        FitnessPlanProfileSummary.format(
            goalLabel = "Cardio",
            energyLabel = "Bình thường",
            levelLabel = "Mới bắt đầu",
            minutes = 0,
            equipmentSelection = 0,
            additionalEquipment = emptySet(),
            impact = 0,
            constraints = WorkoutConstraintProfile()
        )
    }
}
