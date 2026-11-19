package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Offline rest adjustment. Never guesses a large rest duration from unrelated
 * numbers (e.g. "bài 60"). Unsupported or ambiguous phrases use 30 seconds.
 */
object WorkoutRestRequest {
    private val smallNumbers = mapOf(
        "khong" to 0, "mot" to 1, "hai" to 2, "ba" to 3, "bon" to 4,
        "tu" to 4, "nam" to 5, "lam" to 5, "sau" to 6, "bay" to 7,
        "tam" to 8, "chin" to 9, "muoi" to 10
    )
    private val tens = mapOf(
        "hai" to 2, "ba" to 3, "bon" to 4, "tu" to 4, "nam" to 5,
        "sau" to 6, "bay" to 7, "tam" to 8, "chin" to 9
    )

    private fun parseNumberPhrase(phrase: String): Int? {
        val parts = phrase.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        if (parts.size == 1) {
            return parts[0].toIntOrNull() ?: smallNumbers[parts[0]]
        }
        if (parts == listOf("mot", "tram", "hai", "muoi")) return 120
        if (parts.size == 2 && parts[0] == "muoi") {
            val unit = smallNumbers[parts[1]] ?: return null
            return (10 + unit).takeIf { unit in 1..9 }
        }
        if (parts.size == 2 && parts[1] == "muoi") {
            val ten = tens[parts[0]] ?: return null
            return ten * 10
        }
        if (parts.size == 3 && parts[1] == "muoi") {
            val ten = tens[parts[0]] ?: return null
            val unit = smallNumbers[parts[2]] ?: return null
            if (unit !in 1..9) return null
            return ten * 10 + unit
        }
        return null
    }

    /** Pick the longest valid number phrase immediately before a time unit. */
    private fun parseNumberEndingAt(tokens: List<String>, endExclusive: Int): Int? {
        val first = (endExclusive - 4).coerceAtLeast(0)
        for (start in first until endExclusive) {
            parseNumberPhrase(tokens.subList(start, endExclusive).joinToString(" "))?.let { return it }
        }
        return null
    }

    /**
     * Returns a duration only when speech contains an unambiguous, supported rest
     * duration. This lets the command router distinguish a spoken number from the
     * conservative 30-second fallback used for display.
     */
    fun hasRecognizedDuration(utterance: String): Boolean =
        parseRecognizedSeconds(utterance) != null

    fun seconds(utterance: String): Int = parseRecognizedSeconds(utterance) ?: 30

    /**
     * Resolve a short follow-up such as "thêm 30 giây nữa" only while a rest
     * countdown is already active. The amount and unit must be explicit; this
     * never uses the generic 30-second fallback and never starts a new rest.
     */
    fun contextualExtensionSeconds(utterance: String, restIsActive: Boolean): Int? {
        if (!restIsActive || utterance.isBlank() || utterance.length > 140) return null
        val text = Normalizer.normalize(utterance.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null
        // A negation or symptom in the same utterance must never become a rest extension.
        if (Regex("""(?:^| )(?:khong|dung|bo qua|huy|dau|nhuc|chan thuong|choang|chong mat|kho tho)(?: |$)""")
                .containsMatchIn(text)
        ) return null
        val explicitIncrease = Regex("""(?:^| )(?:them|keo dai)(?: |$)""").containsMatchIn(text)
        if (!explicitIncrease) return null
        return parseRecognizedSeconds("nghỉ $utterance")
    }

    private fun parseRecognizedSeconds(utterance: String): Int? {
        val text = Normalizer.normalize(utterance.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.length > 160 || !Regex("\\b(nghi|rest)\\b").containsMatchIn(text)) return null
        val tokens = text.split(' ')

        // Prefer a complete minute+second expression. This also accepts mixed
        // digit/spoken forms such as "1 phút hai mươi lăm giây".
        val minuteIndex = tokens.indexOfFirst { it in setOf("phut", "minute", "minutes") }
        val secondIndex = if (minuteIndex >= 0) {
            tokens.indexOfFirstFrom(minuteIndex + 1) { it in setOf("giay", "gi", "s", "second", "seconds") }
        } else -1
        if (minuteIndex >= 0 && secondIndex > minuteIndex) {
            val minutes = parseNumberEndingAt(tokens, minuteIndex)
            val secondPhrase = tokens.subList(minuteIndex + 1, secondIndex).joinToString(" ")
            val seconds = parseNumberPhrase(secondPhrase)
            if (minutes != null && seconds != null && seconds in 0..59) {
                val total = minutes.toLong() * 60L + seconds.toLong()
                return total.takeIf { it in 15L..120L }?.toInt()
            }
            return null
        }

        // "Một phút rưỡi" and "một trăm hai mươi giây" are common speech forms.
        if (Regex("\\bmot phut ruoi\\b").containsMatchIn(text)) return 90
        if (Regex("\\bmot tram hai muoi giay\\b").containsMatchIn(text)) return 120

        val unitIndex = tokens.indexOfFirst {
            it in setOf("giay", "gi", "s", "second", "seconds", "phut", "minute", "minutes")
        }
        if (unitIndex < 0) return null
        val count = parseNumberEndingAt(tokens, unitIndex) ?: return null
        val unit = tokens[unitIndex]
        val total = if (unit in setOf("phut", "minute", "minutes")) count.toLong() * 60L else count.toLong()
        // Prevent implausible recognition results from silently adding a huge rest.
        return total.takeIf { it in 15L..120L }?.toInt()
    }

    private inline fun <T> List<T>.indexOfFirstFrom(startIndex: Int, predicate: (T) -> Boolean): Int {
        for (index in startIndex.coerceAtLeast(0) until size) {
            if (predicate(this[index])) return index
        }
        return -1
    }
}
