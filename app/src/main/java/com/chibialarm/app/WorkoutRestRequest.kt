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
        val spoken = Regex("\\b(mot phut ruoi|mot phut|hai phut|mot tram hai muoi giay|chin muoi giay|sau muoi giay|ba muoi giay)\\b")
            .find(text)?.value
        val spokenSeconds = when (spoken) {
            "mot phut ruoi", "chin muoi giay" -> 90
            "mot phut", "sau muoi giay" -> 60
            "hai phut", "mot tram hai muoi giay" -> 120
            "ba muoi giay" -> 30
            else -> null
        }
        if (spokenSeconds != null) return spokenSeconds
        val match = Regex("\\b([0-9]{1,3})\\s*(giay|gi|s|phut|minute|minutes)\\b").find(text)
            ?: return 30
        val count = match.groupValues[1].toIntOrNull() ?: return 30
        val unit = match.groupValues[2]
        val total = if (unit in setOf("phut", "minute", "minutes")) count * 60 else count
        // Prevent implausible recognition results from silently adding a huge rest.
        return total.takeIf { it in 15..120 } ?: 30
    }
}
