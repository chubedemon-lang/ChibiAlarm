package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Answers questions about the live rest countdown, not the catalog's recommended rest.
 *
 * This is a read-only, offline conversation helper. It never changes the timer or workout.
 */
object WorkoutRestStatusConversation {
    private const val MAX_INPUT_CHARS = 160
    private const val MAX_REST_MILLIS = 600_000L

    private val remainingPhrases = listOf(
        "con bao lau",
        "con may giay",
        "con may phut",
        "con bao nhieu giay",
        "con bao nhieu phut",
        "con bao nhieu giay nua",
        "con bao nhieu phut nua",
        "nghi con bao nhieu giay",
        "nghi con bao nhieu phut",
        "con lai bao nhieu",
        "nghi con lai",
        "thoi gian nghi con lai",
        "bao lau nua het nghi",
        "bao lau nua nghi xong",
        "how long rest left",
        "how much rest left",
        "how much time left"
    )

    fun answer(
        input: String,
        restIsActive: Boolean,
        remainingMs: Long?,
        restIsPaused: Boolean = false
    ): String? {
        if (input.isBlank() || input.length > MAX_INPUT_CHARS) return null
        val query = normalize(input)
        val asksRemaining = remainingPhrases.any(query::contains)
        val hasRestReference = Regex("\\b(nghi|rest)\\b").containsMatchIn(query)

        // A short "còn bao lâu?" is meaningful only while a rest countdown is open.
        if (!asksRemaining && !(restIsActive && query.contains("con bao lau"))) return null
        if (!restIsActive) {
            return if (hasRestReference && asksRemaining) {
                "Hiện không có đồng hồ nghỉ đang chạy. Nếu muốn hỏi thời gian nghỉ đề xuất của bài, hãy hỏi “nghỉ bao lâu?”."
            } else {
                null
            }
        }

        val safeRemaining = remainingMs?.takeIf { it in 0L..MAX_REST_MILLIS }
            ?: return "Chibi chưa đọc được thời gian nghỉ hiện tại; hãy nhìn bộ đếm trên màn hình."
        val seconds = ((safeRemaining + 999L) / 1_000L).toInt().coerceIn(0, 600)
        val duration = formatDuration(seconds)
        return if (restIsPaused) {
            "Đồng hồ nghỉ đang tạm dừng; còn khoảng $duration. Chibi chưa tự chạy lại."
        } else {
            "Còn khoảng $duration nghỉ. Đồng hồ vẫn chạy; Chibi chưa thay đổi giáo án."
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        if (totalSeconds < 60) return "$totalSeconds giây"
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (seconds == 0) "$minutes phút" else "$minutes phút $seconds giây"
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
