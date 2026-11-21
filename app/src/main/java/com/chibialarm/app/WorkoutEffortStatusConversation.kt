package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Answers questions from the existing, user-confirmed effort check-ins for the active move.
 *
 * These values are subjective self-reports, not sensor measurements, diagnoses, or estimates
 * of physiological load. Only confirmed sets are visible to the conversation.
 */
internal object WorkoutEffortStatusConversation {
    private const val MAX_SETS = 20
    private const val MAX_INPUT_CHARS = 300
    private const val MAX_RESPONSE_CHARS = 700

    fun answer(
        input: String,
        completedSets: Int?,
        effortLevels: List<WorkoutEffortFeedback.Level?>?
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null
        val query = normalize(input)
        if (!isEffortQuestion(query)) return null

        val confirmedCount = completedSets?.coerceIn(0, MAX_SETS) ?: 0
        if (confirmedCount == 0) {
            return "Chưa có hiệp nào được xác nhận để xem cảm nhận. Chibi không tự suy đoán mức độ gắng sức."
        }

        val confirmedLevels = effortLevels.orEmpty().take(confirmedCount).take(MAX_SETS)
        if (asksAboutLatestSet(query)) {
            val latest = confirmedLevels.getOrNull(confirmedCount - 1)
                ?: return "Hiệp $confirmedCount chưa có đánh giá cảm nhận. Chibi không lấy cảm nhận của hiệp trước để thay thế."
            return "Theo cảm nhận bạn đã chọn cho hiệp $confirmedCount: ${latest.label}. Đây là tự đánh giá, không phải phép đo thể lực."
        }

        val rated = confirmedLevels.mapIndexedNotNull { index, level ->
            level?.let { "hiệp ${index + 1}: ${it.label}" }
        }
        if (rated.isEmpty()) {
            return "Bạn chưa ghi nhận cảm nhận cho các hiệp đã xác nhận. Chibi không tự gán mức độ gắng sức."
        }
        return ("Cảm nhận đã ghi: " + rated.joinToString("; ") +
            ". Đây là tự đánh giá của bạn, không phải phép đo thể lực.")
            .take(MAX_RESPONSE_CHARS)
    }

    private fun isEffortQuestion(query: String): Boolean {
        val explicitCues = listOf(
            "muc do gang suc", "gang suc", "danh gia hiep",
            "cam nhan cua toi", "rpe", "effort"
        )
        if (explicitCues.any(query::contains)) return true
        if (query.contains("cam nhan") && (query.contains("hiep") || query.contains("tong ket"))) return true
        val asksAboutSet = query.contains("hiep")
        val describesEffort = listOf("kho", "met", "nhe", "vua suc", "cam thay", "danh gia")
            .any(query::contains)
        return asksAboutSet && describesEffort
    }

    private fun asksAboutLatestSet(query: String): Boolean =
        listOf(
            "hiep vua roi", "hiep gan nhat", "hiep moi nhat",
            "hiep cuoi", "hiep nay", "vua xong"
        ).any(query::contains)

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
