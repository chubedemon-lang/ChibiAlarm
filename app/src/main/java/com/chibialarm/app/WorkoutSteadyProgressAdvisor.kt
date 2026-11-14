package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * Finds a repeated, self-reported manageable-effort pattern and offers an optional
 * next-step discussion. It never changes a workout or increases its dose.
 *
 * Only structured effortLog values are inspected. Journal notes and safety preferences
 * are intentionally excluded; the current Fitness form remains the source of constraints.
 */
object WorkoutSteadyProgressAdvisor {
    data class Suggestion(
        val exerciseName: String,
        val consistentDays: Int,
        val easySets: Int,
        val moderateSets: Int,
        val latestDate: LocalDate,
        val message: String,
        val prompt: String
    ) {
        /** Backward-compatible alias; evidence is counted by distinct calendar days. */
        val consistentSessions: Int get() = consistentDays
    }

    private data class MoveEffort(
        val name: String,
        val levels: List<WorkoutEffortFeedback.Level>
    )

    private data class Session(
        val id: String,
        val date: LocalDate,
        val originalIndex: Int,
        val selfReportedSets: Int,
        val moves: List<MoveEffort>
    )

    private data class Candidate(
        val name: String,
        val easySets: Int,
        val moderateSets: Int,
        val latestDate: LocalDate
    )

    private const val MAX_RECORDS = 10_000
    private const val MAX_SESSIONS = 40
    private const val REQUIRED_SESSIONS = 3
    private const val MAX_LOG_CHARS = 1_200
    private const val MAX_MOVES = 100
    private const val MAX_SETS = 20

