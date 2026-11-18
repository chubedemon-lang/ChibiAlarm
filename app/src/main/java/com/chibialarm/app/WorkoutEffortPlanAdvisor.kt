package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * Opt-in, offline prompt derived only from structured self-reported effort labels.
 *
 * It does not estimate recovery, diagnose injury, or change a plan automatically.
 * Free-text workout notes and health preferences are never read.
 */
object WorkoutEffortPlanAdvisor {
    data class Suggestion(
        val exerciseName: String,
        val repeatedSessions: Int,
        val hardSets: Int,
        val veryHardSets: Int,
        val latestDate: LocalDate,
        val message: String,
        val prompt: String
    )

    private data class MoveEffort(
        val name: String,
        /** Only explicitly rated, unique set indexes are retained. */
        val levels: Map<Int, WorkoutEffortFeedback.Level>
    )

    private data class Session(
        val id: String,
        val date: LocalDate,
        val originalIndex: Int,
        val moves: List<MoveEffort>
    )

    private data class Candidate(
        val name: String,
        val repeatedSessions: Int,
        val veryHardSessions: Int,
        val hardSets: Int,
        val veryHardSets: Int,
        val latestDate: LocalDate
    )

    private const val MAX_RECORDS = 10_000
    private const val MAX_SESSIONS = 5
    private const val MAX_LOG_CHARS = 1_200
    private const val MAX_MOVES = 100
    private const val MAX_SETS = 20

