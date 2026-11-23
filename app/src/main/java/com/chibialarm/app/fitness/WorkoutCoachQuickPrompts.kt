package com.chibialarm.app.fitness

/**
 * Context-aware quick prompts for the live workout coach.
 *
 * Prompts only fill the input field; they never submit, change the plan, start a rest,
 * or record a set. The existing preview/confirmation flow remains authoritative.
 */
internal object WorkoutCoachQuickPrompts {
    data class Prompt(
        val label: String,
        val utterance: String
    )

    fun forSession(
        resting: Boolean,
        hasOptionalFutureExercises: Boolean,
        hasOptionalCurrentSets: Boolean,
        safetyStopped: Boolean = false,
        hasNextExercise: Boolean = false,
        hasCompletedSets: Boolean = false,
        hasLatestEffortCheckIn: Boolean = false
    ): List<Prompt> {
        if (safetyStopped) return emptyList()

        if (resting) {
            return mutableListOf(
                Prompt("Nghỉ thêm 30 giây", "Nghỉ thêm 30 giây"),
                Prompt("Nghỉ thêm 1 phút", "Nghỉ thêm 1 phút")
            ).apply {
                addEffortPrompts(hasCompletedSets, hasLatestEffortCheckIn)
            }
        }

        val prompts = mutableListOf(
            Prompt("Còn 5 phút", "Tôi chỉ còn 5 phút"),
            Prompt("Hôm nay hơi mệt", "Hôm nay hơi mệt, đổi sang bài nhẹ hơn")
        )
        if (hasOptionalFutureExercises) {
            prompts += Prompt("Bỏ bài bổ sung", "Bỏ bài bổ sung")
        }
        if (hasOptionalCurrentSets) {
            prompts += Prompt("Chỉ hiệp bắt buộc", "Chỉ làm hiệp bắt buộc")
        }
        if (hasNextExercise) {
            // The router answers from the next catalog move, not the exercise currently open.
            prompts += Prompt("Bài tiếp theo", "Bài tiếp theo tập thế nào?")
        }
        prompts.addEffortPrompts(hasCompletedSets, hasLatestEffortCheckIn)
        // Read-only coaching prompts are available beside plan controls. They only fill
        // the input; the existing coach answer and safety gates handle the actual request.
        prompts += Prompt("Thở thế nào?", "Thở thế nào?")
        prompts += Prompt("Hướng dẫn từng bước", "Hướng dẫn từng bước")
        return prompts
    }

    private fun MutableList<Prompt>.addEffortPrompts(
        hasCompletedSets: Boolean,
        hasLatestEffortCheckIn: Boolean
    ) {
        if (hasCompletedSets) {
            // Reads only the structured, user-confirmed check-ins already recorded for this move.
            add(Prompt("Xem cảm nhận", "Tổng kết cảm nhận các hiệp đã ghi"))
        }
        if (hasLatestEffortCheckIn) {
            add(Prompt("Sửa cảm nhận", "Sửa cảm nhận hiệp vừa rồi"))
            // The router explains the latest rating; it never starts or changes the rest timer.
            add(Prompt("Nghỉ theo cảm nhận", "Chibi đề xuất nghỉ theo mức độ gắng sức"))
        }
    }
}
