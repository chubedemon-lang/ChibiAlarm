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
        pausedForSafety: Boolean = false,
        restIsActive: Boolean = false,
        restRemainingMs: Long? = null,
        restIsPaused: Boolean = false,
        completedSets: Int? = null,
        completedSetValues: List<Int>? = null,
        completedEffortLevels: List<WorkoutEffortFeedback.Level?>? = null,
        nextMove: TrainingMove? = null
    ): String? {
        if (input.isBlank() || input.length > 300) return null

        val nextMoveAnswer = WorkoutExerciseConversation.answerForNextMove(input, nextMove)
        val restStatusAnswer = WorkoutRestStatusConversation.answer(
            input = input,
            restIsActive = restIsActive,
            remainingMs = restRemainingMs,
            restIsPaused = restIsPaused
        )
        val effortRestAdviceAnswer = WorkoutEffortRestAdviceConversation.answer(
            input = input,
            prescribedRestSeconds = currentMove?.restSeconds,
            completedSets = completedSets,
            effortLevels = completedEffortLevels
        )
        val effortStatusAnswer = WorkoutEffortStatusConversation.answer(
            input = input,
            completedSets = completedSets,
            effortLevels = completedEffortLevels
        )
        val setStatusAnswer = WorkoutSetStatusConversation.answer(
            input = input,
            currentDose = currentDose,
            fallbackDose = currentMove?.dose,
            completedSets = completedSets,
            completedSetValues = completedSetValues
        )
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

        val explicitUnknownExercise = WorkoutExerciseConversation.hasUnknownExplicitExerciseName(input)
        val resolvedAnswer = when {
            explicitUnknownExercise -> catalogAnswer
                ?: "Chibi chưa nhận ra tên động tác trong thư viện. Hãy nêu rõ tên bài để tránh hướng dẫn nhầm."
            nextMoveAnswer != null -> nextMoveAnswer
            restStatusAnswer != null -> restStatusAnswer
            effortRestAdviceAnswer != null -> effortRestAdviceAnswer
            effortStatusAnswer != null -> effortStatusAnswer
            setStatusAnswer != null -> setStatusAnswer
            catalogAnswer?.startsWith(UNKNOWN_EXERCISE_PREFIX) == true -> catalogAnswer
            catalogAnswer != null -> catalogAnswer
            contextualAnswer != null -> contextualAnswer
            else -> null
        } ?: return null

        if (pausedForSafety) {
            return "Buổi tập đang dừng vì cảnh báo an toàn. Chibi không cung cấp hướng dẫn vận động " +
                "hoặc đề xuất tiếp tục lúc này. Hãy nghỉ; nếu triệu chứng nghiêm trọng hoặc kéo dài, " +
                "hãy tìm hỗ trợ y tế phù hợp."
        }
        return resolvedAnswer
    }
}