    private val effortPattern = Regex(
        """hiệp\s+(\d{1,2})\s*:\s*(Nhẹ,\s*còn dư sức|Vừa sức|Khó,\s*cần nghỉ thêm|Quá sức,\s*tạm dừng)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Returns a prompt only when the same exercise was self-rated HARD/VERY_HARD
     * in at least two distinct, valid guided sessions in the recent window.
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
                    workout.minutes !in 1..600 || workout.completedMoves !in 2..MAX_MOVES ||
                    workout.selfReportedSets !in 1..(MAX_MOVES * MAX_SETS) ||
                    workout.effortLog.isBlank() || workout.effortLog.length > MAX_LOG_CHARS
                ) return@mapIndexedNotNull null

                val moves = parseLog(workout.effortLog) ?: return@mapIndexedNotNull null
                val uniqueMoveNames = moves.map { normalize(it.name) }.distinct()
                val ratedSetCount = moves.sumOf { it.levels.size }
                // Effort check-ins are valid only for a session that actually recorded
                // enough completed moves/sets. Rep values remain optional, so setLog is
                // deliberately not required for an otherwise confirmed set.
                if (moves.isEmpty() || uniqueMoveNames.size != moves.size ||
                    moves.size > workout.completedMoves || ratedSetCount > workout.selfReportedSets ||
                    moves.any { move -> move.levels.keys.any { it > workout.selfReportedSets } }
                ) return@mapIndexedNotNull null
                Session(workout.sessionId, date, index, moves)
            }
            .toList()
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.originalIndex })
            .distinctBy { it.id }
            .take(MAX_SESSIONS)

        if (sessions.size < 2) return null

        val candidates = sessions
            .flatMap { session -> session.moves.map { session to it } }
            .groupBy { normalize(it.second.name) }
            .values
            .mapNotNull { observations ->
                val bySession = observations.groupBy { it.first.id }
                val hardSessions = bySession.count { (_, entries) ->
                    entries.any { (_, move) ->
                        move.levels.values.any {
                            it == WorkoutEffortFeedback.Level.HARD ||
                                it == WorkoutEffortFeedback.Level.VERY_HARD
                        }
                    }
                }
                if (hardSessions < 2) return@mapNotNull null

                val highLevels = observations.flatMap { it.second.levels.values }.filter {
                    it == WorkoutEffortFeedback.Level.HARD ||
                        it == WorkoutEffortFeedback.Level.VERY_HARD
                }
                val veryHardSessions = bySession.count { (_, entries) ->
                    entries.any { (_, move) ->
                        WorkoutEffortFeedback.Level.VERY_HARD in move.levels.values
                    }
                }
                Candidate(
                    name = observations.first().second.name,
                    repeatedSessions = hardSessions,
                    veryHardSessions = veryHardSessions,
                    hardSets = highLevels.count { it == WorkoutEffortFeedback.Level.HARD },
                    veryHardSets = highLevels.count { it == WorkoutEffortFeedback.Level.VERY_HARD },
                    latestDate = observations
                        .filter { (_, move) -> move.levels.values.any {
                            it == WorkoutEffortFeedback.Level.HARD ||
                                it == WorkoutEffortFeedback.Level.VERY_HARD
                        } }
                        .maxOf { it.first.date }
                )
            }
            .sortedWith(
                compareByDescending<Candidate> { it.veryHardSessions }
                    .thenByDescending { it.repeatedSessions }
                    .thenByDescending { it.veryHardSets }
                    .thenByDescending { it.hardSets }
                    .thenByDescending { it.latestDate }
                    .thenBy { normalize(it.name) }
            )

        val best = candidates.firstOrNull() ?: return null
        val severity = buildList {
            if (best.hardSets > 0) add("${best.hardSets} hiệp khó")
            if (best.veryHardSets > 0) add("${best.veryHardSets} hiệp quá sức")
        }.joinToString(" và ")

        val message = "Trong ${sessions.size} buổi Guided Workout có check-in gần đây, " +
            "bạn tự đánh dấu ${best.repeatedSessions} buổi có hiệp khó hoặc quá sức ở ${best.name} " +
            "($severity). Chibi chưa tự thay đổi giáo án; nếu muốn, bạn có thể mở gợi ý một buổi nhẹ hơn. " +
            "Đây là cảm nhận tự khai báo, không phải phép đo thể lực hay đánh giá hồi phục. " +
            "Nếu có đau hoặc triệu chứng bất thường, hãy dừng tập thay vì cố hoàn thành giáo án."

        return Suggestion(
            exerciseName = best.name,
            repeatedSessions = best.repeatedSessions,
            hardSets = best.hardSets,
            veryHardSets = best.veryHardSets,
            latestDate = best.latestDate,
            message = message,
            prompt = "Tao muốn xem một buổi tập nhẹ hơn, không tự tăng số hiệp; " +
                "ưu tiên thay thế cho động tác: ${best.name}"
        )
    }

    private fun parseLog(raw: String): List<MoveEffort>? {
        if (raw.isBlank() || raw.length > MAX_LOG_CHARS) return null
        val segments = raw.split(';')
        if (segments.isEmpty() || segments.size > MAX_MOVES) return null
        val result = mutableListOf<MoveEffort>()

        for (segment in segments) {
            val separator = segment.indexOf(':')
            if (separator !in 1..90) return null
            val name = segment.substring(0, separator).trim().take(90)
            if (name.isBlank()) return null

            val payload = segment.substring(separator + 1)
            val matches = effortPattern.findAll(payload).toList()
            if (matches.isEmpty() || matches.size > MAX_SETS) return null
            val remainder = StringBuilder(payload)
            matches.asReversed().forEach { match ->
                remainder.delete(match.range.first, match.range.last + 1)
            }
            if (remainder.toString().replace(Regex("[,\\s]+"), "").isNotEmpty()) return null

            val levels = linkedMapOf<Int, WorkoutEffortFeedback.Level>()
            for (match in matches) {
                val setIndex = match.groupValues[1].toIntOrNull() ?: return null
                if (setIndex !in 1..MAX_SETS || setIndex in levels) return null
                val label = match.groupValues[2].replace(Regex("\\s+"), " ").trim()
                val level = WorkoutEffortFeedback.Level.values().firstOrNull {
                    it.label.equals(label, ignoreCase = true)
                } ?: return null
                levels[setIndex] = level
            }
            result += MoveEffort(name, levels)
        }
        return result
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
