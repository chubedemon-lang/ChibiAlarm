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
        val levels: List<WorkoutEffortFeedback.Level>
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
        val sessions = workouts.asSequence()
            .takeLast(MAX_RECORDS)
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

                val moves = parseLog(workout.effortLog)
                    .distinctBy { normalize(it.name) }
                if (moves.isEmpty()) return@mapIndexedNotNull null
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
                        move.levels.any {
                            it == WorkoutEffortFeedback.Level.HARD ||
                                it == WorkoutEffortFeedback.Level.VERY_HARD
                        }
                    }
                }
                if (hardSessions < 2) return@mapNotNull null

                val highLevels = observations.flatMap { it.second.levels }.filter {
                    it == WorkoutEffortFeedback.Level.HARD ||
                        it == WorkoutEffortFeedback.Level.VERY_HARD
                }
                val veryHardSessions = bySession.count { (_, entries) ->
                    entries.any { (_, move) ->
                        WorkoutEffortFeedback.Level.VERY_HARD in move.levels
                    }
                }
                Candidate(
                    name = observations.first().second.name,
                    repeatedSessions = hardSessions,
                    veryHardSessions = veryHardSessions,
                    hardSets = highLevels.count { it == WorkoutEffortFeedback.Level.HARD },
                    veryHardSets = highLevels.count { it == WorkoutEffortFeedback.Level.VERY_HARD },
                    latestDate = observations
                        .filter { (_, move) -> move.levels.any {
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
            "bạn tự đánh dấu ${best.repeatedSessions} buổi có hiệp khó ở ${best.name} " +
            "($severity). Nếu hôm nay bạn muốn, Chibi có thể mở gợi ý một buổi nhẹ hơn. " +
            "Đây là cảm nhận tự khai báo, không phải phép đo thể lực hay đánh giá hồi phục. " +
            "Nếu có đau hoặc triệu chứng bất thường, hãy dừng tập thay vì cố hoàn thành giáo án."

        return Suggestion(
            exerciseName = best.name,
            repeatedSessions = best.repeatedSessions,
            hardSets = best.hardSets,
            veryHardSets = best.veryHardSets,
            latestDate = best.latestDate,
            message = message,
            prompt = "Hôm nay tao hơi mệt, muốn một buổi tập nhẹ hơn, " +
                "ưu tiên vận động nhẹ và không tự tăng số hiệp."
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
