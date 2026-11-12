package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Compares self-reported set values for one exercise across the two latest
 * guided sessions that recorded it. This is a journal comparison, not a
 * measure of technique, effort, recovery or medical readiness.
 *
 * Free-text workout notes are never read.
 */
object WorkoutSetHistoryConversation {
    private const val MAX_RECORDS = 10_000
    private const val MAX_LOG_CHARS = 1_200
    private const val MAX_MOVES = 100
    private const val MAX_SETS = 20
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val setPattern = Regex(
        """hiệp\s+(\d{1,2})\s*:\s*(\d{1,3})\s+(lần|nhịp|giây)""",
        RegexOption.IGNORE_CASE
    )
    private val stopWords = setOf(
        "bai", "dong", "tac", "tap", "buoi", "lan", "truoc", "nay", "so",
        "hiep", "bao", "nhieu", "rep", "hai", "giua", "gan", "nhat", "the",
        "nao", "tao", "minh", "toi", "vua", "qua", "sanh", "voi", "tien",
        "duoc", "lam", "moi", "mot"
    )

    private data class SetValue(val index: Int, val value: Int, val unit: String)
    private data class MoveLog(val name: String, val sets: List<SetValue>)
    private data class Session(
        val date: LocalDate,
        val originalIndex: Int,
        val moves: List<MoveLog>
    )
    private data class MoveChoice(val name: String?, val ambiguous: List<String> = emptyList())

