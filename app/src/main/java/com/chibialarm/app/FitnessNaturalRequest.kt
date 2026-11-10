package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic Vietnamese offline preference extraction, not a remote AI or medical advice.
 * Unrecognized fields are left untouched in the UI; the user reviews before starting.
 */
object FitnessNaturalRequest {
    data class Result(
        val goal: Int? = null,
        val level: Int? = null,
        val energy: Int? = null,
        val minutes: Int? = null,
        val equipment: Int? = null,
        val impact: Int? = null,
        val reply: String
    )

    private fun normalize(input: String): String {
        // Keep "đầu" distinct from "đau". Removing Vietnamese diacritics first made
        // "đầu gối" indistinguishable from "đau gối", causing false injury warnings.
        val protected = input.lowercase(Locale.ROOT).replace("đầu", "headword").replace('đ', 'd')
        return Normalizer.normalize(protected, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    private const val LOCATION =
        "(?:headword\\s+goi|khop\\s+goi|lung\\s+duoi|co\\s+tay|co\\s+chan|headword|goi|lung|vai|co|hong|nguc|chan|tay|khop)"
    private const val PAIN_WORD = "(?:dau|nhuc|te|sung)"
    private val painBeforeLocation = Regex(
        "(?:^| )$PAIN_WORD(?:\\s+(?:o|tai|vung))?\\s+$LOCATION(?: |$)"
    )
    private val locationBeforePain = Regex(
        "(?:^| )$LOCATION\\s+(?:(khong(?:\\s+(?:con|bi|co))?|het)\\s+)?" +
            "(?:(?:dang|bi|rat|hoi)\\s+)?$PAIN_WORD(?: |$)"
    )
    private val negationAtEnd = Regex("(?:^| )(?:khong(?:\\s+(?:con|bi|co))?|het)$")
    private val standalonePain = Regex("(?:^| )$PAIN_WORD(?: |$)")

    /** Recognizes pain/symptoms in either word order while respecting nearby negation. */
    private fun hasUnnegatedPain(text: String): Boolean {
        for (match in painBeforeLocation.findAll(text)) {
            val prefix = text.substring(0, match.range.first).trimEnd()
            if (!negationAtEnd.containsMatchIn(prefix)) return true
        }
        for (match in locationBeforePain.findAll(text)) {
            // Group 1 is the location; group 2 is an optional negation.
            if (match.groupValues[2].isBlank()) return true
        }
        for (match in standalonePain.findAll(text)) {
            val prefix = text.substring(0, match.range.first).trimEnd()
            if (!negationAtEnd.containsMatchIn(prefix)) return true
        }
        return false
    }

    private val smallNumbers = mapOf(
        "mot" to 1, "hai" to 2, "ba" to 3, "bon" to 4, "tu" to 4,
        "nam" to 5, "lam" to 5, "sau" to 6, "bay" to 7, "tam" to 8, "chin" to 9
    )

    /** Parses common Vietnamese spoken numbers used for workout duration (1–120). */
    private fun spokenNumber(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        if (words.size == 1) return when (words[0]) {
            "muoi" -> 10
            else -> smallNumbers[words[0]]
        }

        var index = 0
        var total = 0
        if (words.getOrNull(index) == "mot" && words.getOrNull(index + 1) == "tram") {
            total = 100
            index += 2
            if (words.getOrNull(index) == "linh" || words.getOrNull(index) == "le") index++
        }
        if (index >= words.size) return total.takeIf { it > 0 }
        val first = words[index]
        val tens = when {
            first == "muoi" -> 10
            smallNumbers[first] != null && words.getOrNull(index + 1) == "muoi" ->
                smallNumbers.getValue(first) * 10
            else -> return null
        }
        index += if (first == "muoi") 1 else 2
        val unit = if (index < words.size) {
            if (index != words.lastIndex) return null
            smallNumbers[words[index]] ?: return null
        } else 0
        return (total + tens + unit).takeIf { it in 1..120 }
    }

    private fun parseDuration(text: String): Int? {
        if (Regex("(?:^| )nua tieng(?: |$)").containsMatchIn(text)) return 30
        val number = "(?:[0-9]{1,3}|mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin|muoi)(?:\\s+(?:tram|linh|le|muoi|mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin))*"
        val match = Regex("(?:^| )($number)\\s*(phut|minute|min|p|tieng|gio)(?: |$)")
            .find(text) ?: return null
        // Do not silently round fractional spoken durations such as "15 phút rưỡi".
        val tail = text.substring(match.range.last + 1).trimStart()
        if (tail.startsWith("ruoi")) return null
        val amount = spokenNumber(match.groupValues[1]) ?: return null
        val minutes = when (match.groupValues[2]) {
            "tieng", "gio" -> amount.toLong() * 60L
            else -> amount.toLong()
        }
        return minutes.takeIf { it in 5L..120L }?.toInt()
    }

    fun parse(input: String): Result {
        val text = normalize(input.take(1000))
        fun has(vararg phrases: String) = phrases.any { text.contains(it) }
        // Acute/non-local symptoms remain conservative stop signals. Musculoskeletal
        // pain is parsed separately so "đầu gối" alone is not mistaken for "đau gối".
        val symptoms = listOf(
            "chan thuong", "dang dau", "bi dau", "chong mat", "choang",
            "kho tho", "buon non", "muon ngat"
        )
        val injury = symptoms.any { phrase ->
            Regex("(?:^| )" + Regex.escape(phrase) + "(?: |$)").containsMatchIn(text)
        } || hasUnnegatedPain(text)
        val lowImpact = has("khong nhay", "it tac dong", "nhe nhang", "tranh nhay")
        val energy = when {
            has("met", "het suc", "it nang luong") -> 0
            has("khoe", "nhieu nang luong", "sung suc") -> 2
            else -> null
        }
        val goal = when {
            has("gian co", "linh hoat", "yoga") -> 2
            has("ngoi lau", "van phong", "moi lung") -> 3
            has("tim mach", "cardio", "dot mo", "giam can", "giam mo", "suc ben", "ben bi") -> 1
            has("suc manh", "tang co", "co bap", "tang suc manh", "phat trien co") -> 0
            has("moi bat dau", "tan thu") -> 4
            else -> null
        }
        val level = when {
            has("moi bat dau", "tan thu", "chua tap bao gio", "nguoi moi", "beginner") -> 0
            has("dang tap deu", "trung cap", "tap thuong xuyen", "intermediate") -> 1
            has("co kinh nghiem", "nang cao", "tap lau nam", "advanced") -> 2
            else -> null
        }
        val equipment = when {
            has("khong dung cu", "khong co dung cu", "tay khong") -> 0
            has("ta don", "day khang luc", "co ta") -> 2
            has("tham tap", "co tham") -> 1
            else -> null
        }
        val duration = parseDuration(text)
        val detected = listOf(goal, level, energy, equipment, duration).count { it != null } +
            if (injury || lowImpact) 1 else 0
        val reply = when {
            injury -> "Mày báo đau hoặc có dấu hiệu cần thận trọng. Tao đã chọn mức hạn chế vận động cao nhất. Không bắt đầu tập khi đang có triệu chứng; hãy tham khảo chuyên gia y tế. Kiểm tra các lựa chọn trước khi tiếp tục."
            detected == 0 -> "Tao chưa hiểu rõ mục tiêu. Hãy thử: 'giãn cơ 15 phút không dụng cụ'. Mày vẫn có thể chọn thủ công."
            else -> "Tao đã điền những thông tin nhận ra vào biểu mẫu. Kiểm tra lại mục tiêu, thời gian, dụng cụ và hạn chế vận động rồi bấm 'Gợi ý chương trình phù hợp'. Đây là gợi ý offline, không phải tư vấn y khoa."
        }
        return Result(
            goal = goal,
            level = level,
            energy = energy,
            minutes = duration,
            equipment = equipment,
            impact = if (injury) 2 else if (lowImpact) 1 else null,
            reply = reply
        )
    }
}
