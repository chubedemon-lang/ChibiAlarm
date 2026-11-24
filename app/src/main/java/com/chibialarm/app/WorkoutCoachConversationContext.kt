package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Small, in-memory conversation memory for one Guided Workout Activity.
 *
 * The active move remains the default for ordinary follow-ups ("nghỉ bao lâu?").
 * A phrase such as "bài đó" refers to the last explicitly named catalog exercise or
 * the upcoming catalog exercise that the user just asked about.
 * Only the exact canonical exercise name may be copied into the guest-local workout checkpoint
 * or Android saved state so a restored session can keep a useful follow-up reference. No raw
 * transcript, free-form user text, effort answer, or health note is stored by this memory.
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

        val nextMoveAnswer = WorkoutExerciseConversation.answerForNextMove(input, nextMove)
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

        // A successful question about the upcoming move changes the conversational
        // referent. Otherwise "bài đó" after "bài tiếp theo cần gì?" would incorrectly
        // point back to an older exercise mentioned earlier in the session.
        // The router's safety response must never update memory.
        if (!pausedForSafety &&
            !response.startsWith(UNKNOWN_EXERCISE_PREFIX) &&
            !response.startsWith(AMBIGUOUS_EXERCISE_PREFIX)
        ) {
            when {
                nextMove != null && nextMoveAnswer != null && response == nextMoveAnswer ->
                    lastExplicitMove = nextMove
                else ->
                    resolveExplicitMove(input)?.let { lastExplicitMove = it }
            }
        }
        return response
    }

    /**
     * Return only a canonical catalog name for the guest-local checkpoint/Bundle.
     * The conversation transcript and all free-form input remain transient.
     */
    fun snapshotMoveName(): String? = lastExplicitMove?.name
        ?.takeIf { canonicalMove(it) != null && it.length <= MAX_MOVE_NAME_CHARS }

    /**
     * Restore a reference only when it exactly matches one canonical catalog entry.
     * Invalid, unknown, oversized, or ambiguous values clear the previous reference.
     */
    fun restoreMoveName(name: String?) {
        lastExplicitMove = name
            ?.takeIf { it.length in 1..MAX_MOVE_NAME_CHARS }
            ?.let(::canonicalMove)
    }

    private fun canonicalMove(name: String): TrainingMove? =
        TrainingCatalog.allMoves.singleOrNull { it.name == name }

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
        const val MAX_MOVE_NAME_CHARS = 100
        const val MIN_EXPLICIT_NAME_CHARS = 3
        const val UNKNOWN_EXERCISE_PREFIX = "Chibi chưa nhận ra tên động tác"
        const val AMBIGUOUS_EXERCISE_PREFIX = "Chibi tìm thấy vài động tác gần giống"
    }
}
