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

    private const val MAX_PREVIOUS_SET_REPORTS = 20
    private const val MAX_RECOMMENDED_REST_SECONDS = 300
    private const val EXTRA_REST_PER_HARD_SET = 15
    private const val MAX_EXTRA_REST_SECONDS = 90

    fun recommend(level: Level, prescribedRestSeconds: Int): Recommendation =
        recommend(level, prescribedRestSeconds, emptyList())

    /**
     * Adapts only the optional rest suggestion to consecutive self-reported HARD/VERY_HARD
     * check-ins for the same exercise in this session. A lighter/easier report breaks the
     * streak. The caller must still obtain explicit confirmation before starting rest.
     */
    fun recommend(
        level: Level,
        prescribedRestSeconds: Int,
        previousLevelsForSameMove: List<Level?>
    ): Recommendation {
        require(prescribedRestSeconds in 15..180) {
            "Prescribed rest must be between 15 and 180 seconds"
        }
        require(previousLevelsForSameMove.size <= MAX_PREVIOUS_SET_REPORTS) {
            "Previous set reports must be bounded to the exercise plan"
        }

        val previousHardStreak = previousLevelsForSameMove.asReversed().takeWhile {
            it == Level.HARD || it == Level.VERY_HARD
        }.size

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
            Level.HARD -> {
                val hardStreak = previousHardStreak + 1
                val requestedExtra = (hardStreak * EXTRA_REST_PER_HARD_SET)
                    .coerceAtMost(MAX_EXTRA_REST_SECONDS)
                val rest = (prescribedRestSeconds + requestedExtra)
                    .coerceAtMost(MAX_RECOMMENDED_REST_SECONDS)
                val actualExtra = (rest - prescribedRestSeconds).coerceAtLeast(0)
                val message = if (previousHardStreak == 0) {
                    "Bạn thấy khá khó. Chibi đề xuất nghỉ thêm 15 giây; không tăng tải."
                } else {
                    "Bạn vừa tự đánh dấu mức khó ở " + hardStreak +
                        " hiệp liên tiếp. Chibi đề xuất nghỉ thêm " + actualExtra +
                        " giây (tổng " + rest + " giây). Đây là gợi ý từ cảm nhận của bạn, " +
                        "không phải phép đo thể lực; không tăng tải."
                }
                Recommendation(
                    restSeconds = rest,
                    holdBeforeNextSet = false,
                    message = message
                )
            }
            Level.VERY_HARD -> Recommendation(
                restSeconds = maxOf(prescribedRestSeconds, 120)
                    .coerceAtMost(MAX_RECOMMENDED_REST_SECONDS),
                holdBeforeNextSet = true,
                message = "Bạn tự đánh giá hiệp này quá sức. Hãy giữ buổi tập tạm dừng trước bước tiếp theo; chỉ tiếp tục khi đã hồi phục và thấy an toàn."
            )
        }
    }
}
