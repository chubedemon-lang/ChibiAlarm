package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Describes a three-session trend for one exercise using only explicitly self-reported set values.
 *
 * It compares only set indexes present in all three sessions and only when the unit matches.
 * Free-text notes are never read. The result is descriptive coaching context, not a fitness,
 * technique, recovery, or medical assessment, and it never changes a plan.
 */
internal object WorkoutSetTrendConversation {
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
        "bai", "tap", "dong", "tac", "qua", "buoi", "gan", "nhat", "the", "nao",
        "xu", "huong", "tien", "bo", "tang", "giam", "so", "sanh", "ba", "toi",
        "tao", "minh", "vua", "roi", "nhung", "cac"
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
        if (!isTrendQuestion(query)) return null

        val sessions = workouts.takeLast(MAX_RECORDS)
            .mapIndexedNotNull { index, workout ->
                val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val validSessionId = runCatching {
                    UUID.fromString(workout.sessionId).toString() == workout.sessionId
                }.getOrDefault(false)
                if (date.isAfter(today) || workout.minutes !in 1..600 || !validSessionId) {
                    return@mapIndexedNotNull null
                }
                val moves = if (workout.setLog.length <= MAX_LOG_CHARS) {
                    parseMoves(workout.setLog)
                } else {
                    emptyList()
                }
                Session(date, index, moves)
            }
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.originalIndex })

        val knownNames = sessions.flatMap { it.moves }
            .map { it.name }
            .distinctBy(::normalize)
        if (knownNames.isEmpty()) {
            return "Chưa có chi tiết hiệp tự khai báo hợp lệ để xem xu hướng. Hãy lưu ít nhất ba buổi Guided Workout có ghi số liệu."
        }

        val choice = chooseMove(query, knownNames)
        if (choice.ambiguous.isNotEmpty()) {
            return "Chibi tìm thấy vài động tác gần giống: " + choice.ambiguous.joinToString(", ") +
                ". Hãy nhắc đầy đủ tên bài để tránh so sánh nhầm."
        }
        val selectedName = choice.name
            ?: return "Mày hãy nêu đầy đủ tên động tác muốn xem xu hướng qua ba buổi, ví dụ: “Xu hướng Squat không tạ qua 3 buổi gần nhất”."
        val key = normalize(selectedName)

        // A duplicated exercise name inside one session is ambiguous; skip that session
        // rather than choosing an arbitrary entry or counting it twice.
        val matching = sessions.mapNotNull { session ->
            val moves = session.moves.filter { normalize(it.name) == key }
            moves.singleOrNull()?.let { session to it }
        }.take(3).reversed()

        if (matching.size < 3) {
            return "Mới tìm thấy ${matching.size} buổi có chi tiết hiệp cho ${selectedName}; cần ba buổi riêng biệt để xem xu hướng. Chibi không lấy mục tiêu giáo án hoặc ghi chú riêng tư để bù số liệu."
        }

        val setMaps = matching.map { (_, move) -> move.sets.associateBy { it.index } }
        val commonIndexes = setMaps.map { it.keys }.reduce { common, indexes -> common intersect indexes }
            .sorted()
        if (commonIndexes.isEmpty()) {
            return "Ba buổi có ${selectedName}, nhưng không có hiệp cùng chỉ số ở cả ba buổi để so sánh an toàn. Chibi không tự ghép các hiệp khác vị trí."
        }

        val comparableIndexes = commonIndexes.filter { index ->
            setMaps.map { it.getValue(index).unit }.distinct().size == 1
        }
        if (comparableIndexes.isEmpty()) {
            return "Ba buổi có ${selectedName}, nhưng đơn vị của các hiệp trùng chỉ số không nhất quán. Chibi không quy đổi lần, nhịp hoặc giây."
        }

        val groups = comparableIndexes.groupBy { setMaps.first().getValue(it).unit }
            .toSortedMap()
        val lines = groups.map { (unit, indexes) ->
            val totals = setMaps.map { sets ->
                indexes.sumOf { index -> sets.getValue(index).value }
            }
            val trend = describeTrend(totals)
            val values = totals.joinToString(" → ") { "${it} ${unit}" }
            val dates = matching.joinToString(" → ") { (session, _) ->
                session.date.format(dateFormat)
            }
            "• ${unit}: " + values + " (${trend}; ${dates}; so sánh hiệp ${indexes.joinToString(", ")})"
        }

        return buildString {
            append("Xu hướng số liệu tự khai báo của ${selectedName} qua ba buổi có ghi bài này:")
            append("\n")
            append(lines.joinToString("\n"))
            if (comparableIndexes.size < commonIndexes.size) {
                append("\\nMột số hiệp trùng chỉ số bị bỏ qua vì đơn vị không khớp.")
            }
            append("\\nChỉ so sánh hiệp cùng chỉ số và cùng đơn vị; hiệp thiếu dữ liệu không được ước tính. ")
            append("Đây là nhật ký tự khai báo, không đánh giá kỹ thuật, tải tập hay khả năng hồi phục. ")
            append("Chibi không tự tăng mục tiêu hoặc thay đổi giáo án; mọi điều chỉnh cần bản xem trước và xác nhận.")
        }
    }

    private fun isTrendQuestion(query: String): Boolean {
        val asksThreeSessions = listOf(
            "3 buoi", "ba buoi", "3 buoi gan nhat", "ba buoi gan nhat",
            "qua 3 buoi", "qua ba buoi"
        ).any(query::contains)
        val asksTrend = listOf(
            "xu huong", "so sanh", "tien bo", "tang giam", "tang hay giam", "thay doi"
        ).any(query::contains)
        return asksThreeSessions && asksTrend
    }

    private fun parseMoves(raw: String): List<MoveLog> {
        if (raw.isBlank() || raw.length > MAX_LOG_CHARS) return emptyList()
        return raw.split(';').take(MAX_MOVES).mapNotNull segment@{ segment ->
            val separator = segment.indexOf(':')
            if (separator !in 1..80) return@segment null
            val name = segment.substring(0, separator).trim().take(80)
            if (name.isBlank()) return@segment null
            val body = segment.substring(separator + 1)
            val matches = setPattern.findAll(body).take(MAX_SETS + 1).toList()
            if (matches.isEmpty() || matches.size > MAX_SETS) return@segment null
            val residue = setPattern.replace(body, "").replace(",", "").trim()
            if (residue.isNotEmpty()) return@segment null

            val values = matches.mapNotNull value@{ match ->
                val index = match.groupValues[1].toIntOrNull() ?: return@value null
                val value = match.groupValues[2].toIntOrNull() ?: return@value null
                val unit = match.groupValues[3].lowercase(Locale.ROOT)
                val maximum = if (unit == "giây") 600 else 200
                if (index !in 1..MAX_SETS || value !in 1..maximum) return@value null
                SetValue(index, value, unit)
            }
            if (values.size != matches.size || values.map { it.index }.distinct().size != values.size) {
                return@segment null
            }
            MoveLog(name, values.sortedBy { it.index })
        }
    }

    private fun chooseMove(query: String, names: List<String>): MoveChoice {
        val scored = names.map { name ->
            val normalized = normalize(name)
            val score = if (normalized.isNotBlank() && query.contains(normalized)) {
                10_000 + normalized.length
            } else {
                val tokens = normalized.split(' ')
                    .filter { it.length >= 4 && it !in stopWords }
                    .distinct()
                val matched = tokens.filter { token ->
                    (" " + query + " ").contains(" " + token + " ")
                }
                if (matched.isEmpty()) 0 else matched.size * 100 + matched.sumOf { it.length }
            }
            name to score
        }.filter { it.second > 0 }

        val bestScore = scored.maxOfOrNull { it.second } ?: return MoveChoice(null)
        val best = scored.filter { it.second == bestScore }.map { it.first }.distinctBy(::normalize)
        return if (best.size == 1) MoveChoice(best.single()) else MoveChoice(null, best)
    }

    private fun describeTrend(values: List<Int>): String {
        val first = values[0]
        val middle = values[1]
        val latest = values[2]
        return when {
            first == middle && middle == latest -> "ổn định"
            first < middle && middle < latest -> "tăng đều"
            first > middle && middle > latest -> "giảm đều"
            first <= middle && middle <= latest -> "không giảm, có tăng hoặc giữ ổn định"
            first >= middle && middle >= latest -> "không tăng, có giảm hoặc giữ ổn định"
            else -> "dao động"
        }
    }

    private fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed.replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
