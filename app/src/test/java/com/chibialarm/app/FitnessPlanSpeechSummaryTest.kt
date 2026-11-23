package com.chibialarm.app

import org.junit.Assert.assertEquals
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
        assertTrue(spoken.contains("tránh sàn"))
        assertTrue(spoken.contains("tránh với cao"))
        assertTrue(spoken.contains("tránh bật nhảy"))
        assertTrue(spoken.contains("Gợi ý offline"))
        assertTrue(spoken.length <= FitnessPlanSpeechSummary.MAX_CHARACTERS)
    }

    @Test fun lowImpactPreferenceIsNotTruncatedInSpokenSummary() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(
            profile(impact = 1),
            "Cardio nhẹ không bật nhảy"
        )

        assertTrue(spoken.contains("Ưu tiên ít tác động, không nhảy."))
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

        assertTrue(spoken.contains("Bạn đã báo đau hoặc chấn thương"))
        assertTrue(spoken.contains("không tạo hoặc bắt đầu giáo án"))
        assertTrue(spoken.contains("không phải tư vấn y khoa"))
        assertFalse(spoken.contains("Chibi chọn"))
    }

    @Test fun spokenSummaryIncludesEstimatedDurationAndAggregateRestTime() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(
            profile(),
            "Toàn thân",
            estimatedMinutes = 22,
            prescribedRestSeconds = 90
        )

        assertTrue(spoken.contains("Ước tính 22 phút"))
        assertTrue(spoken.contains("Tổng thời gian nghỉ theo giáo án: 1 phút 30 giây"))
        assertTrue(spoken.length <= FitnessPlanSpeechSummary.MAX_CHARACTERS)
    }

    @Test fun overTargetPlanIsSpokenAsAnOverrun() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(
            profile(),
            "Toàn thân",
            estimatedMinutes = 32,
            exceedsTarget = true,
            prescribedRestSeconds = 150
        )

        assertTrue(spoken.contains("Ước tính 32 phút, vượt mục tiêu"))
        assertTrue(spoken.contains("Tổng thời gian nghỉ theo giáo án: 2 phút 30 giây"))
        assertTrue(spoken.length <= FitnessPlanSpeechSummary.MAX_CHARACTERS)
    }

    @Test fun spokenSummaryCanExplainTheSelectedPlanMuscleFocus() {
        val spoken = FitnessPlanSpeechSummary.fromProfile(
            profile(),
            "Toàn thân",
            muscleFocusSummary = "Nhóm cơ chính: Đùi trước, Mông."
        )

        assertTrue(spoken.contains("Nhóm cơ chính: Đùi trước, Mông."))
        assertTrue(spoken.length <= FitnessPlanSpeechSummary.MAX_CHARACTERS)
    }

    @Test fun spokenSummaryAddsAConservativeReminderForLatestVeryHardCheckIn() {
        val base = FitnessPlanSpeechSummary.fromProfile(profile(), "Toàn thân")
        val spoken = FitnessPlanSpeechSummary.withEffortReview(
            base,
            listOf("Check-in gần nhất: bạn tự đánh dấu “Quá sức, tạm dừng”; private knee note")
        )

        assertTrue(spoken.contains("đánh dấu một động tác trong kế hoạch là quá sức"))
        assertTrue(spoken.contains("biến thể nhẹ hơn"))
        assertFalse(spoken.contains("private knee note"))
        assertTrue(spoken.length <= FitnessPlanSpeechSummary.MAX_WITH_EFFORT_REVIEW_CHARACTERS)
    }

    @Test fun spokenSummaryExplainsRepeatedHardCheckInsWithoutReadingJournalText() {
        val base = FitnessPlanSpeechSummary.fromProfile(profile(), "Toàn thân")
        val spoken = FitnessPlanSpeechSummary.withEffortReview(
            base,
            listOf("đã tự đánh dấu Squat là khó/quá sức ở 2 buổi khác nhau")
        )

        assertTrue(spoken.contains("khó hoặc quá sức ở nhiều buổi"))
        assertTrue(spoken.contains("thời gian nghỉ"))
        assertFalse(spoken.contains("Squat"))
    }

    @Test fun noEffortWarningsLeaveTheExistingSpokenSummaryUnchanged() {
        val base = FitnessPlanSpeechSummary.fromProfile(profile(), "Toàn thân")

        assertEquals(base, FitnessPlanSpeechSummary.withEffortReview(base, emptyList()))
    }

    @Test fun painStopSummaryNeverAddsAPlanEffortOrStartPrompt() {
        val base = FitnessPlanSpeechSummary.fromProfile(
            profile(impact = 2, constraints = WorkoutConstraintProfile()),
            "Giáo án"
        )
        val spoken = FitnessPlanSpeechSummary.withEffortReview(
            base,
            listOf("Quá sức, tạm dừng")
        )

        assertEquals(base, spoken)
        assertTrue(spoken.contains("không tạo hoặc bắt đầu giáo án"))
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
        assertTrue(spoken.contains("tránh sàn"))
        assertTrue(spoken.contains("tránh với cao"))
    }
}
