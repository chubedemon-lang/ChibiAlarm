package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessPlanSpeechSummaryTest {
    private fun profile(
        impact: Int = 1,
        constraints: WorkoutConstraintProfile = WorkoutConstraintProfile(
            avoidWristLoad = true,
            avoidFloorPositions = true,
            avoidOverheadReach = true,
            avoidJumping = true
        ),
        goal: String = "Tăng sức mạnh",
        level: String = "Đang tập đều",
        equipment: Int = 2
    ) = FitnessPlanProfileSummary.format(
        goalLabel = goal,
        energyLabel = "Cảm thấy bình thường",
        levelLabel = level,
        minutes = 25,
        equipmentSelection = equipment,
        additionalEquipment = setOf(TrainingEquipment.MAT),
        impact = impact,
        constraints = constraints
    )

    @Test fun spokenSummaryIncludesEveryPersonalizationDimensionAndMovementFilter() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(profile(), "Toàn thân không dụng cụ")

        assertTrue(spoken.contains("Mục tiêu Tăng sức mạnh"))
        assertTrue(spoken.contains("Trình độ Đang tập đều"))
        assertTrue(spoken.contains("25 phút"))
        assertTrue(spoken.contains("Dụng cụ tạ hoặc dây"))
        assertTrue(spoken.contains("ít tác động, không nhảy"))
        assertTrue(spoken.contains("tránh tải cổ tay"))
        assertTrue(spoken.contains("tránh tư thế sàn"))
        assertTrue(spoken.contains("tránh với quá đầu"))
        assertTrue(spoken.contains("tránh bật nhảy"))
        assertTrue(spoken.contains("Gợi ý offline"))
        assertTrue(spoken.length <= FitnessPlanSpeechSummary.MAX_CHARACTERS)
    }

    @Test fun noRestrictionIsSpokenAsNoExtraMovementFilter() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(
            profile(impact = 0, constraints = WorkoutConstraintProfile(), equipment = 0),
            "Bắt đầu nhẹ nhàng"
        )

        assertTrue(spoken.contains("không có bộ lọc bổ sung"))
        assertFalse(spoken.contains("tránh tải cổ tay"))
    }

    @Test fun reportedPainIsNeverRephrasedAsPermissionToStart() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(
            profile(impact = 2, constraints = WorkoutConstraintProfile()),
            "Giáo án"
        )

        assertTrue(spoken.contains("đã báo đau/chấn thương; không tạo giáo án"))
        assertTrue(spoken.contains("không phải tư vấn y khoa"))
    }

    @Test fun longUiLabelsRemainBoundedAndKeepAllSafetyFilters() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(
            profile(
                goal = "Mục tiêu luyện tập với tên dài nhưng vẫn hợp lệ và có nhiều từ mô tả",
                level = "Trình độ có nhiều từ mô tả nhưng vẫn hợp lệ",
                impact = 1
            ),
            "Tên chương trình luyện tập có nhiều từ mô tả nhưng vẫn hợp lệ"
        )

        assertTrue(spoken.length <= FitnessPlanSpeechSummary.MAX_CHARACTERS)
        assertTrue(spoken.contains("tránh tải cổ tay"))
        assertTrue(spoken.contains("tránh tư thế sàn"))
        assertTrue(spoken.contains("tránh với quá đầu"))
    }
}
