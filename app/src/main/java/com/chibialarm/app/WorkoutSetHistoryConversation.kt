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
    private const val MAX_DISPLAY_MOVES = 8
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
    private data class RepProgress(
        val name: String,
        val previousDate: LocalDate,
        val latestDate: LocalDate,
        val previousTotal: Int,
        val latestTotal: Int,
        val matchedSets: Int
    ) {
        val increase: Int get() = latestTotal - previousTotal
    }

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
                    !validSessionId
                ) return@mapIndexedNotNull null

                // Keep the newest valid guided session even when it has no set log.
                // A later timed-only session must not make this answer fall back to older sets.
                val moves = if (workout.setLog.length <= MAX_LOG_CHARS) {
                    parseMoves(workout.setLog)
                } else {
                    emptyList()
                }
                Session(date, index, moves)
            }
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.originalIndex })

        if (isWeeklyTotalQuestion(query)) {
            return weeklyTotalAnswer(query, sessions, today)
        }
        if (isLatestSetQuestion(query)) {
            return latestSetAnswer(sessions)
        }
        if (isLatestExerciseQuestion(query)) {
            return latestExerciseAnswer(sessions)
        }
        // Specific progress-ranking requests must win over the broader
        // "list exercises in the latest session" intent.
        if (isLargestRepIncreaseQuestion(query)) {
            return largestRepIncreaseAnswer(sessions)
        }
        if (isLatestSessionMoveListQuestion(query)) {
            return latestSessionMoveList(sessions)
        }

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
        raw.take(MAX_LOG_CHARS).split(';').take(MAX_MOVES).mapNotNull segment@{ segment ->
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

    private fun isSetQuestion(text: String): Boolean {
        val asksSetValues = listOf(
            "bao nhieu lan", "may lan", "bao nhieu rep", "so rep", "so hiep", "may hiep",
            "may cai", "so lan", "lam duoc may"
        ).any { text.contains(it) }
        val mentionsWeekComparison = listOf(
            "tuan nay", "tuan truoc", "tuan vua roi", "tuan truoc do"
        ).any { text.contains(it) }
        val asksExerciseComparison = !mentionsWeekComparison && listOf(
            "so sanh", "so voi", "tien bo", "lan truoc", "lan nay", "hai buoi", "2 buoi"
        ).any { text.contains(it) }
        return asksSetValues || asksExerciseComparison || isWeeklyTotalQuestion(text) ||
            isLargestRepIncreaseQuestion(text) || isLatestSetQuestion(text) ||
            isLatestExerciseQuestion(text) || isLatestSessionMoveListQuestion(text)
    }

    private data class WeeklyUnitTotal(
        var total: Long = 0L,
        var setCount: Int = 0,
        val sessionIndexes: MutableSet<Int> = linkedSetOf()
    )

    /** A seven-calendar-day sum of values the user explicitly reported for confirmed sets. */
    private fun isWeeklyTotalQuestion(text: String): Boolean {
        val mentionsWeek = listOf(
            "tuan nay", "7 ngay gan nhat", "bay ngay gan nhat", "7 ngay qua"
        ).any { text.contains(it) }
        val asksForRepetitions = listOf(
            "bao nhieu lan", "tong so lan", "tong cong bao nhieu lan",
            "tong so rep", "bao nhieu rep", "tong rep", "bao nhieu cai"
        ).any { text.contains(it) }
        return mentionsWeek && asksForRepetitions
    }

    /**
     * Totals only recorded values in the latest seven calendar days.
     * Missing set values are omitted rather than replaced with plan targets; units are never converted.
     */
    private fun weeklyTotalAnswer(
        query: String,
        sessions: List<Session>,
        today: LocalDate
    ): String {
        val firstDay = today.minusDays(6)
        val recent = sessions.filter { !it.date.isBefore(firstDay) && !it.date.isAfter(today) }
        if (recent.isEmpty()) {
            return "Chưa có buổi Guided Workout hợp lệ trong 7 ngày gần nhất để tổng hợp số liệu hiệp tự khai báo."
        }

        val knownNames = recent.flatMap { it.moves }
            .map { it.name }
            .distinctBy { normalize(it) }
        if (knownNames.isEmpty()) {
            return "Trong 7 ngày gần nhất chưa có chi tiết hiệp tự khai báo hợp lệ. Chibi không suy ra số lần từ mục tiêu giáo án."
        }

        val choice = chooseMove(query, knownNames)
        if (choice.ambiguous.isNotEmpty()) {
            return "Chibi tìm thấy vài động tác gần giống: " + choice.ambiguous.joinToString(", ") +
                ". Hãy nhắc đầy đủ tên bài để tránh cộng nhầm."
        }
        val selectedName = choice.name
        val selectedKey = selectedName?.let(::normalize)
        val totals = linkedMapOf<String, MutableMap<String, WeeklyUnitTotal>>()
        val displayNames = linkedMapOf<String, String>()
        var skippedDuplicateEntries = 0

        recent.forEach sessionLoop@{ session ->
            session.moves.groupBy { normalize(it.name) }.forEach moveLoop@{ (key, entries) ->
                if (selectedKey != null && key != selectedKey) return@moveLoop
                // Duplicate normalized names in one saved session are ambiguous; do not double count.
                if (entries.size != 1) {
                    skippedDuplicateEntries++
                    return@moveLoop
                }
                val move = entries.single()
                if (move.sets.isEmpty()) return@moveLoop
                displayNames.putIfAbsent(key, move.name)
                val unitTotals = totals.getOrPut(key) { linkedMapOf() }
                move.sets.forEach { set ->
                    val total = unitTotals.getOrPut(set.unit) { WeeklyUnitTotal() }
                    total.total = (total.total + set.value.toLong()).coerceAtMost(Int.MAX_VALUE.toLong())
                    total.setCount = (total.setCount + 1).coerceAtMost(MAX_RECORDS * MAX_SETS)
                    total.sessionIndexes.add(session.originalIndex)
                }
            }
        }

        if (totals.isEmpty()) {
            return if (selectedName != null) {
                "Chưa có số liệu hiệp tự khai báo hợp lệ cho " + selectedName +
                    " trong 7 ngày gần nhất. Chibi không lấy mục tiêu giáo án hoặc buổi cũ để bù."
            } else {
                "Trong 7 ngày gần nhất chưa có số liệu hiệp tự khai báo hợp lệ để cộng. Chibi không suy ra số lần từ mục tiêu giáo án."
            }
        }

        val visible = totals.entries
            .sortedBy { normalize(displayNames[it.key] ?: it.key) }
            .take(MAX_DISPLAY_MOVES)
        val lines = visible.map { (key, units) ->
            val unitText = units.entries.sortedBy { it.key }.joinToString("; ") { (unit, value) ->
                value.total.toString() + " " + unit + " (" + value.setCount + " hiệp, " +
                    value.sessionIndexes.size + " buổi)"
            }
            "• " + (displayNames[key] ?: key) + ": " + unitText
        }

        return buildString {
            if (selectedName != null) {
                append("Trong 7 ngày gần nhất, số liệu đã ghi cho " + selectedName + ":")
            } else {
                append("Tổng hợp số liệu hiệp tự khai báo trong 7 ngày gần nhất:")
            }
            append("\n")
            append(lines.joinToString("\n"))
            if (totals.size > visible.size) {
                append("\n• Còn " + (totals.size - visible.size) + " động tác khác chưa hiển thị.")
            }
            if (skippedDuplicateEntries > 0) {
                append("\nMột số mục trùng tên trong cùng buổi đã được bỏ qua để tránh cộng hai lần.")
            }
            append("\nChỉ cộng hiệp có số liệu bạn tự khai báo; hiệp chưa ghi số lần không được ước tính. " +
                "Đơn vị lần, nhịp và giây được giữ riêng, không quy đổi. Ghi chú riêng tư không được đọc.")
        }
    }

    /** Recognizes a request to rank exercises by logged rep increase, not perceived fitness. */
    private fun isLargestRepIncreaseQuestion(text: String): Boolean {
        val asksForExercise = listOf("bai nao", "bai tap nao", "dong tac nao").any { text.contains(it) }
        val asksForIncrease = listOf(
            "tang so lan nhieu nhat", "tang rep nhieu nhat", "tang nhieu nhat",
            "tang duoc nhieu nhat", "tien bo nhat", "tien bo nhieu nhat"
        ).any { text.contains(it) }
        return asksForExercise && asksForIncrease
    }

    /**
     * Ranks only exercises with two recent logged sessions, identical set indexes,
     * and repetition units on every matched set. Timed/beat-based sets are excluded.
     */
    private fun largestRepIncreaseAnswer(sessions: List<Session>): String {
        if (sessions.isEmpty()) {
            return "Chưa có buổi Guided Workout hợp lệ trong nhật ký trên máy để so sánh số lần."
        }
        val names = sessions.flatMap { it.moves }.map { it.name }.distinctBy { normalize(it) }
        var comparableExercises = 0
        val increases = names.mapNotNull { name ->
            val normalizedName = normalize(name)
            // Duplicate normalized names in one session are ambiguous; do not silently pick one.
            if (sessions.any { session ->
                    session.moves.count { normalize(it.name) == normalizedName } > 1
                }) return@mapNotNull null

            val observations = sessions.mapNotNull { session ->
                session.moves.firstOrNull { normalize(it.name) == normalizedName }
                    ?.let { session to it }
            }.take(2)
            if (observations.size < 2) return@mapNotNull null

            val latest = observations[0]
            val previous = observations[1]
            val latestSets = latest.second.sets.associateBy { it.index }
            val previousSets = previous.second.sets.associateBy { it.index }
            if (latestSets.isEmpty() || latestSets.keys != previousSets.keys) return@mapNotNull null

            val pairs = latestSets.keys.map { index ->
                latestSets.getValue(index) to previousSets.getValue(index)
            }
            if (pairs.any { (current, prior) -> current.unit != "lần" || prior.unit != "lần" }) {
                return@mapNotNull null
            }
            comparableExercises++
            val latestTotal = pairs.sumOf { it.first.value }
            val previousTotal = pairs.sumOf { it.second.value }
            val progress = RepProgress(
                name = latest.second.name,
                previousDate = previous.first.date,
                latestDate = latest.first.date,
                previousTotal = previousTotal,
                latestTotal = latestTotal,
                matchedSets = pairs.size
            )
            progress.takeIf { it.increase > 0 }
        }

        if (comparableExercises == 0) {
            return "Chưa đủ hai buổi có cùng động tác, cùng số hiệp và tất cả giá trị đều ghi bằng đơn vị lần để so sánh. Chibi không quy đổi giây hoặc nhịp thành số lần; hãy ghi thêm dữ liệu tương ứng nhé."
        }
        if (increases.isEmpty()) {
            return "Trong $comparableExercises động tác đủ điều kiện so sánh, nhật ký chưa ghi nhận động tác nào tăng tổng số lần ở các hiệp tương ứng. Điều này không có nghĩa thể lực giảm; chỉ phản ánh các số liệu bạn đã lưu."
        }

        val highest = increases.maxOf { it.increase }
        val leaders = increases.filter { it.increase == highest }
            .sortedBy { normalize(it.name) }
        val visible = leaders.take(MAX_DISPLAY_MOVES)
        val lines = visible.map { progress ->
            "• ${progress.name}: ${progress.latestTotal} so với ${progress.previousTotal} lần " +
                "(+${progress.increase} lần qua ${progress.matchedSets} hiệp; " +
                "${progress.latestDate.format(dateFormat)} so với ${progress.previousDate.format(dateFormat)})"
        }
        return buildString {
            append("Động tác có mức tăng số lần ghi nhận lớn nhất giữa hai buổi gần nhất có ghi bài đó:")
            append("\n")
            append(lines.joinToString("\n"))
            if (leaders.size > visible.size) append("\n• Còn ${leaders.size - visible.size} động tác đồng hạng.")
            append("\nChỉ so sánh các hiệp cùng chỉ số, cùng số lượng hiệp và đơn vị lần; số liệu do bạn tự khai báo. ")
            append("Kết quả không đánh giá kỹ thuật, biên độ, tải tập hay mức độ hồi phục.")
        }
    }

    /** A short follow-up may refer to the last confirmed set without repeating the exercise name. */
    private fun isLatestSetQuestion(text: String): Boolean {
        val refersToSet = listOf(
            "hiep vua roi", "set vua roi", "hiep cuoi", "set cuoi"
        ).any { text.contains(it) }
        val asksValue = listOf(
            "bao nhieu", "may lan", "may cai", "so lan", "lam duoc may"
        ).any { text.contains(it) }
        return refersToSet && asksValue
    }

    /** Resolves "bài vừa rồi" only inside the latest saved guided session. */
    private fun isLatestExerciseQuestion(text: String): Boolean {
        val refersToExercise = listOf(
            "bai vua roi", "dong tac vua roi", "bai cuoi", "dong tac cuoi"
        ).any { text.contains(it) }
        val asksValue = listOf(
            "bao nhieu", "may lan", "may cai", "so lan", "may hiep", "so hiep",
            "lam duoc may"
        ).any { text.contains(it) }
        return refersToExercise && asksValue
    }

    private fun latestSetAnswer(sessions: List<Session>): String {
        val latest = sessions.firstOrNull()
            ?: return "Chưa tìm thấy buổi Guided Workout hợp lệ trong nhật ký trên máy."
        val move = latest.moves.lastOrNull()
            ?: return "Buổi Guided Workout gần nhất (${latest.date.format(dateFormat)}) chưa có chi tiết hiệp tự khai báo. Chibi không lấy số liệu từ buổi cũ thay thế."
        val set = move.sets.lastOrNull()
            ?: return "Chưa tìm thấy hiệp đã xác nhận trong buổi Guided Workout gần nhất."
        return "Trong buổi Guided Workout gần nhất (${latest.date.format(dateFormat)}), động tác cuối cùng có ghi hiệp là ${move.name}: Hiệp ${set.index}: ${set.value} ${set.unit}. Đây là số liệu bạn tự khai báo, không phải cảm biến xác minh."
    }

    private fun latestExerciseAnswer(sessions: List<Session>): String {
        val latest = sessions.firstOrNull()
            ?: return "Chưa tìm thấy buổi Guided Workout hợp lệ trong nhật ký trên máy."
        val move = latest.moves.lastOrNull()
            ?: return "Buổi Guided Workout gần nhất (${latest.date.format(dateFormat)}) chưa có chi tiết hiệp tự khai báo. Chibi không lấy số liệu từ buổi cũ thay thế."
        return "Trong buổi Guided Workout gần nhất (${latest.date.format(dateFormat)}), động tác cuối cùng có ghi hiệp là ${move.name}: ${formatSets(move.sets)}. Đây là số liệu bạn tự khai báo, không phải cảm biến xác minh."
    }

    private fun isLatestSessionMoveListQuestion(text: String): Boolean {
        val mentionsRecentSession = text.contains("buoi") &&
            listOf("gan nhat", "moi nhat", "vua roi", "hom qua").any { text.contains(it) }
        // A generic request to list recent sessions belongs to WorkoutHistoryConversation.
        // This router should only consume a request that explicitly asks for exercises.
        val namesExercise = listOf(
            "bai nao", "bai gi", "nhung bai", "cac bai", "bai tap",
            "dong tac nao", "nhung dong tac", "dong tac"
        ).any { text.contains(it) }
        val listsExercises = text.contains("liet ke") &&
            listOf("bai tap", "dong tac", "cac bai", "nhung bai").any { text.contains(it) }
        return mentionsRecentSession && (namesExercise || listsExercises)
    }

    private fun latestSessionMoveList(sessions: List<Session>): String {
        val latest = sessions.firstOrNull()
            ?: return "Chưa tìm thấy buổi Guided Workout hợp lệ trong nhật ký trên máy."
        if (latest.moves.isEmpty()) {
            return "Buổi Guided Workout gần nhất (${latest.date.format(dateFormat)}) chưa có chi tiết hiệp tự khai báo. Có thể buổi đó dùng động tác theo thời gian hoặc bạn chưa xác nhận số hiệp; Chibi sẽ không lấy dữ liệu từ buổi cũ thay thế."
        }

        val visibleMoves = latest.moves.take(MAX_DISPLAY_MOVES)
        return buildString {
            append("Các động tác có chi tiết hiệp đã tự ghi trong buổi Guided Workout gần nhất (")
            append(latest.date.format(dateFormat))
            append("):")
            visibleMoves.forEach { move ->
                append("\n• ")
                append(move.name)
                append(": ")
                append(formatSets(move.sets))
            }
            val remaining = latest.moves.size - visibleMoves.size
            if (remaining > 0) append("\n• Còn ${remaining} động tác có chi tiết hiệp khác.")
            append("\nChỉ liệt kê động tác có số hiệp/giá trị đã ghi; bài theo thời gian có thể không xuất hiện. ")
            append("Số liệu do bạn tự khai báo, không phải cảm biến xác minh; ghi chú riêng tư không được đọc.")
        }
    }

    private fun formatSets(sets: List<SetValue>): String =
        sets.joinToString(", ") { "Hiệp " + it.index + ": " + it.value + " " + it.unit }

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
