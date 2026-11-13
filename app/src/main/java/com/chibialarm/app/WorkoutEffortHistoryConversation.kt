package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Answers questions about explicitly self-reported perceived effort from the local workout journal.
 * It reads only the bounded structured effortLog field, never free-text notes or safety preferences.
 * The labels describe what the user selected; they are not measurements of recovery or fitness.
 */
object WorkoutEffortHistoryConversation {
    private const val MAX_RECORDS = 10_000
    private const val MAX_LOG_CHARS = 1_200
    private const val MAX_MOVES = 100
    private const val MAX_SETS = 20
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val effortPattern = Regex(
        """hiệp\s+(\d{1,2})\s*:\s*(Nhẹ,\s*còn dư sức|Vừa sức|Khó,\s*cần nghỉ thêm|Quá sức,\s*tạm dừng)""",
        RegexOption.IGNORE_CASE
    )
    private val stopWords = setOf(
        "buoi", "tap", "lan", "gan", "nhat", "truoc", "nay", "the", "nao",
        "tao", "minh", "toi", "cam", "nhan", "thay", "muc", "do", "gang",
        "suc", "so", "sanh", "hiep", "bai", "dong", "tac", "kho", "de",
        "qua", "nhe", "vua", "roi", "cua", "o", "la", "khong", "giua",
        "hai", "hom"
    )

    private data class SetEffort(val index: Int, val level: WorkoutEffortFeedback.Level)
    private data class MoveEffort(val name: String, val sets: List<SetEffort>)
    private data class Session(
        val date: LocalDate,
        val originalIndex: Int,
        val moves: List<MoveEffort>
    )
    private data class MoveChoice(val name: String?, val ambiguous: List<String> = emptyList())

