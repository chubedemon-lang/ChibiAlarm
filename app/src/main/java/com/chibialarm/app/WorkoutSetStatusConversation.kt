package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Answers read-only questions about the active move's confirmed set progress.
 *
 * Counts are user-confirmed, not sensor measurements. Optional sets are kept separate
 * from the required minimum so the coach never implies that extra volume is mandatory.
 */
internal object WorkoutSetStatusConversation {
    private const val MAX_INPUT_CHARS = 160

    private val remainingPhrases = listOf(
        "con may hiep",
        "con bao nhieu hiep",
        "con lai may hiep",
        "con lai bao nhieu hiep",
        "con hiep nao khong",
        "may hiep nua",
        "con may set",
        "con bao nhieu set",
        "how many sets left",
        "sets remaining"
    )

    private val completedPhrases = listOf(
        "da lam may hiep",
        "da lam bao nhieu hiep",
        "da hoan thanh may hiep",
        "da hoan thanh bao nhieu hiep",
        "bao nhieu hiep da xong",
        "hiep da ghi",
        "tien do hiep",
        "how many sets have i done",
        "sets completed"
    )

    fun answer(
        input: String,
        currentDose: String?,
        fallbackDose: String?,
        completedSets: Int?
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null
        val query = normalize(input)
        val asksRemaining = remainingPhrases.any(query::contains)
        val asksCompleted = completedPhrases.any(query::contains)
        if (!asksRemaining && !asksCompleted) return null

        val plan = sequenceOf(currentDose, fallbackDose)
            .filterNotNull()
            .mapNotNull(WorkoutSetTracker::parse)
            .firstOrNull()
            ?: return "Động tác hiện tại không có mục tiêu theo hiệp; Chibi không thể xác định số hiệp."

        val completed = completedSets
            ?: return "Chibi chưa có tiến độ xác nhận của động tác này; hãy mở đúng động tác để xem số hiệp."
        if (completed !in 0..plan.maximumSets) {
            return "Chibi chưa đọc được tiến độ hiệp hợp lệ; hãy kiểm tra bộ đếm trên màn hình."
        }

        val requiredDone = minOf(completed, plan.requiredSets)
        val optionalTotal = plan.maximumSets - plan.requiredSets
        val optionalDone = (completed - plan.requiredSets).coerceIn(0, optionalTotal)

        if (asksCompleted) {
            return buildString {
                append("Bạn đã tự xác nhận $requiredDone/${plan.requiredSets} hiệp bắt buộc.")
                if (optionalTotal > 0) {
                    append(" Hiệp tùy chọn đã ghi: $optionalDone/$optionalTotal.")
                }
                append(" Số hiệp do bạn tự xác nhận, không phải cảm biến đo.")
            }
        }

        val requiredRemaining = plan.requiredSets - requiredDone
        val optionalRemaining = optionalTotal - optionalDone
        return when {
            requiredRemaining > 0 && optionalTotal > 0 ->
                "Còn $requiredRemaining hiệp bắt buộc. Sau đó có tối đa $optionalTotal " +
                    "hiệp tùy chọn; phần tùy chọn không bắt buộc."
            requiredRemaining > 0 ->
                "Còn $requiredRemaining hiệp bắt buộc."
            optionalRemaining > 0 ->
                "Đã đủ ${plan.requiredSets}/${plan.requiredSets} hiệp bắt buộc. " +
                    "Còn tối đa $optionalRemaining hiệp tùy chọn; chỉ thực hiện nếu bạn còn sức."
            else ->
                "Đã ghi nhận đủ ${plan.maximumSets}/${plan.maximumSets} hiệp. Không cần tập thêm."
        } + " Số hiệp do bạn tự xác nhận."
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
