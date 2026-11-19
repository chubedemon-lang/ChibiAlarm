package com.chibialarm.app

/**
 * Routes questions from the active guided workout to catalog-grounded answers.
 *
 * Generic follow-ups may inherit the currently open exercise. A named exercise is resolved
 * by the catalog first, so asking about Plank while Squat is open cannot answer about Squat.
 * This router is offline-only and never mutates the workout or its reward ledger.
 */
object WorkoutCoachQuestionRouter {
    private const val UNKNOWN_EXERCISE_PREFIX = "Chibi chưa nhận ra tên động tác"

    fun answer(
        input: String,
        currentMove: TrainingMove?,
        currentDose: String? = null,
        pausedForSafety: Boolean = false
    ): String? {
        if (input.isBlank() || input.length > 300) return null

        val catalogAnswer = WorkoutExerciseConversation.answer(input)
        val contextualAnswer = if (
            currentMove != null && WorkoutExerciseConversation.isContextualQuestion(input)
        ) {
            WorkoutExerciseConversation.answerForCurrentMove(
                input = input,
                move = currentMove,
                currentDose = currentDose ?: currentMove.dose
            )
        } else {
            null
        }

        val resolvedAnswer = when {
            catalogAnswer != null && !catalogAnswer.startsWith(UNKNOWN_EXERCISE_PREFIX) ->
                catalogAnswer
            contextualAnswer != null -> contextualAnswer
            else -> catalogAnswer
        } ?: return null

        if (pausedForSafety) {
            return "Buổi tập đang dừng vì cảnh báo an toàn. Chibi không cung cấp hướng dẫn vận động " +
                "hoặc đề xuất tiếp tục lúc này. Hãy nghỉ; nếu triệu chứng nghiêm trọng hoặc kéo dài, " +
                "hãy tìm hỗ trợ y tế phù hợp."
        }
        return resolvedAnswer
    }
}
