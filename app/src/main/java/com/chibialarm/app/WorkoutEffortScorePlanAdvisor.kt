package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * Opt-in next-session prompt derived only from exact numeric perceived-effort self-ratings.
 *
 * It requires two most-recent, distinct guided sessions with at least two rated sets each
 * and an average of 7.5/10 or higher in both. It never reads free-text notes, diagnoses
 * recovery, or edits a plan. Scores remain self-reports, not sensor measurements.
 */
object WorkoutEffortScorePlanAdvisor {
    data class Suggestion(
        val latestAverage: Double,
        val previousAverage: Double,
        val latestRatedSets: Int,
        val previousRatedSets: Int,
        val latestDate: LocalDate,
        val previousDate: LocalDate,
        val message: String,
        val prompt: String
    )

    private data class SessionScore(
        val date: LocalDate,
        val originalIndex: Int,
        val sessionId: String,
        val average: Double,
        val ratedSets: Int
    )

    private const val MAX_RECORDS = 10_000
    private const val MAX_LOG_CHARS = 1_200
    private const val MAX_MOVES = 500
    private const val MAX_SETS_PER_MOVE = 20
    private const val REQUIRED_SESSIONS = 2
    private const val MIN_RATED_SETS_PER_SESSION = 2
    private const val MIN_SESSION_AVERAGE = 7.5

    /** Returns null unless the two most recent valid sessions both meet the conservative gate. */
    fun suggest(
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now(),
        lookbackDays: Int = 7
    ): Suggestion? {
        require(lookbackDays in 1..30) { "Lookback must be between 1 and 30 days" }
        val firstDay = today.minusDays(lookbackDays - 1L)
        val sessions = workouts.takeLast(MAX_RECORDS)
            .mapIndexedNotNull { index, workout ->
                val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val validId = runCatching {
                    UUID.fromString(workout.sessionId).toString() == workout.sessionId
                }.getOrDefault(false)
                if (!validId || date.isBefore(firstDay) || date.isAfter(today) ||
                    workout.minutes !in 1..600 || workout.completedMoves !in 2..MAX_MOVES ||
                    workout.selfReportedSets !in MIN_RATED_SETS_PER_SESSION..2_000 ||
                    workout.effortScoreLog.isBlank() || workout.effortScoreLog.length > MAX_LOG_CHARS
                ) return@mapIndexedNotNull null

                val scores = parseScores(workout.effortScoreLog) ?: return@mapIndexedNotNull null
                if (scores.size !in MIN_RATED_SETS_PER_SESSION..workout.selfReportedSets) {
                    return@mapIndexedNotNull null
                }
                SessionScore(
                    date = date,
                    originalIndex = index,
                    sessionId = workout.sessionId,
                    average = scores.average(),
                    ratedSets = scores.size
                )
            }
            .sortedWith(compareByDescending<SessionScore> { it.date }
                .thenByDescending { it.originalIndex })
            .distinctBy { it.sessionId }
            .take(REQUIRED_SESSIONS)

        if (sessions.size != REQUIRED_SESSIONS) return null
        val latest = sessions[0]
        val previous = sessions[1]
        if (latest.average < MIN_SESSION_AVERAGE || previous.average < MIN_SESSION_AVERAGE) return null

        val message = "Hai buổi Guided Workout hợp lệ gần nhất trong $lookbackDays ngày có điểm gắng sức " +
            "trung bình tự chấm ${formatScore(latest.average)}/10 (${latest.ratedSets} hiệp có điểm) và " +
            "${formatScore(previous.average)}/10 (${previous.ratedSets} hiệp có điểm). Chibi có thể gợi ý " +
            "một buổi nhẹ hơn để bạn xem trước. Đây là cảm nhận tự khai báo, không phải phép đo thể lực " +
            "hay khả năng hồi phục; Chibi không tự thay đổi giáo án."
        val prompt = "Tao muốn xem bản nháp buổi tập nhẹ hơn dựa trên điểm gắng sức tự chấm; " +
            "giữ nguyên hiệp đã hoàn thành, không tự tăng số hiệp, số lần hoặc tải."

        return Suggestion(
            latestAverage = latest.average,
            previousAverage = previous.average,
            latestRatedSets = latest.ratedSets,
            previousRatedSets = previous.ratedSets,
            latestDate = latest.date,
            previousDate = previous.date,
            message = message,
            prompt = prompt
        )
    }

    /** Handles only an explicit request to preview a lighter session based on numeric scores. */
    fun answer(
        input: String,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): String? {
        if (input.isBlank() || input.length > 300) return null
        val query = normalize(input)
        val asksForScore = query.contains("diem gang suc") ||
            query.contains("diem tu danh gia") ||
            Regex("\\brpe\\b").containsMatchIn(query)
        val asksForLighterPlan = listOf(
            "buoi nhe hon", "buoi tap nhe hon", "tap nhe hon", "giam tai",
            "goi y buoi nhe", "de xuat buoi nhe"
        ).any(query::contains)
        if (!asksForScore || !asksForLighterPlan) return null

        val suggestion = suggest(workouts, today)
            ?: return "Chưa đủ dữ liệu để gợi ý buổi nhẹ hơn theo điểm số: cần hai buổi Guided Workout " +
                "riêng biệt gần nhất trong 7 ngày, mỗi buổi có ít nhất hai hiệp được tự chấm và điểm " +
                "trung bình của cả hai buổi từ 7,5/10 trở lên. Chibi không đọc ghi chú riêng tư và không " +
                "tự thay đổi giáo án."
        return suggestion.message + "\n\nNếu muốn xem bản nháp, hãy nói: “${suggestion.prompt}”. " +
            "Chibi chưa thay đổi giáo án; mọi thay đổi vẫn cần bạn xem trước và xác nhận."
    }

    /** Malformed, duplicate, stale and out-of-range entries invalidate the whole session. */
    private fun parseScores(raw: String): List<Int>? {
        if (raw.isBlank() || raw.length > MAX_LOG_CHARS) return null
        val entries = raw.split(',')
        if (entries.isEmpty() || entries.size > MAX_MOVES) return null
        val seenMoves = mutableSetOf<Int>()
        val scores = mutableListOf<Int>()

        for (entry in entries) {
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator != entry.lastIndexOf(':')) return null
            val moveIndex = entry.substring(0, separator).toIntOrNull() ?: return null
            if (moveIndex !in 0 until MAX_MOVES || !seenMoves.add(moveIndex)) return null

            val tokens = entry.substring(separator + 1).split('.')
            if (tokens.isEmpty() || tokens.size > MAX_SETS_PER_MOVE) return null
            var ratedInMove = 0
            for (token in tokens) {
                if (token == "-") continue
                val score = token.toIntOrNull() ?: return null
                if (score !in 1..10) return null
                scores += score
                ratedInMove++
            }
            if (ratedInMove == 0) return null
        }
        return scores.takeIf { it.size >= MIN_RATED_SETS_PER_SESSION }
    }

    private fun formatScore(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

    private fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed.replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
            .trim()
    }
}
