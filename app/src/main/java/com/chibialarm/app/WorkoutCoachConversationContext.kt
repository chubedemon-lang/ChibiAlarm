package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Small, in-memory conversation memory for one Guided Workout Activity.
 *
 * The active move remains the default for ordinary follow-ups ("nghỉ bao lâu?").
 * A phrase such as "bài đó" refers to the last explicitly named catalog exercise.
 * This state is deliberately not persisted, exported, logged, or sent over the network.
 */
class WorkoutCoachConversationContext {
    private var lastExplicitMove: TrainingMove? = null

    fun answer(
        input: String,
        activeMove: TrainingMove?,
        activeDose: String? = null,
        pausedForSafety: Boolean = false,
        restIsActive: Boolean = false,
        restRemainingMs: Long? = null,
        restIsPaused: Boolean = false,
        activeCompletedSets: Int? = null,
        activeCompletedSetValues: List<Int>? = null,
        activeEffortLevels: List<WorkoutEffortFeedback.Level?>? = null,
        nextMove: TrainingMove? = null
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null

        val refersToPrevious = WorkoutExerciseConversation.refersToPreviousExercise(input)
        val targetMove = if (refersToPrevious) lastExplicitMove else activeMove
        val targetDose = when {
            targetMove == null -> null
            activeMove != null && targetMove.name == activeMove.name -> activeDose ?: targetMove.dose
            else -> targetMove.dose
        }

        val response = WorkoutCoachQuestionRouter.answer(
            input = input,
            currentMove = targetMove,
            currentDose = targetDose,
            pausedForSafety = pausedForSafety,
            restIsActive = restIsActive,
            restRemainingMs = restRemainingMs,
            restIsPaused = restIsPaused,
            completedSets = if (targetMove != null && targetMove.name == activeMove?.name) {
                activeCompletedSets
            } else {
                null
            },
            completedSetValues = if (targetMove != null && targetMove.name == activeMove?.name) {
                activeCompletedSetValues
            } else {
                null
            },
            completedEffortLevels = if (targetMove != null && targetMove.name == activeMove?.name) {
                activeEffortLevels
            } else {
                null
            },
            nextMove = nextMove
        ) ?: return null

        // Remember only a uniquely named catalog move after a successful, non-ambiguous answer.
        // Never update the conversational target from an unknown exercise or during a safety pause.
        if (!pausedForSafety &&
            !response.startsWith(UNKNOWN_EXERCISE_PREFIX) &&
            !response.startsWith(AMBIGUOUS_EXERCISE_PREFIX)
        ) {
            resolveExplicitMove(input)?.let { lastExplicitMove = it }
        }
        return response
    }

    /** Clear references when a new guided session is started in the same Activity instance. */
    fun reset() {
        lastExplicitMove = null
    }

    private fun resolveExplicitMove(input: String): TrainingMove? {
        val query = normalize(input)
        if (query.isBlank()) return null
        val matches = TrainingCatalog.allMoves
            .distinctBy { normalize(it.name) }
            .filter { move ->
                val name = normalize(move.name)
                name.length >= MIN_EXPLICIT_NAME_CHARS && query.contains(name)
            }
        if (matches.isEmpty()) return null

        val longestLength = matches.maxOf { normalize(it.name).length }
        val longest = matches.filter { normalize(it.name).length == longestLength }
        if (longest.size != 1) return null

        val selectedName = normalize(longest.single().name)
        // A longer catalog name may contain a shorter alias. Accept that nested match,
        // but never choose between two distinct exercise names mentioned in one utterance.
        val hasDifferentExercise = matches.any { move ->
            val name = normalize(move.name)
            name != selectedName && !selectedName.contains(name)
        }
        return longest.single().takeUnless { hasDifferentExercise }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private companion object {
        const val MAX_INPUT_CHARS = 300
        const val MIN_EXPLICIT_NAME_CHARS = 3
        const val UNKNOWN_EXERCISE_PREFIX = "Chibi chưa nhận ra tên động tác"
        const val AMBIGUOUS_EXERCISE_PREFIX = "Chibi tìm thấy vài động tác gần giống"
    }
}
