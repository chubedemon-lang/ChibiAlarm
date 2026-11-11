package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutContextCoachTest {
    private val move = TrainingMove(
        name = "Chống đẩy tường",
        dose = "2 hiệp × 8 lần",
        steps = "Chống hai tay lên tường rồi đẩy ra.",
        easier = "Đứng gần tường hơn.",
        avoid = "Không ép cổ tay.",
        seconds = 90
    )

    @Test fun normalCueUsesCatalogTechniqueAndDose() {
        val text = WorkoutContextCoach.script(move)
        assertTrue(text.contains("2 hiệp × 8 lần"))
        assertTrue(text.contains("Chống hai tay lên tường"))
        assertTrue(text.contains("Không ép cổ tay"))
        assertFalse(text.contains("Cách tập nhẹ hơn"))
        assertTrue(text.contains("không phải đánh giá tư thế"))
    }

    @Test fun easierCueGivesSpecificAlternativeInsteadOfFullIntensity() {
        val text = WorkoutContextCoach.script(move, preferEasier = true)
        assertTrue(text.contains("Đứng gần tường hơn"))
        assertFalse(text.contains("Chống hai tay lên tường"))
        assertTrue(text.contains("Hãy dừng nếu đau"))
    }

    @Test fun progressCueReportsSelfConfirmedSetsAndNextMove() {
        val text = WorkoutContextCoach.progressScript(
            WorkoutContextCoach.Progress(
                completedMoves = 2,
                totalMoves = 5,
                currentMoveIndex = 2,
                currentMoveName = "Squat không tạ",
                sets = WorkoutContextCoach.SetProgress(
                    completed = 1, required = 2, maximum = 3,
                    targetMin = 8, targetMax = 12, unit = "lần"
                ),
                exerciseSecondsRemaining = 42,
                nextMoveName = "Nâng hông trên sàn"
            )
        )
        assertTrue(text.contains("2 trên 5 động tác"))
        assertTrue(text.contains("Đang ở bước 3: Squat không tạ"))
        assertTrue(text.contains("tự xác nhận 1 trên 3 hiệp"))
        assertTrue(text.contains("tối thiểu 2 hiệp"))
        assertTrue(text.contains("8 đến 12 lần"))
        assertTrue(text.contains("còn 42 giây"))
        assertTrue(text.contains("Nâng hông trên sàn"))
        assertTrue(text.contains("không phải cảm biến tự đếm"))
    }

    @Test fun restStatusUsesItsOwnCountdownAndDoesNotCallItExerciseTime() {
        val text = WorkoutContextCoach.progressScript(
            WorkoutContextCoach.Progress(
                completedMoves = 1,
                totalMoves = 3,
                currentMoveIndex = 1,
                currentMoveName = "Chống đẩy tường",
                resting = true,
                restSecondsRemaining = 35,
                exerciseSecondsRemaining = 90
            )
        )
        assertTrue(text.contains("đang nghỉ riêng, còn 35 giây"))
        assertTrue(text.contains("Thời gian nghỉ không tính vào thời gian vận động"))
        assertFalse(text.contains("Thời gian nhắc nhở của động tác còn"))
    }

    @Test fun safetyLockIsIncludedInProgressReport() {
        val text = WorkoutContextCoach.progressScript(
            WorkoutContextCoach.Progress(
                completedMoves = 0,
                totalMoves = 2,
                currentMoveIndex = 0,
                currentMoveName = "Squat",
                pausedForSafety = true
            )
        )
        assertTrue(text.contains("đang khóa an toàn"))
        assertTrue(text.contains("không tự tiếp tục"))
    }


    @Test fun progressCueIncludesAnApproximateWholeSessionEstimate() {
        val text = WorkoutContextCoach.progressScript(
            WorkoutContextCoach.Progress(
                completedMoves = 1,
                totalMoves = 3,
                currentMoveIndex = 1,
                currentMoveName = "Squat",
                estimatedSessionSecondsRemaining = 95
            )
        )
        assertTrue(text.contains("Ước tính phần còn lại khoảng 1 phút 35 giây"))
        assertTrue(text.contains("nghỉ và chuyển bước"))
        assertTrue(text.contains("đây chỉ là ước tính"))
    }

    @Test fun safetyLockDoesNotPresentAWorkoutTimeEstimate() {
        val text = WorkoutContextCoach.progressScript(
            WorkoutContextCoach.Progress(
                completedMoves = 0,
                totalMoves = 2,
                currentMoveIndex = 0,
                currentMoveName = "Squat",
                pausedForSafety = true
            )
        )
        assertTrue(text.contains("đang khóa an toàn"))
        assertFalse(text.contains("Ước tính phần còn lại"))
    }

    @Test fun emptyAndOversizedCatalogFieldsAreBounded() {
        val text = WorkoutContextCoach.script(
            TrainingMove("", "", "x".repeat(2000), "", "", 60)
        )
        assertTrue(text.startsWith("Động tác hiện tại"))
        assertTrue(text.length < 800)
        assertTrue(text.contains("không phải đánh giá tư thế"))
    }
}
