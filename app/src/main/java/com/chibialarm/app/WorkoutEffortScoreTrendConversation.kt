package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Read-only trend summary for exact, numeric perceived-effort self-ratings.
 *
 * The journal stores scores by move index, not by a durable catalog name, so this summary is
 * deliberately session-wide. It never guesses which exercise an index represents and never
 * reads free-text notes. A trend is descriptive only; it is not a recovery or medical assessment.
 */
object WorkoutEffortScoreTrendConversation {
    private const val MAX_RECORDS = 10_000
    private const val MAX_LOG_CHARS = 1_200
    private const val MAX_MOVES = 500
    private const val MAX_SETS_PER_MOVE = 20
    private const val MAX_SESSIONS = 5
    private const val LOOKBACK_DAYS = 30
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    private data class SessionScore(
        val date: LocalDate,
        val originalIndex: Int,
        val sessionId: String,
        val average: Double,
        val ratedSets: Int
    )

    /** Returns null for ordinary chat so the existing coach/history routes can handle it. */
    fun answer(
        input: String,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): String? {
        val query = normalize(input.take(300))
        if (!isScoreHistoryQuestion(query)) return null

        val firstDay = today.minusDays(LOOKBACK_DAYS - 1L)
        val sessions = workouts.takeLast(MAX_RECORDS)
            .mapIndexedNotNull { index, workout ->
                val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val validId = runCatching {
                    UUID.fromString(workout.sessionId).toString() == workout.sessionId
                }.getOrDefault(false)
                if (!validId || date.isBefore(firstDay) || date.isAfter(today) ||
                    workout.minutes !in 1..600 || workout.completedMoves !in 1..MAX_MOVES ||
                    workout.selfReportedSets !in 1..2_000 ||
                    workout.effortScoreLog.isBlank() ||
                    workout.effortScoreLog.length > MAX_LOG_CHARS
                ) return@mapIndexedNotNull null

                val scores = parseScores(workout.effortScoreLog) ?: return@mapIndexedNotNull null
                if (scores.isEmpty() || scores.size > workout.selfReportedSets) {
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
            // A duplicated journal row must never manufacture an extra comparison point.
            .distinctBy { it.sessionId }
            .take(MAX_SESSIONS)

        if (sessions.isEmpty()) {
            return "Chưa có điểm gắng sức số 1–10 hợp lệ trong nhật ký 30 ngày gần nhất. " +
                "Các check-in dạng nhãn như “Khó” không được chuyển ngược thành điểm số."
        }

        val latest = sessions.first()
        val lines = sessions.take(3).map { session ->
            "• ${session.date.format(dateFormat)}: ${formatScore(session.average)}/10 " +
                "(${session.ratedSets} hiệp có điểm)"
        }
        val comparison = sessions.getOrNull(1)?.let { previous ->
            val difference = latest.average - previous.average
            val trend = when {
                difference >= 1.0 -> "cao hơn ${formatScore(difference)} điểm"
                difference <= -1.0 -> "thấp hơn ${formatScore(-difference)} điểm"
                else -> "chưa thay đổi rõ (chênh ${formatScore(kotlin.math.abs(difference))} điểm, dưới 1 điểm)"
            }
            "So với buổi trước (${previous.date.format(dateFormat)}), điểm trung bình tự chấm ở buổi mới nhất " +
                "${trend}. Đây chỉ là thay đổi trong câu trả lời tự đánh giá."
        } ?: "Mới có một buổi hợp lệ; chưa đủ hai buổi để nhận xét xu hướng."

        return buildString {
            append("Điểm gắng sức tự đánh giá theo từng buổi trong 30 ngày gần nhất (trung bình các hiệp có chấm điểm):")
            append("\n").append(lines.joinToString("\n"))
            append("\n").append(comparison)
            append("\nĐiểm do bạn tự chấm, không phải phép đo thể lực, kỹ thuật hay khả năng hồi phục. " +
                "Chibi không tự giảm/tăng giáo án từ xu hướng này; nếu thấy đau, chóng mặt hoặc khó thở bất thường, hãy dừng tập và tìm trợ giúp phù hợp.")
        }
    }

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
            for (token in tokens) {
                if (token == "-") continue
                val score = token.toIntOrNull() ?: return null
                if (score !in 1..10) return null
                scores += score
            }
        }
        return scores.takeIf { it.isNotEmpty() }
    }

    private fun isScoreHistoryQuestion(query: String): Boolean {
        val mentionsScore = query.contains("diem gang suc") ||
            query.contains("diem tu danh gia") ||
            query.contains("diem rpe") ||
            Regex("\\\\brpe\\\\b").containsMatchIn(query) ||
            query.contains("diem 1 10") || query.contains("thang diem 1 10")
        if (!mentionsScore) return false
        return listOf(
            "gan day", "buoi gan nhat", "buoi moi nhat", "buoi truoc", "buoi vua roi",
            "xu huong", "tang hay giam", "so sanh", "trung binh", "nhat ky", "lich su",
            "hom nay", "buoi tap", "lan tap"
        ).any(query::contains)
    }

    private fun formatScore(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

    private fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed.replace(Regex("\\\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\\\s+"), " ")
            .trim()
    }
}
