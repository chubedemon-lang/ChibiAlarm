package com.chibialarm.app

/**
 * Converts a person's optional perceived-effort check-in into a conservative rest suggestion.
 *
 * This is not a physiological measurement or medical assessment. It never increases reps,
 * resistance, set count, or XP; the user chooses whether to accept the suggestion.
 */
object WorkoutEffortFeedback {
    enum class Level(val label: String) {
        EASY("Nhẹ, còn dư sức"),
        MODERATE("Vừa sức"),
        HARD("Khó, cần nghỉ thêm"),
        VERY_HARD("Quá sức, tạm dừng")
    }

    data class Recommendation(
        val restSeconds: Int,
        val holdBeforeNextSet: Boolean,
        val message: String
    )

    fun recommend(level: Level, prescribedRestSeconds: Int): Recommendation {
        require(prescribedRestSeconds in 15..180) {
            "Prescribed rest must be between 15 and 180 seconds"
        }
        return when (level) {
            Level.EASY -> Recommendation(
                restSeconds = prescribedRestSeconds,
                holdBeforeNextSet = false,
                message = "Bạn thấy nhẹ hơn dự kiến. Giữ nguyên giáo án; không tự tăng số lần hoặc tải."
            )
            Level.MODERATE -> Recommendation(
                restSeconds = prescribedRestSeconds,
                holdBeforeNextSet = false,
                message = "Vừa sức. Nghỉ theo thời gian của giáo án."
            )
            Level.HARD -> Recommendation(
                restSeconds = (prescribedRestSeconds + 15).coerceAtMost(600),
                holdBeforeNextSet = false,
                message = "Bạn thấy khá khó. Chibi đề xuất nghỉ thêm 15 giây; không tăng tải."
            )
            Level.VERY_HARD -> Recommendation(
                restSeconds = 120,
                holdBeforeNextSet = true,
                message = "Bạn thấy quá sức. Hãy tạm dừng trước hiệp tiếp theo và chỉ tiếp tục khi đã hồi phục, thấy an toàn."
            )
        }
    }
}
