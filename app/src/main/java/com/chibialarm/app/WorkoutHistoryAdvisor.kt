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
        // Journals are append-ordered. Bound work to the newest records so an old,
        // long-running history cannot hide the current week's sessions behind the cap.
        val recent = workouts.takeLast(MAX_RECORDS).asSequence().mapNotNull { workout ->
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
     * Combines weekly activity totals with recent, repeated self-reported hard effort.
     * The result is an optional reflection, never a recovery/readiness diagnosis.
     */
    fun lighterSessionMessage(
        summary: Summary,
        recentEffort: WorkoutEffortPlanAdvisor.Suggestion?
    ): String? {
        val reasons = mutableListOf<String>()
        if (summary.suggestLighterSession) {
            reasons += "Trong 7 ngày, bạn đã ghi ${summary.activeDays}/7 ngày vận động và ${summary.totalMinutes} phút."
        }
        if (recentEffort != null) {
            val severity = buildList {
                if (recentEffort.hardSets > 0) add("${recentEffort.hardSets} hiệp khó")
                if (recentEffort.veryHardSets > 0) add("${recentEffort.veryHardSets} hiệp quá sức")
            }.joinToString(" và ")
            reasons += "Trong ${recentEffort.repeatedSessions} buổi Guided Workout gần đây, bạn tự đánh dấu ${severity.ifBlank { "mức gắng sức cao" }} ở ${recentEffort.exerciseName}."
        }
        if (reasons.isEmpty()) return null
        return "🐰 " + reasons.joinToString(" ") +
            " Bạn có thể cân nhắc nghỉ hoặc xem giáo án phục hồi nhẹ. " +
            "Đây là gợi ý từ nhật ký tự ghi, không phải đánh giá hồi phục hay tư vấn y khoa."
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
        // Use the same newest-record window as summarize(); old journal entries must not
        // mask a qualifying session from yesterday after the retention bound is reached.
        val lastGuided = workouts.takeLast(MAX_RECORDS).asSequence().mapNotNull { workout ->
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
            .filter {
                it.id != recommended.id &&
                    it.category == recommended.category &&
                    difficultyRank(it.id) <= difficultyRank(recommended.id)
            }
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

    private fun difficultyRank(programId: String): Int = when (programId) {
        "advanced", "weighted" -> 2
        "full", "legs", "upper", "core" -> 1
        else -> 0
    }

    private fun moveOverlapCount(first: TrainingProgram, second: TrainingProgram): Int {
        val firstMoves = first.moves.map { it.name }.toSet()
        val secondMoves = second.moves.map { it.name }.toSet()
        return firstMoves.intersect(secondMoves).size
    }
}
