package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Parses a narrowly phrased report of work the user says they actually completed.
 *
 * This is deliberately separate from target editing: "I did 10 reps" records a
 * self-reported set value, while "reduce to 10 reps" changes the future target.
 * The result is only a proposal; GuidedWorkoutActivity must ask for confirmation.
 */
object WorkoutSetReportCommand {
    data class Report(val value: Int, val unit: String)

    private val numberWords =
        "(?:mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin|muoi)" +
            "(?:\\s+(?:tram|linh|le|muoi|mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin)){0,3}"
    private val number = "(?:[0-9]{1,3}|$numberWords)"
    private val reportPattern = Regex(
        """^(?:toi da lam duoc|toi lam duoc|toi da lam|toi lam|""" +
            """toi da thuc hien duoc|toi thuc hien duoc|toi da thuc hien|toi thuc hien|""" +
            """da lam duoc|da lam|da thuc hien duoc|da thuc hien|""" +
            """toi da giu duoc|toi giu duoc|da giu duoc|da giu|""" +
            """toi da hoan thanh|da hoan thanh|hoan thanh|hiep nay)\s+($number)\s*""" +
            """(lan|cai|reps?|nhip|giay|s)(?:\s+(?:roi|xong))?$"""
    )

    private val smallNumbers = mapOf(
        "mot" to 1, "hai" to 2, "ba" to 3, "bon" to 4, "tu" to 4,
        "nam" to 5, "lam" to 5, "sau" to 6, "bay" to 7, "tam" to 8, "chin" to 9
    )

    private fun normalize(input: String): String {
        val protected = input.lowercase(Locale.ROOT)
            // Keep the body-part word "đầu" distinct from the symptom "đau".
            .replace(Regex("(?<![\\p{L}\\p{N}])đầu(?![\\p{L}\\p{N}])"), "headword")
            .replace('đ', 'd')
        return Normalizer.normalize(protected, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseNumber(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        var index = 0
        var total = 0
        if (words.getOrNull(1) == "tram") {
            val hundreds = smallNumbers[words[0]] ?: return null
            total = hundreds * 100
            index = 2
            if (words.getOrNull(index) == "linh" || words.getOrNull(index) == "le") index++
        }
        if (index >= words.size) return total.takeIf { it > 0 }

        val first = words[index]
        val tens: Int
        when {
            first == "muoi" -> {
                tens = 10
                index++
            }
            smallNumbers[first] != null && words.getOrNull(index + 1) == "muoi" -> {
                tens = smallNumbers.getValue(first) * 10
                index += 2
            }
            smallNumbers[first] != null && index == words.lastIndex ->
                return (total + smallNumbers.getValue(first)).takeIf { it > 0 }
            else -> return null
        }

        val ones = if (index == words.size) 0 else {
            if (index != words.lastIndex) return null
            smallNumbers[words[index]] ?: return null
        }
        return (total + tens + ones).takeIf { it > 0 }
    }

    /** Returns null unless the whole utterance is an unambiguous self-report. */
    fun parse(utterance: String): Report? {
        if (utterance.length !in 1..160) return null
        val normalized = normalize(utterance)
        val match = reportPattern.matchEntire(normalized) ?: return null
        val value = parseNumber(match.groupValues[1]) ?: return null
        val spokenUnit = match.groupValues[2]
        val unit = when (spokenUnit) {
            "lan", "cai", "rep", "reps" -> "lần"
            "nhip" -> "nhịp"
            "giay", "s" -> "giây"
            else -> return null
        }
        val maximum = if (unit == "giây") 600 else 200
        if (value !in 1..maximum) return null
        return Report(value, unit)
    }
}
