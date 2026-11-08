package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Offline rest adjustment. Never guesses a large rest duration from unrelated
 * numbers (e.g. "bài 60"). Unsupported or ambiguous phrases use 30 seconds.
 */
object WorkoutRestRequest {
    fun seconds(utterance: String): Int {
        val text = Normalizer.normalize(utterance.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.length > 160 || !Regex("\\b(nghi|rest)\\b").containsMatchIn(text)) return 30
        val match = Regex("\\b([0-9]{1,3})\\s*(giay|gi|s|phut|minute|minutes)\\b").find(text)
            ?: return 30
        val count = match.groupValues[1].toIntOrNull() ?: return 30
        val unit = match.groupValues[2]
        val total = if (unit in setOf("phut", "minute", "minutes")) count * 60 else count
        // Prevent implausible recognition results from silently adding a huge rest.
        return total.takeIf { it in 15..120 } ?: 30
    }
}
