package com.chibialarm.app

import java.time.LocalDate

/**
 * Small offline training-load hint derived only from dates and minutes in the local workout journal.
 * It never reads free-text notes, sends data to a service, or diagnoses recovery/readiness.
 */
object WorkoutHistoryAdvisor {
    data class Summary(
        val sessions: Int,
        val activeDays: Int,
        val totalMinutes: Int,
        val latestDate: LocalDate?
    ) {
        /** A conservative prompt to consider an easier day, not an instruction to train or rest. */
        val suggestLighterSession: Boolean
            get() = activeDays >= 5 && totalMinutes >= 180
    }

    /**
     * A same-category alternative when the exact same guided program was logged yesterday.
     * This is a variety hint only; it does not estimate recovery or tell the user to train.
     */
    data class VarietySuggestion(
        val program: TrainingProgram,
        val previousProgramName: String,
        val reason: String
    )

    private const val MAX_RECORDS = 10_000

    fun summarize(workouts: List<Workout>, today: LocalDate = LocalDate.now()): Summary {
        val firstDay = today.minusDays(6)
        val recent = workouts.asSequence().take(MAX_RECORDS).mapNotNull { workout ->
            val date = runCatching { LocalDate.parse(workout.date) }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(firstDay) || date.isAfter(today) || workout.minutes !in 1..600) {
                return@mapNotNull null
            }
            date to workout.minutes
        }.toList()
        val total = recent.sumOf { it.second.toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Summary(
            sessions = recent.size,
            activeDays = recent.map { it.first }.distinct().size,
            totalMinutes = total,
            latestDate = recent.maxOfOrNull { it.first }
        )
    }

    /**
     * Avoids offering the exact same catalog program on two consecutive calendar days.
     * Only structured catalog names, dates and durations are inspected; private journal notes
     * are deliberately ignored. Alternatives must already have passed the caller's safety filters.
     */
    fun suggestVariety(
        recommended: TrainingProgram,
        workouts: List<Workout>,
        eligibleAlternatives: List<TrainingProgram>,
        today: LocalDate = LocalDate.now()
    ): VarietySuggestion? {
        val yesterday = today.minusDays(1)
        val lastGuided = workouts.asSequence().take(MAX_RECORDS).mapNotNull { workout ->
            val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                ?: return@mapNotNull null
            if (date != yesterday || workout.minutes !in 1..600) return@mapNotNull null
            val program = TrainingCatalog.programs.firstOrNull { it.name == workout.name }
                ?: return@mapNotNull null
            date to program
        }.lastOrNull() ?: return null

        val previous = lastGuided.second
        if (previous.id != recommended.id) return null

        val alternative = eligibleAlternatives.asSequence()
            .filter { it.id != recommended.id && it.category == recommended.category }
            .distinctBy { it.id }
            .minWithOrNull(
                compareBy<TrainingProgram> { moveOverlapCount(it, previous) }
                    .thenBy { it.id }
            ) ?: return null

        return VarietySuggestion(
            program = alternative,
            previousProgramName = previous.name,
            reason = "Hôm qua bạn đã ghi buổi ${previous.name}. Để đổi nội dung thay vì lặp nguyên giáo án, Chibi gợi ý ${alternative.name} trong cùng nhóm ${recommended.category}. Đây chỉ là gợi ý đa dạng bài tập, không phải đánh giá hồi phục."
        )
    }

    private fun moveOverlapCount(first: TrainingProgram, second: TrainingProgram): Int {
        val firstMoves = first.moves.map { it.name }.toSet()
        val secondMoves = second.moves.map { it.name }.toSet()
        return firstMoves.intersect(secondMoves).size
    }
}