    fun answer(
        input: String,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): String? {
        val query = normalize(input.take(300))
        if (!isEffortQuestion(query)) return null

        val sessions = workouts.takeLast(MAX_RECORDS)
            .mapIndexedNotNull { index, workout ->
                val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val validSessionId = runCatching {
                    UUID.fromString(workout.sessionId).toString() == workout.sessionId
                }.getOrDefault(false)
                if (date.isAfter(today) || workout.minutes !in 1..600 || !validSessionId ||
                    workout.effortLog.isBlank() || workout.effortLog.length > MAX_LOG_CHARS
                ) return@mapIndexedNotNull null

                val moves = parseLog(workout.effortLog)
                if (moves.isEmpty()) return@mapIndexedNotNull null
                Session(date, index, moves)
            }
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.originalIndex })

        if (sessions.isEmpty()) {
            return "Chưa có check-in cảm nhận hợp lệ trong nhật ký trên máy. Hãy hoàn thành và lưu một buổi Guided Workout có chọn mức độ gắng sức trước nhé."
        }

        if (query.contains("qua suc")) {
            val events = sessions.flatMap { session ->
                session.moves.flatMap { move ->
                    move.sets.filter { it.level == WorkoutEffortFeedback.Level.VERY_HARD }
                        .map { Triple(session, move, it) }
                }
            }.take(5)
            if (events.isEmpty()) {
                return "Chưa tìm thấy hiệp nào được bạn tự đánh dấu “Quá sức, tạm dừng” trong các check-in đã lưu. Điều này không chứng minh rằng các buổi tập đều nhẹ hoặc an toàn."
            }
            val lines = events.map { event ->
                "• " + event.first.date.format(dateFormat) + " — " +
                    event.second.name + ", hiệp " + event.third.index
            }
            return "Các lần bạn tự đánh dấu quá sức trong nhật ký:\n" +
                lines.joinToString("\n") +
                "\nĐây là lựa chọn tự khai báo tại thời điểm tập, không phải phép đo hay đánh giá y tế."
        }

        val names = sessions.flatMap { it.moves }.map { it.name }.distinctBy { normalize(it) }
        val choice = chooseMove(query, names)
        if (choice.ambiguous.isNotEmpty()) {
            return "Chibi tìm thấy vài động tác gần giống: " + choice.ambiguous.joinToString(", ") +
                ". Hãy nhắc đầy đủ tên bài để tránh đọc nhầm lịch sử."
        }

        val selectedName = choice.name
        if (selectedName == null) {
            val latest = sessions.first()
            val lines = latest.moves.take(5).map { move ->
                "• " + move.name + ": " + formatSets(move.sets)
            }
            return "Check-in cảm nhận gần nhất (" + latest.date.format(dateFormat) + "):\n" +
                lines.joinToString("\n") +
                "\nMức độ do bạn tự chọn; nhật ký không đo thể lực, kỹ thuật hay khả năng hồi phục."
        }

        val matching = sessions.mapNotNull { session ->
            session.moves.firstOrNull { normalize(it.name) == normalize(selectedName) }
                ?.let { session to it }
        }
        if (matching.isEmpty()) {
            return "Chưa tìm thấy check-in cảm nhận cho " + selectedName + " trong nhật ký Guided Workout."
        }

        val latest = matching[0]
        if (isComparisonQuestion(query)) {
            val previous = matching.getOrNull(1)
                ?: return "Mới tìm thấy một buổi có check-in cảm nhận cho " + selectedName +
                    " (" + latest.first.date.format(dateFormat) + "). Chưa đủ hai buổi để đối chiếu."
            return "Đối chiếu cảm nhận tự khai báo cho " + selectedName + ":\n" +
                "• Buổi mới nhất (" + latest.first.date.format(dateFormat) + "): " +
                formatSets(latest.second.sets) + "\n" +
                "• Buổi trước (" + previous.first.date.format(dateFormat) + "): " +
                formatSets(previous.second.sets) + "\n" +
                "Các nhãn chỉ phản ánh câu trả lời của bạn, không cho biết mức hồi phục hay tiến bộ thể lực."
        }

        return "Check-in gần nhất cho " + selectedName + " (" +
            latest.first.date.format(dateFormat) + "): " + formatSets(latest.second.sets) +
            ". Đây là cảm nhận do bạn tự khai báo, không phải phép đo tự động."
    }

    private fun parseLog(raw: String): List<MoveEffort> {
        if (raw.isBlank() || raw.length > MAX_LOG_CHARS) return emptyList()
        return raw.split(';').take(MAX_MOVES).mapNotNull { segment ->
            val separator = segment.indexOf(':')
            if (separator !in 1..90) return@mapNotNull null
            val name = segment.substring(0, separator).trim().take(90)
            if (name.isBlank()) return@mapNotNull null

            val seen = mutableSetOf<Int>()
            val sets = effortPattern.findAll(segment.substring(separator + 1))
                .mapNotNull { match ->
                    val index = match.groupValues[1].toIntOrNull()
                        ?: return@mapNotNull null
                    if (index !in 1..MAX_SETS || !seen.add(index)) return@mapNotNull null
                    val label = match.groupValues[2].replace(Regex("\\s+"), " ").trim()
                    val level = WorkoutEffortFeedback.Level.values().firstOrNull {
                        it.label.equals(label, ignoreCase = true)
                    } ?: return@mapNotNull null
                    SetEffort(index, level)
                }.toList()
            if (sets.isEmpty()) null else MoveEffort(name, sets.sortedBy { it.index })
        }
    }

    private fun chooseMove(query: String, names: List<String>): MoveChoice {
        val queryWords = normalize(query).split(' ')
            .filter { it.isNotBlank() && it !in stopWords }
            .toSet()
        if (queryWords.isEmpty()) return MoveChoice(null)
        val scored = names.distinctBy { normalize(it) }.map { name ->
            val nameWords = normalize(name).split(' ')
                .filter { it.isNotBlank() && it !in stopWords }
                .toSet()
            name to nameWords.count { it in queryWords }
        }
        val best = scored.maxOfOrNull { it.second } ?: 0
        if (best <= 0) return MoveChoice(null)
        val matches = scored.filter { it.second == best }.map { it.first }.distinct()
        return if (matches.size == 1) MoveChoice(matches.single())
        else MoveChoice(null, matches.take(5))
    }

    private fun formatSets(sets: List<SetEffort>): String =
        sets.sortedBy { it.index }.joinToString(", ") {
            "hiệp " + it.index + ": " + it.level.label
        }

    private fun isEffortQuestion(text: String): Boolean = listOf(
        "cam nhan", "cam thay", "muc do gang suc", "gang suc", "qua suc",
        "hiep nao", "so sanh cam nhan", "so sanh muc do",
        "lan truoc kho", "lan truoc thay", "buoi truoc kho", "buoi truoc thay",
        "kho hay de", "de hay kho"
    ).any { text.contains(it) }

    private fun isComparisonQuestion(text: String): Boolean =
        text.contains("so sanh") || text.contains("hai buoi") ||
            (text.contains("lan truoc") && text.contains("lan nay"))

    private fun normalize(input: String): String {
        val withoutDiacritics = Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return withoutDiacritics
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