    private val effortPattern = Regex(
        """hiệp\s+(\d{1,2})\s*:\s*(Nhẹ,\s*còn dư sức|Vừa sức|Khó,\s*cần nghỉ thêm|Quá sức,\s*tạm dừng)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Suggests a conversation only when the same exercise has check-ins on at least
     * three distinct recent calendar days. If multiple sessions occur on one day, every
     * session's recorded sets for that exercise must be EASY or MODERATE. Partial check-ins
     * are not positive evidence.
     */
    fun suggest(
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now(),
        lookbackDays: Int = 30
    ): Suggestion? {
        require(lookbackDays in 1..90)
        val firstDay = today.minusDays(lookbackDays.toLong() - 1L)
        val sessions = workouts.takeLast(MAX_RECORDS)
            .asSequence()
            .mapIndexedNotNull { index, workout ->
                val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val validId = runCatching {
                    UUID.fromString(workout.sessionId).toString() == workout.sessionId
                }.getOrDefault(false)
                if (!validId || date.isBefore(firstDay) || date.isAfter(today) ||
                    workout.minutes !in 1..600 || workout.effortLog.isBlank() ||
                    workout.effortLog.length > MAX_LOG_CHARS
                ) return@mapIndexedNotNull null

                val moves = parseLog(workout.effortLog).distinctBy { normalize(it.name) }
                val recordedSetCount = moves.sumOf { it.levels.size }
                if (moves.isEmpty() || workout.selfReportedSets !in 1..(MAX_MOVES * MAX_SETS) ||
                    recordedSetCount != workout.selfReportedSets
                ) return@mapIndexedNotNull null
                Session(workout.sessionId, date, index, workout.selfReportedSets, moves)
            }
            .toList()
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.originalIndex })
            .distinctBy { it.id }

        // Bound history by distinct days, not raw sessions. Multiple workouts on one
        // calendar day must never manufacture three separate progression observations.
        val recentDates = sessions.map { it.date }.distinct().take(MAX_SESSIONS).toSet()
        if (recentDates.size < REQUIRED_SESSIONS) return null
        val boundedSessions = sessions.filter { it.date in recentDates }

        val candidates = boundedSessions
            .flatMap { session -> session.moves.map { session to it } }
            .groupBy { normalize(it.second.name) }
            .values
            .mapNotNull { observations ->
                // A day is one observation even if the user completed several sessions.
                // Any hard/very-hard set in any same-day session blocks progression.
                val latestThreeDays = observations
                    .groupBy { it.first.date }
                    .map { (date, sameDay) -> date to sameDay.map { it.second } }
                    .sortedByDescending { it.first }
                    .take(REQUIRED_SESSIONS)

                if (latestThreeDays.size != REQUIRED_SESSIONS) return@mapNotNull null
                val allManageable = latestThreeDays.all { (_, dayMoves) ->
                    dayMoves.isNotEmpty() && dayMoves.all { move ->
                        move.levels.isNotEmpty() && move.levels.all {
                            it == WorkoutEffortFeedback.Level.EASY ||
                                it == WorkoutEffortFeedback.Level.MODERATE
                        }
                    }
                }
                if (!allManageable) return@mapNotNull null

                val levels = latestThreeDays.flatMap { it.second }.flatMap { it.levels }
                Candidate(
                    name = latestThreeDays.first().second.first().name,
                    easySets = levels.count { it == WorkoutEffortFeedback.Level.EASY },
                    moderateSets = levels.count { it == WorkoutEffortFeedback.Level.MODERATE },
                    latestDate = latestThreeDays.maxOf { it.first }
                )
            }
            .sortedWith(
                compareByDescending<Candidate> { it.easySets }
                    .thenByDescending { it.moderateSets }
                    .thenByDescending { it.latestDate }
                    .thenBy { normalize(it.name) }
            )

        val best = candidates.firstOrNull() ?: return null
        val message = "Trong ${REQUIRED_SESSIONS} ngày tập riêng biệt gần nhất có check-in Guided Workout cho " +
            "${best.name}, bạn tự chọn mức nhẹ ở ${best.easySets} hiệp và vừa sức ở " +
            "${best.moderateSets} hiệp. Đây là cảm nhận tự khai báo, không đo kỹ thuật, thể lực " +
            "hay khả năng hồi phục. Chibi không tự tăng số lần, số hiệp hoặc tải; nếu muốn, " +
            "bạn có thể xem trước một bước tiến triển nhỏ rồi tự xác nhận."

        return Suggestion(
            exerciseName = best.name,
            consistentDays = REQUIRED_SESSIONS,
            easySets = best.easySets,
            moderateSets = best.moderateSets,
            latestDate = best.latestDate,
            message = message,
            prompt = "Tao muốn xem trước một bước tiến triển nhỏ cho ${best.name} dựa trên check-in tự khai báo. " +
                "Giữ số hiệp và tải hiện tại, giữ mọi hạn chế vận động đang chọn; chỉ đề xuất phương án " +
                "để tao tự xem và xác nhận, không tự áp dụng."
        )
    }

    private fun parseLog(raw: String): List<MoveEffort> {
        if (raw.isBlank() || raw.length > MAX_LOG_CHARS) return emptyList()
        return raw.split(';').take(MAX_MOVES).mapNotNull { segment ->
            val separator = segment.indexOf(':')
            if (separator !in 1..90) return@mapNotNull null
            val name = segment.substring(0, separator).trim().take(90)
            if (name.isBlank()) return@mapNotNull null

            val seenSets = mutableSetOf<Int>()
            val levels = effortPattern.findAll(segment.substring(separator + 1))
                .take(MAX_SETS)
                .mapNotNull { match ->
                    val setIndex = match.groupValues[1].toIntOrNull()
                        ?: return@mapNotNull null
                    if (setIndex !in 1..MAX_SETS || !seenSets.add(setIndex)) return@mapNotNull null
                    val label = match.groupValues[2].replace(Regex("\\s+"), " ").trim()
                    WorkoutEffortFeedback.Level.values().firstOrNull {
                        it.label.equals(label, ignoreCase = true)
                    }
                }
                .toList()
            if (levels.isEmpty()) null else MoveEffort(name, levels)
        }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed.replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
