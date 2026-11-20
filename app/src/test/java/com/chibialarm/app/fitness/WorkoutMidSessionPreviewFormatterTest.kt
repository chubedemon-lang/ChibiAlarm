package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.TrainingMove
import com.chibialarm.app.TrainingEquipment
import com.chibialarm.app.WorkoutConstraintProfile
import com.chibialarm.app.WorkoutExtensionPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMidSessionPreviewFormatterTest {
    private fun move(name: String): TrainingMove =
        TrainingCatalog.allMoves.first { it.name == name }

    private fun preview(
        moves: List<TrainingMove>,
        currentIndex: Int,
        request: WorkoutMidSessionRequest.Request,
        availableEquipment: Set<TrainingEquipment> = emptySet(),
        completedCurrentSets: Int = 0
    ): WorkoutMidSessionPlanner.Preview {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = currentIndex,
            completedCurrentSets = completedCurrentSets,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 75,
            extensionMoveNames = emptySet(),
            level = 0,
            extensionPreferences = WorkoutExtensionPreferences(
                additionalEquipment = availableEquipment
            )
        )
        check(result is WorkoutMidSessionPlanner.Result.Proposed)
        return result.preview
    }

    @Test fun previewShowsTheExactCurrentAndUpcomingCatalogMoves() {
        val result = preview(
            listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Chống đẩy tường"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            request = WorkoutMidSessionRequest.Request(preferEasier = true),
            availableEquipment = setOf(TrainingEquipment.STABLE_CHAIR)
        )

        val text = WorkoutMidSessionPreviewFormatter.format(result)
        assertTrue(text.contains("1. Bước trước — Khởi động tại chỗ"))
        assertTrue(text.contains("2. Đang tập — Đứng lên – ngồi xuống"))
        assertTrue(text.contains("2 hiệp × 6–10 lần"))
        assertTrue(text.contains("3. Tiếp theo — Ép ngực tường bằng cẳng tay"))
        assertTrue(text.contains("Thay đổi đề xuất:"))
        assertTrue(text.contains("Squat không tạ → Đứng lên – ngồi xuống"))
        assertTrue(text.contains("Chống đẩy tường → Ép ngực tường bằng cẳng tay"))
        assertTrue(text.contains("Thời gian còn lại ước tính:"))
        assertFalse(text.contains("Đã hoàn thành — Khởi động"))
    }

    @Test fun previewAlwaysShowsAnEstimateAndExplainsAnUnmetTimeTarget() {
        val moves = listOf(move("Squat không tạ"), move("Thả lỏng"))
        val result = preview(
            moves,
            currentIndex = 0,
            request = WorkoutMidSessionRequest.Request(remainingMinutes = 5, restSeconds = 30)
        )
        val forced = result.copy(
            estimatedRemainingSeconds = 390,
            targetExceeded = true
        )
        val text = WorkoutMidSessionPreviewFormatter.format(forced)
        assertTrue(text.contains("6 phút 30 giây"))
        assertTrue(text.contains("Mục tiêu thời gian của bạn: 5 phút."))
        assertTrue(text.contains("vẫn dài hơn mục tiêu"))
        assertTrue(text.contains("nghỉ riêng 30 giây"))
    }

    @Test fun longPreviewKeepsTheCurrentMoveVisibleAndBoundsTheRemainingList() {
        val base = move("Squat không tạ")
        val moves = (1..15).map { index ->
            base.copy(name = "Động tác $index", dose = "2 hiệp × 8 lần")
        }
        val result = WorkoutMidSessionPlanner.Preview(
            before = moves,
            after = moves,
            currentIndex = 13,
            currentMoveChanged = false,
            replacements = emptyList(),
            removedMoves = emptyList(),
            estimatedRemainingSeconds = 600,
            requestedMinutes = null,
            targetExceeded = false,
            restSeconds = 0,
            extensionMoveNames = emptySet()
        )
        val text = WorkoutMidSessionPreviewFormatter.format(result)
        assertTrue(text.contains("… 13 bước trước trong phiên được giữ nguyên"))
        assertTrue(text.contains("14. Đang tập — Động tác 14"))
    }

    @Test fun previewExplainsThatAConstraintBasedReplacementMayNotBeEquivalent() {
        val result = preview(
            listOf(
                move("Khởi động tại chỗ"),
                move("Đẩy vai với tạ đơn hoặc dây"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            request = WorkoutMidSessionRequest.Request(avoidOverheadReach = true)
        )

        val text = WorkoutMidSessionPreviewFormatter.format(result)
        assertTrue(text.contains("Đẩy vai với tạ đơn hoặc dây → Mở vai và ngực"))
        assertTrue(text.contains("bài thay thế có thể khác nhóm cơ hoặc cường độ"))
        assertTrue(text.contains("xác nhận trước khi áp dụng"))
    }
    @Test fun previewExplainsWhyACuratedReplacementWasChosen() {
        val result = preview(
            listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            request = WorkoutMidSessionRequest.Request(preferEasier = true),
            availableEquipment = setOf(TrainingEquipment.STABLE_CHAIR)
        )

        val text = WorkoutMidSessionPreviewFormatter.format(result)
        assertTrue(text.contains("Squat không tạ → Đứng lên – ngồi xuống"))
        assertTrue(text.contains("Lý do: Có ghế vững làm mốc"))
        assertTrue(text.contains("chỉ dùng ghế không bánh xe"))
    }

    @Test fun previewNamesAnAddedOptionalMoveBeforeTheCooldown() {
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Squat không tạ"),
            move("Chống đẩy tường"),
            move("Thả lỏng")
        )
        val parsed = WorkoutMidSessionRequest.parse("Có ghế vững chắc, thêm một bài")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 1,
            extensionPreferences = WorkoutExtensionPreferences(goal = 1)
        )
        check(result is WorkoutMidSessionPlanner.Result.Proposed)
        val text = WorkoutMidSessionPreviewFormatter.format(result.preview)
        assertTrue(text.contains("Bổ sung động tác tùy chọn theo yêu cầu"))
        assertTrue(text.contains("Thêm: Nâng gối ngồi ghế"))
        assertTrue(text.contains("Động tác bổ sung không thay thế hiệp bắt buộc"))
        assertTrue(text.contains("Thời gian còn lại ước tính"))
    }
    @Test fun currentMovePreviewShowsConfirmedAndRemainingRequiredAndOptionalSets() {
        val result = preview(
            listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            request = WorkoutMidSessionRequest.Request(restSeconds = 30),
            completedCurrentSets = 1
        )

        val text = WorkoutMidSessionPreviewFormatter.format(result)
        assertTrue(text.contains("2–3 hiệp × 8–12 lần"))
        assertTrue(text.contains("đã tự xác nhận 1/2 hiệp bắt buộc"))
        assertTrue(text.contains("còn 1 hiệp bắt buộc"))
        assertTrue(text.contains("còn tối đa 1 hiệp tùy chọn"))
    }

    @Test fun meetingTheRequiredMinimumDoesNotShowRequiredSetsAsRemaining() {
        val result = preview(
            listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            request = WorkoutMidSessionRequest.Request(restSeconds = 30),
            completedCurrentSets = 2
        )

        val text = WorkoutMidSessionPreviewFormatter.format(result)
        assertTrue(text.contains("đã tự xác nhận 2/3 hiệp; đã đủ 2 hiệp bắt buộc"))
        assertTrue(text.contains("còn tối đa 1 hiệp tùy chọn"))
        assertFalse(text.contains("còn 0 hiệp bắt buộc"))
    }

    @Test fun spokenPreviewSummarizesTheProposalWithoutApplyingIt() {
        val result = preview(
            listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            request = WorkoutMidSessionRequest.Request(preferEasier = true, restSeconds = 30),
            availableEquipment = setOf(TrainingEquipment.STABLE_CHAIR)
        )
        val spoken = WorkoutMidSessionPreviewFormatter.spokenSummary(
            result.copy(
                addedMoves = listOf("Nâng gối ngồi ghế"),
                removedMoves = listOf("Bài bổ sung chưa bắt đầu")
            )
        )

        assertTrue(spoken.contains("Squat không tạ"))
        assertTrue(spoken.contains("Đứng lên – ngồi xuống"))
        assertTrue(spoken.contains("Bỏ bài chưa bắt đầu"))
        assertTrue(spoken.contains("Thêm bài tùy chọn"))
        assertTrue(spoken.contains("30 giây"))
        assertTrue(spoken.contains("Các hiệp đã xác nhận được giữ nguyên"))
        assertTrue(spoken.contains("Chưa áp dụng"))
        assertTrue(spoken.length <= 350)
    }

    @Test fun setLimitPreviewExplainsThatOnlyOptionalSetsWereRemoved() {
        val result = preview(
            listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            request = WorkoutMidSessionRequest.Request(currentSetLimit = 2),
            completedCurrentSets = 1
        )

        val text = WorkoutMidSessionPreviewFormatter.format(result)
        val spoken = WorkoutMidSessionPreviewFormatter.spokenSummary(result)
        assertTrue(text.contains("Giới hạn Squat không tạ tối đa còn 2 hiệp"))
        assertTrue(text.contains("giữ nguyên 2 hiệp bắt buộc"))
        assertTrue(text.contains("chỉ bỏ hiệp tùy chọn"))
        assertTrue(spoken.contains("Giới hạn Squat không tạ tối đa còn 2 hiệp"))
        assertTrue(spoken.contains("2 hiệp bắt buộc"))
        assertTrue(spoken.contains("Chưa áp dụng"))
        assertTrue(spoken.length <= 350)
    }

}
