package com.chibialarm.app

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutBreathingGuideTest {
    @Test fun strengthMovesCueExhaleDuringEffortWithoutBreathHolding() {
        val cue = WorkoutBreathingGuide.cue("Squat không tạ")

        assertTrue(cue.startsWith("Squat không tạ."))
        assertTrue(cue.contains("Thở ra nhẹ ở pha dùng lực"))
        assertTrue(cue.contains("hít vào khi trở về hoặc hạ xuống"))
        assertTrue(cue.contains("Không nín thở"))
    }

    @Test fun plankCuePrioritizesContinuousComfortableBreathing() {
        val cue = WorkoutBreathingGuide.cue("Plank khuỷu hoặc plank gối")

        assertTrue(cue.contains("Giữ nhịp hít vào và thở ra đều"))
        assertTrue(cue.contains("Không nín thở"))
        assertTrue(cue.contains("không thể thở thoải mái"))
    }

    @Test fun cardioCueUsesTheTalkTestAndAllowsSlowingDown() {
        val cue = WorkoutBreathingGuide.cue("Đi bộ nhanh tại chỗ")

        assertTrue(cue.contains("tốc độ vẫn nói chuyện được"))
        assertTrue(cue.contains("giảm tốc độ hoặc nghỉ"))
    }

    @Test fun unknownExerciseDoesNotReceiveInventedPoseSpecificAdvice() {
        val cue = WorkoutBreathingGuide.cue("Bài tự tạo của người dùng")

        assertTrue(cue.contains("Chibi chưa có gợi ý nhịp thở riêng"))
        assertTrue(cue.contains("thở liên tục, tự nhiên"))
        assertTrue(cue.contains("không nín thở"))
    }

    @Test fun everyCueIncludesStopSignalsForUnusualSymptoms() {
        val cue = WorkoutBreathingGuide.cue("Squat không tạ")

        assertTrue(cue.contains("đau ngực"))
        assertTrue(cue.contains("chóng mặt"))
        assertTrue(cue.contains("hãy dừng tập và nghỉ"))
    }
}