    fun answer(
        input: String,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): String? {
        val query = normalize(input.take(300))
        if (!isSetQuestion(query)) return null

        val sessions = workouts.takeLast(MAX_RECORDS)
            .mapIndexedNotNull { index, workout ->
                val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val validSessionId = runCatching {
                    UUID.fromString(workout.sessionId).toString() == workout.sessionId
                }.getOrDefault(false)
                if (date.isAfter(today) || workout.minutes !in 1..600 ||
                    !validSessionId || workout.setLog.isBlank() ||
                    workout.setLog.length > MAX_LOG_CHARS
                ) return@mapIndexedNotNull null

                val moves = parseMoves(workout.setLog)
                if (moves.isEmpty()) return@mapIndexedNotNull null
                Session(date, index, moves)
            }
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.originalIndex })

        val knownNames = sessions.flatMap { it.moves }
            .map { it.name }
            .distinctBy { normalize(it) }
        if (knownNames.isEmpty()) {
            return "Chưa có chi tiết hiệp tự khai báo trong nhật ký để so sánh. Hãy lưu một buổi Guided Workout có xác nhận hiệp trước nhé."
        }

        val choice = chooseMove(query, knownNames)
        if (choice.ambiguous.isNotEmpty()) {
            return "Chibi tìm thấy vài động tác gần giống: " + choice.ambiguous.joinToString(", ") +
                ". Hãy nhắc đầy đủ tên bài để tránh so sánh nhầm."
        }
        val selectedName = choice.name
            ?: return "Mày hãy nhắc tên động tác muốn so sánh, ví dụ: “So sánh Squat không tạ giữa hai buổi gần nhất”."
        val normalizedName = normalize(selectedName)

        val matching = sessions.mapNotNull { session ->
            session.moves.firstOrNull { normalize(it.name) == normalizedName }
                ?.let { session to it }
        }
        if (matching.isEmpty()) {
            return "Chưa tìm thấy chi tiết hiệp cho " + selectedName + " trong nhật ký Guided Workout."
        }

        val latest = matching[0]
        if (matching.size == 1) {
            return "Mới tìm thấy một buổi có dữ liệu cho " + selectedName +
                " (" + latest.first.date.format(dateFormat) + "): " + formatSets(latest.second.sets) +
                ". Chưa đủ hai buổi để so sánh; các giá trị này do bạn tự khai báo."
        }

        val previous = matching[1]
        val latestSets = latest.second.sets.associateBy { it.index }
        val previousSets = previous.second.sets.associateBy { it.index }
        val setIndices = (latestSets.keys + previousSets.keys).sorted()
        val lines = setIndices.map { index ->
            val current = latestSets[index]
            val prior = previousSets[index]
            when {
                current != null && prior != null && current.unit == prior.unit -> {
                    val difference = current.value - prior.value
                    val delta = when {
                        difference > 0 -> "+" + difference + " " + current.unit
                        difference < 0 -> difference.toString() + " " + current.unit
                        else -> "không đổi"
                    }
                    "Hiệp " + index + ": " + current.value + " " + current.unit +
                        " so với " + prior.value + " " + prior.unit + " (" + delta + ")"
                }
                current != null && prior != null ->
                    "Hiệp " + index + ": " + current.value + " " + current.unit +
                        " so với " + prior.value + " " + prior.unit + " (không so sánh vì khác đơn vị)"
                current != null ->
                    "Hiệp " + index + ": " + current.value + " " + current.unit + " (buổi trước chưa ghi)"
                else ->
                    "Hiệp " + index + ": " + prior!!.value + " " + prior.unit + " (buổi mới nhất chưa ghi)"
            }
        }

        return buildString {
            append("Đối chiếu " + selectedName + " trong hai buổi gần nhất có ghi hiệp:")
            append("\n• Mới nhất: " + latest.first.date.format(dateFormat))
            append("\n• Trước đó: " + previous.first.date.format(dateFormat))
            lines.forEach { append("\n• " + it) }
            append("\nSố liệu do bạn tự khai báo; so sánh này không đánh giá kỹ thuật, cường độ hay mức độ hồi phục.")
        }
    }

    private fun parseMoves(raw: String): List<MoveLog> =
        raw.take(MAX_LOG_CHARS).split(';').take(MAX_MOVES).mapNotNull { segment ->
            val separator = segment.indexOf(':')
            if (separator !in 1..80) return@mapNotNull null
            val name = segment.substring(0, separator).trim().take(80)
            if (name.isBlank()) return@mapNotNull null
            val body = segment.substring(separator + 1)
            val matches = setPattern.findAll(body).take(MAX_SETS + 1).toList()
            if (matches.isEmpty() || matches.size > MAX_SETS) return@mapNotNull null
            val residue = setPattern.replace(body, "").replace(",", "").trim()
            if (residue.isNotEmpty()) return@mapNotNull null

            val values = matches.mapNotNull { match ->
                val index = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val value = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val unit = match.groupValues[3].lowercase(Locale.ROOT)
                val maximum = if (unit == "giây") 600 else 200
                if (index !in 1..MAX_SETS || value !in 1..maximum) return@mapNotNull null
                SetValue(index, value, unit)
            }
            if (values.size != matches.size || values.map { it.index }.distinct().size != values.size) {
                return@mapNotNull null
            }
            MoveLog(name, values.sortedBy { it.index })
        }

    private fun chooseMove(query: String, names: List<String>): MoveChoice {
        val scored = names.map { name ->
            val normalizedName = normalize(name)
            val score = if (normalizedName.isNotBlank() && query.contains(normalizedName)) {
                10_000 + normalizedName.length
            } else {
                val tokens = normalizedName.split(' ')
                    .filter { it.length >= 4 && it !in stopWords }
                    .distinct()
                val matched = tokens.filter { containsToken(query, it) }
                if (matched.isEmpty()) 0 else matched.size * 100 + matched.sumOf { it.length }
            }
            name to score
        }.filter { it.second > 0 }

        val bestScore = scored.maxOfOrNull { it.second } ?: return MoveChoice(null)
        val best = scored.filter { it.second == bestScore }.map { it.first }
        return if (best.size == 1) MoveChoice(best.single()) else MoveChoice(null, best)
    }

    private fun containsToken(text: String, token: String): Boolean =
        (" " + text + " ").contains(" " + token + " ")

    private fun isSetQuestion(text: String): Boolean =
        listOf(
            "so sanh", "so voi", "tien bo", "lan truoc", "lan nay",
            "hai buoi", "2 buoi", "bao nhieu lan", "may lan",
            "bao nhieu rep", "so rep", "so hiep", "may hiep"
        ).any { text.contains(it) }

    private fun formatSets(sets: List<SetValue>): String =
        sets.joinToString(", ") { "hiệp " + it.index + ": " + it.value + " " + it.unit }

    private fun normalize(input: String): String {
        val withoutDiacritics = Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return withoutDiacritics
            .replace(Regex("\\p{M}+"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
