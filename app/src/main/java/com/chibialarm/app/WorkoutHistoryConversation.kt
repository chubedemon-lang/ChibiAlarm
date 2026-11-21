package com.chibialarm.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Answers simple workout-history questions from the on-device journal.
 *
 * Only structured date, activity name, duration, self-reported set values and effort labels are read.
 * Free-text notes, safety preferences and health details are never inspected or returned.
 * This is a local journal summary, not a recovery or medical assessment.
 */
object WorkoutHistoryConversation {
    private const val MAX_RECORDS = 10_000
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    private data class Entry(
        val date: LocalDate,
        val name: String,
        val minutes: Int,
        val originalIndex: Int
    )

    private data class Window(
        val label: String,
        val start: LocalDate
    )

    fun answer(
        input: String,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): String? {
        val effortHistoryAnswer = WorkoutEffortHistoryConversation.answer(input, workouts, today)
        if (effortHistoryAnswer != null) return effortHistoryAnswer

        val setHistoryAnswer = WorkoutSetHistoryConversation.answer(input, workouts, today)
        if (setHistoryAnswer != null) return setHistoryAnswer

        val text = normalize(input.take(300))
        if (isEffortBasedLighterPlanQuestion(text)) {
            return effortBasedLighterPlanAnswer(workouts, today)
        }
        if (!isHistoryQuestion(text)) return null

        // Keep the newest records when a long-lived journal exceeds the processing cap.
        val entries = workouts.takeLast(MAX_RECORDS)
            .mapIndexedNotNull { index, workout ->
                val date = runCatching { LocalDate.parse(workout.date) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                if (date.isAfter(today) || workout.minutes !in 1..600) {
                    return@mapIndexedNotNull null
                }
                Entry(
                    date = date,
                    name = workout.name.trim().take(90).ifBlank { "Buổi tập" },
                    minutes = workout.minutes,
                    originalIndex = index
                )
            }
            .toList()

        if (isNextWeekPlanQuestion(text)) {
            return suggestNextWeekPlan(entries, today)
        }

        if (isComparisonQuestion(text)) {
            return compareRecentPeriods(entries, today)
        }

        if (isRecentListQuestion(text)) {
            val requestedCount = recentListCount(text)
            val recent = entries
                .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.originalIndex })
                .take(requestedCount)
            if (recent.isEmpty()) {
                return "Không tìm thấy buổi tập hợp lệ trong nhật ký trên máy."
            }
            val lines = recent.mapIndexed { index, entry ->
                (index + 1).toString() + ". " + entry.date.format(dateFormat) +
                    " — " + entry.name + " • " + entry.minutes + " phút"
            }
            return "Đây là " + recent.size + " buổi gần nhất đã ghi trong nhật ký:\n" +
                lines.joinToString("\n") +
                "\nChỉ hiển thị ngày, tên hoạt động và thời lượng; ghi chú riêng tư không được đọc."
        }

        if (isLatestQuestion(text)) {
            val latest = entries.maxWithOrNull(
                compareBy<Entry> { it.date }.thenBy { it.originalIndex }
            ) ?: return "Không tìm thấy buổi tập hợp lệ trong nhật ký trên máy. Nếu vừa tập, hãy lưu buổi đó trong mục Nhật ký tập."

            val daysAgo = ChronoUnit.DAYS.between(latest.date, today)
            val whenText = when (daysAgo) {
                0L -> "hôm nay"
                1L -> "hôm qua"
                else -> "$daysAgo ngày trước"
            }
            return "Buổi gần nhất được ghi là ${latest.name}, vào $whenText (${latest.date.format(dateFormat)}), thời lượng ${latest.minutes} phút. Đây là dữ liệu nhật ký tự ghi trên thiết bị, không phải số đo tự động."
        }

        val window = chooseWindow(text, today)
        val selected = entries.filter { !it.date.isBefore(window.start) }
        if (selected.isEmpty()) {
            return "Không tìm thấy buổi tập hợp lệ trong ${window.label}. Nhật ký chỉ phản ánh những buổi bạn đã lưu trên thiết bị."
        }

