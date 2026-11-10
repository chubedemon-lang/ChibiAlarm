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
}
