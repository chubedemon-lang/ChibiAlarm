package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.TrainingMove
import com.chibialarm.app.WorkoutConstraintProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMidSessionPreviewFormatterTest {
    private fun move(name: String): TrainingMove =
        TrainingCatalog.allMoves.first { it.name == name }

    private fun preview(
        moves: List<TrainingMove>,
        currentIndex: Int,
        request: WorkoutMidSessionRequest.Request
    ): WorkoutMidSessionPlanner.Preview {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = currentIndex,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 75,
            extensionMoveNames = emptySet(),
            level = 0
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
            request = WorkoutMidSessionRequest.Request(preferEasier = true)
        )

        val text = WorkoutMidSessionPreviewFormatter.format(result)
        assertTrue(text.contains("1. Bước trước — Khởi động tại chỗ"))
        assertTrue(text.contains("2. Đang tập — Đứng lên – ngồi xuống"))
        assertTrue(text.contains("2 hiệp × 6–10 lần"))
        assertTrue(text.contains("3. Tiếp theo — Chống đẩy tường"))
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
}