        val sessions = selected.size
        val activeDays = selected.map { it.date }.distinct().size
        val totalMinutes = selected.sumOf { it.minutes.toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val asksSessions = has(text, "may buoi", "bao nhieu buoi", "so buoi", "may lan", "bao nhieu lan")
        val asksDays = has(text, "may ngay", "bao nhieu ngay", "ngay van dong", "so ngay")
        val asksMinutes = has(text, "may phut", "bao nhieu phut", "tong thoi gian", "bao lau")
        val asksSpecificValue = asksSessions || asksDays || asksMinutes

        val parts = buildList {
            if (asksSessions || !asksSpecificValue) add("$sessions buổi đã ghi")
            if (asksDays || !asksSpecificValue) add("$activeDays ngày có vận động được ghi nhận")
            if (asksMinutes || !asksSpecificValue) add("$totalMinutes phút")
        }
        return "Trong ${window.label}: ${parts.joinToString(" • ")}. Các con số được tổng hợp từ nhật ký cục bộ; chúng không đánh giá thể lực hay mức độ hồi phục."
    }

    private data class PeriodSummary(
        val sessions: Int,
        val activeDays: Int,
        val minutes: Int,
        val medianSessionMinutes: Int?
    )

    /** Median is robust to one unusually long logged session; it is descriptive, not a prescription. */
    private fun medianSessionMinutes(entries: List<Entry>): Int? {
        val durations = entries.map { it.minutes }.sorted()
        if (durations.isEmpty()) return null
        val middle = durations.size / 2
        return if (durations.size % 2 == 1) {
            durations[middle]
        } else {
            ((durations[middle - 1].toLong() + durations[middle].toLong() + 1L) / 2L).toInt()
        }
    }

    /** Compare two fixed seven-day windows; this describes logged activity, not recovery. */
    private fun compareRecentPeriods(entries: List<Entry>, today: LocalDate): String {
        val currentStart = today.minusDays(6)
        val previousStart = today.minusDays(13)
        fun summarize(start: LocalDate, endExclusive: LocalDate): PeriodSummary {
            val selected = entries.filter { !it.date.isBefore(start) && it.date.isBefore(endExclusive) }
            val minutes = selected.sumOf { it.minutes.toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return PeriodSummary(
                sessions = selected.size,
                activeDays = selected.map { it.date }.distinct().size,
                minutes = minutes,
                medianSessionMinutes = medianSessionMinutes(selected)
            )
        }

        val current = summarize(currentStart, today.plusDays(1))
        val previous = summarize(previousStart, currentStart)
        if (current.sessions == 0 && previous.sessions == 0) {
            return "Chưa có buổi tập hợp lệ trong hai giai đoạn để so sánh. Nhật ký chỉ phản ánh những buổi bạn đã lưu trên thiết bị."
        }

        fun delta(value: Int, unit: String): String = when {
            value > 0 -> "+$value $unit"
            value < 0 -> "$value $unit"
            else -> "không đổi"
        }

        return buildString {
            append("So sánh 7 ngày gần nhất với 7 ngày liền trước (dựa trên nhật ký tự ghi):")
            append("\n• Buổi tập: ${current.sessions} so với ${previous.sessions} (${delta(current.sessions - previous.sessions, "buổi")})")
            append("\n• Ngày có vận động: ${current.activeDays} so với ${previous.activeDays} (${delta(current.activeDays - previous.activeDays, "ngày")})")
            append("\n• Thời lượng: ${current.minutes} so với ${previous.minutes} phút (${delta(current.minutes - previous.minutes, "phút")})")
            append("\nĐây chỉ là so sánh dữ liệu đã ghi; không đo cường độ, chất lượng buổi tập hay khả năng hồi phục.")
        }
    }

    private fun isComparisonQuestion(text: String): Boolean {
        val mentionsBothWeeks = text.contains("tuan nay") && text.contains("tuan truoc")
        // Vietnamese users naturally say "so với tuần trước"; recognize it alongside
        // explicit comparison verbs so the history router does not fall through to planning.
        val asksForComparison = has(
            text, "so sanh", "so voi", "nhieu hon", "it hon", "tang hay giam", "khac gi", "the nao"
        )
        return mentionsBothWeeks && asksForComparison
    }

    private fun isNextWeekPlanQuestion(text: String): Boolean {
        val mentionsNextWeek = has(text, "tuan toi", "tuan sau")
        val asksForPlan = has(
            text, "ke hoach", "lich tap", "dieu chinh", "nen tap", "sap xep", "may buoi", "the nao"
        )
        return mentionsNextWeek && asksForPlan
    }

    /**
     * Proposes a conservative next-week frequency from logged active days only.
     * It never writes the routine or increases the target to "catch up" missed activity.
     */
    private fun suggestNextWeekPlan(entries: List<Entry>, today: LocalDate): String {
        val currentStart = today.minusDays(6)
        val previousStart = today.minusDays(13)

        fun summarize(start: LocalDate, endExclusive: LocalDate): PeriodSummary {
            val selected = entries.filter { !it.date.isBefore(start) && it.date.isBefore(endExclusive) }
            return PeriodSummary(
                sessions = selected.size,
                activeDays = selected.map { it.date }.distinct().size,
                minutes = selected.sumOf { it.minutes.toLong() }
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                medianSessionMinutes = medianSessionMinutes(selected)
            )
        }

        val current = summarize(currentStart, today.plusDays(1))
        val previous = summarize(previousStart, currentStart)
        if (current.sessions == 0 && previous.sessions == 0) {
            return "Chưa đủ nhật ký trong hai tuần gần nhất để gợi ý lịch tuần tới. Hãy ghi lại vài buổi trước; Chibi sẽ không tự đoán số ngày tập."
        }

        // Keep the draft at or below the latest observed frequency and reserve rest days
        // when the journal shows six or seven active days.
        val targetDays = when {
            current.activeDays == 0 -> 1
            else -> current.activeDays.coerceAtMost(5)
        }
        val suggestedDates = WorkoutWeekSchedulePreview.nextWeekDates(today, targetDays)
        val schedule = suggestedDates.joinToString(" • ") { WorkoutWeekSchedulePreview.format(it) }
        // Use the current week's median when available; otherwise fall back to the prior week.
        // This only describes the user's recent logged duration and never increases a target.
        val referenceDuration = current.medianSessionMinutes ?: previous.medianSessionMinutes
        val target = when {
            current.activeDays == 0 -> "1 ngày vận động nhẹ, nếu phù hợp"
            current.activeDays > 5 -> "tối đa 5 ngày tập có cấu trúc, xen kẽ ngày nghỉ"
            else -> "$targetDays ngày vận động, xen kẽ ngày nghỉ"
        }
        val trend = when {
            current.activeDays < previous.activeDays ->
                "Tuần vừa rồi có ít ngày vận động được ghi nhận hơn tuần trước."
            current.activeDays > previous.activeDays ->
                "Tuần vừa rồi có nhiều ngày vận động được ghi nhận hơn tuần trước."
            else ->
                "Số ngày vận động được ghi nhận giữa hai tuần không đổi."
        }
        val scheduleNote = when {
            current.activeDays == 0 ->
                "Ngày gợi ý là lựa chọn nhẹ và có thể bỏ qua; không cần tập bù."
            current.activeDays > 5 ->
                "Nhật ký ghi nhận ${current.activeDays} ngày; bản nháp chừa ít nhất hai ngày không có buổi tập có cấu trúc."
            else ->
                "Các ngày chỉ là gợi ý để dàn lịch; có thể đổi theo quỹ thời gian và lịch cá nhân."
        }

        return buildString {
            append("Gợi ý lịch tuần tới (bản nháp, chưa tự thay đổi lịch):")
            append("\n• $trend")
            append("\n• Nhật ký: ${current.activeDays} ngày vận động tuần vừa rồi; ${previous.activeDays} ngày ở tuần trước.")
            append("\n• Mục tiêu khởi đầu: $target.")
            append("\n• Ngày gợi ý: $schedule.")
            append("\n• $scheduleNote")
            append("\n• Không cần tập bù những ngày đã bỏ lỡ; giữ thời lượng vừa với quỹ thời gian hiện có.")
            referenceDuration?.let {
                append("\n• Thời lượng tham khảo: trung vị các buổi gần đây là $it phút/buổi; đây không phải mục tiêu bắt buộc.")
            }
            append("\nĐây là gợi ý theo nhật ký tự ghi, không đánh giá thể lực hay hồi phục. Hãy kiểm tra mục tiêu, thời lượng sẵn có, dụng cụ và hạn chế vận động rồi tự xác nhận ngày cụ thể trong Lịch trình.")
        }
    }

    /**
     * Offers an opt-in lighter-plan preview only when the same move was self-rated
     * HARD/VERY_HARD in at least two distinct recent guided sessions.
     */
    private fun isEffortBasedLighterPlanQuestion(text: String): Boolean {
        val asksAboutMove = has(text, "bai nao", "dong tac nao", "co bai nao", "co dong tac nao")
        val asksToReduce = has(text, "giam tai", "giam khoi luong", "tap nhe hon", "nhe hon")
        return asksAboutMove && asksToReduce
    }

    private fun effortBasedLighterPlanAnswer(
        workouts: List<Workout>,
        today: LocalDate
    ): String {
        val suggestion = WorkoutEffortPlanAdvisor.suggest(workouts, today)
            ?: return "Chưa đủ dữ liệu để gợi ý giảm tải: cần ít nhất hai buổi Guided Workout riêng biệt trong 30 ngày, cùng một động tác được bạn tự đánh dấu khó hoặc quá sức. Chibi không đọc ghi chú riêng tư và không tự thay đổi giáo án."

        return suggestion.message +
            "\n\nNếu muốn xem bản nháp, hãy nói: “${suggestion.prompt}”. " +
            "Chibi chưa thay đổi giáo án hoặc lịch; mọi thay đổi vẫn cần bạn xem trước và xác nhận."
    }

    private fun isRecentListQuestion(text: String): Boolean {
        val asksForList = has(
            text, "liet ke", "danh sach", "xem cac buoi", "cac buoi gan day",
            "cac buoi vua qua", "buoi tap gan day", "buoi tap vua qua"
        )
        val asksForCountedRecent = Regex(
            """(?:^| )\d{1,2} buoi (?:tap )?(?:gan nhat|gan day|vua qua)(?: |$)"""
        ).containsMatchIn(text)
        return asksForList || asksForCountedRecent
    }

    private fun recentListCount(text: String): Int {
        val requested = Regex(
            """(?:^| )(\d{1,2}) buoi (?:tap )?(?:gan nhat|gan day|vua qua)(?: |$)"""
        ).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return (requested ?: 5).coerceIn(1, 10)
    }

    private fun chooseWindow(text: String, today: LocalDate): Window = when {
        has(text, "hom nay", "ngay hom nay") ->
            Window("hôm nay", today)
        has(text, "thang nay") ->
            Window("tháng này", today.withDayOfMonth(1))
        has(text, "30 ngay", "mot thang qua") ->
            Window("30 ngày gần nhất", today.minusDays(29))
        else ->
            Window("7 ngày gần nhất", today.minusDays(6))
    }

    private fun isLatestQuestion(text: String): Boolean =
        has(text, "lan gan nhat", "lan cuoi", "buoi gan nhat", "buoi moi nhat", "gan nhat tap",
            "khi nao tap", "bao lau roi chua tap", "chua tap bao lau") ||
            (text.contains("bao lau") && text.contains("chua tap"))

    private fun isHistoryQuestion(text: String): Boolean {
        val hasSubject = has(text, "tap", "buoi", "van dong", "nhat ky", "lich su")
        val asksForHistory = has(
            text, "bao nhieu", "may buoi", "may ngay", "may phut", "bao lau",
            "tong buoi", "tong so", "tong phut", "tong thoi gian",
            "so buoi", "so ngay", "so phut", "khi nao", "gan nhat",
            "nhat ky", "lich su"
        )
        return (hasSubject && (asksForHistory || isRecentListQuestion(text))) ||
            isComparisonQuestion(text) || isNextWeekPlanQuestion(text)
    }

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

    private fun has(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(it) }
}
