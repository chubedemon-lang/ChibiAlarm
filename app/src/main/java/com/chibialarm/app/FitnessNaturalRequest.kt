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
        val reply: String,
        val avoidWristLoad: Boolean? = null,
        val avoidFloorPositions: Boolean? = null,
        val avoidOverheadReach: Boolean? = null
    )

    private fun normalize(input: String): String {
        // Keep "đầu" distinct from "đau". Removing Vietnamese diacritics first made
        // "đầu gối" indistinguishable from "đau gối", causing false injury warnings.
        val protected = input.lowercase(Locale.ROOT)
            // Preserve lexical compounds before stripping Vietnamese diacritics:
            // "đầu gối" is a body part; "bắt đầu" is a beginner phrase, not "đau".
            .replace(Regex("đầu\\s+gối"), "kneecap")
            .replace(Regex("bắt\\s+đầu"), "beginning")
            .replace(Regex("đầu\\s+tiên"), "firstword")
            .replace('đ', 'd')
        return Normalizer.normalize(protected, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    private const val LOCATION =
        "(?:kneecap|khop\\s+goi|lung\\s+duoi|co\\s+tay|co\\s+chan|goi|lung|vai|co|hong|nguc|chan|tay|khop)"
    private const val PAIN_WORD = "(?:dau|nhuc|te|sung)"
    // Localized discomfort is treated conservatively like pain, but only when a body
    // location is present. Generic "hôm nay khó chịu" must not block a workout.
    private const val LOCALIZED_PAIN_WORD = "(?:$PAIN_WORD|kho\\s+chiu)"
    private val painBeforeLocation = Regex(
        "(?:^| )$LOCALIZED_PAIN_WORD(?:\\s+(?:o|tai|vung))?\\s+$LOCATION(?: |$)"
    )
    private val locationBeforePain = Regex(
        "(?:^| )$LOCATION\\s+(?:(khong(?:\\s+(?:con|bi|co))?|het)\\s+)?" +
            "(?:(?:dang|bi|rat|hoi)\\s+){0,2}$LOCALIZED_PAIN_WORD(?: |$)"
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
            // Group 1 is the optional negation immediately before the pain word.
            if (match.groupValues[1].isBlank()) return true
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
        fun hasWholePhrase(vararg phrases: String) = phrases.any { phrase ->
            Regex("(?:^| )" + Regex.escape(phrase) + "(?: |$)").containsMatchIn(text)
        }
        // Acute/non-local symptoms remain conservative stop signals. Musculoskeletal
        // pain is parsed separately so "đầu gối" alone is not mistaken for "đau gối".
        val symptoms = listOf(
            "chan thuong", "chong mat", "choang",
            "kho tho", "buon non", "muon ngat"
        )
        val injury = symptoms.any { phrase ->
            Regex("(?:^| )" + Regex.escape(phrase) + "(?: |$)").containsMatchIn(text)
        } || hasUnnegatedPain(text)
        val lowImpact = has("khong nhay", "dung nhay", "it tac dong", "nhe nhang", "tranh nhay")
        // A movement preference is not a pain diagnosis. Active pain still triggers the stop gate.
        // Explicitly relaxing a preference is allowed; a separate active-pain stop
        // signal below remains sticky in the conversation merge and cannot be cleared here.
        // If one utterance contains both permission and restriction, keep the safer
        // restriction. A later, unambiguous turn can still relax this preference.
        // Remove opt-out phrases before checking the generic "han che co tay" rule.
        // Otherwise "bo han che co tay" contains the restriction substring and the
        // first-match rule incorrectly keeps the restriction enabled.
        val wristRelaxations = listOf(
            "bo han che co tay", "bo tranh co tay", "khong can tranh co tay",
            "co the chong tay", "chong tay duoc", "co the ti co tay",
            "co the chong tay va nam san", "co the chong tay va quy san",
            "co the nam san va chong tay", "co the quy san va chong tay"
        )
        val wristRestrictionText = wristRelaxations.fold(text) { remaining, phrase ->
            remaining.replace(phrase, " ")
        }
        val avoidWristLoad = when {
            has(
                "khong chong tay", "tranh chong tay", "khong muon chong tay", "dung chong tay",
                "khong ti co tay", "tranh ti co tay", "khong chiu tai co tay",
                "khong chiu tai qua co tay", "dung ti co tay"
            ) || wristRestrictionText.contains("han che co tay") -> true
            wristRelaxations.any(text::contains) -> false
            else -> null
        }

        // A negated request such as "khong can tranh nam san" contains the same
        // substring as a positive restriction. Strip complete relaxation phrases
        // first, then let any remaining explicit restriction take priority.
        val floorRelaxations = listOf(
            "bo han che nam san", "bo han che quy san", "khong can tranh nam san",
            "khong can tranh quy san", "khong con can tranh nam san",
            "khong con can tranh quy san", "co the nam san", "co the quy san",
            "co the chong tay va nam san", "co the chong tay va quy san",
            "co the nam san va chong tay", "co the quy san va chong tay",
            "co the nam san va quy san", "co the quy san va nam san",
            "nam san duoc", "quy san duoc", "tap duoi san duoc"
        )
        val floorRestrictionText = floorRelaxations.fold(text) { remaining, phrase ->
            remaining.replace(phrase, " ")
        }
        val floorRestrictions = listOf(
            "khong nam san", "tranh nam san", "dung nam san",
            "khong quy san", "tranh quy san", "dung quy san",
            "khong xuong san", "tranh xuong san", "dung xuong san",
            "khong nam", "tranh nam", "dung nam", "khong quy", "tranh quy", "dung quy",
            "khong tap duoi san", "tranh bai nam san", "khong nam xuong san"
        )
        val avoidFloorPositions = when {
            floorRestrictions.any(floorRestrictionText::contains) -> true
            floorRelaxations.any(text::contains) -> false
            else -> null
        }

        // Overhead reach is a session preference, not a shoulder-pain diagnosis.
        // Strip explicit permission phrases before checking restrictions so
        // "không cần tránh đưa tay qua đầu" cannot become a false restriction.
        val overheadRelaxations = listOf(
            "bo han che dua tay qua dau", "bo tranh dua tay qua dau",
            "khong can tranh dua tay qua dau", "khong con can tranh dua tay qua dau",
            "co the dua tay qua dau", "dua tay qua dau duoc",
            "co the nang tay qua dau", "nang tay qua dau duoc",
            "co the voi tay qua dau"
        )
        val overheadRestrictionText = overheadRelaxations.fold(text) { remaining, phrase ->
            remaining.replace(phrase, " ")
        }
        val overheadRestrictions = listOf(
            "khong dua tay qua dau", "tranh dua tay qua dau", "dung dua tay qua dau",
            "khong nang tay qua dau", "tranh nang tay qua dau",
            "khong day ta qua dau", "dung day ta qua dau", "tranh dong tac qua dau",
            "khong voi tay qua dau", "han che dua tay qua dau",
            "han che dong tac qua dau"
        )
        val avoidOverheadReach = when {
            overheadRestrictions.any(overheadRestrictionText::contains) -> true
            overheadRelaxations.any(text::contains) -> false
            else -> null
        }

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
            has("moi beginning", "beginning", "moi bat dau", "tan thu") -> 4
            else -> null
        }
        val level = when {
            has("moi beginning", "beginning", "moi bat dau", "tan thu", "chua tap bao gio", "nguoi moi", "beginner") -> 0
            has("dang tap deu", "trung cap", "tap thuong xuyen", "intermediate") -> 1
            has("co kinh nghiem", "nang cao", "tap lau nam", "advanced") -> 2
            else -> null
        }
        // Equipment phrases need word boundaries and explicit negation. In particular,
        // substring matching made "không có tạ" accidentally match the positive phrase "có tạ".
        val noDumbbell = hasWholePhrase(
            "khong co ta", "khong con ta", "khong dung ta", "bo ta", "het ta",
            "khong co ta don", "khong con ta don", "khong dung ta don", "bo ta don"
        )
        val noBand = hasWholePhrase(
            "khong co day", "khong con day", "khong dung day", "bo day", "het day",
            "khong co day khang luc", "khong con day khang luc",
            "khong dung day khang luc", "bo day khang luc"
        )
        val hasDumbbell = hasWholePhrase("ta don", "co ta", "dung ta", "su dung ta") && !noDumbbell
        val hasBand = hasWholePhrase(
            "day khang luc", "co day", "dung day", "su dung day"
        ) && !noBand
        val hasMat = hasWholePhrase("tham tap", "co tham") &&
            !hasWholePhrase("khong co tham", "khong con tham", "khong dung tham", "bo tham")
        val explicitlyNoEquipment = hasWholePhrase(
            "khong dung cu", "khong co dung cu", "tay khong",
            "khong con dung cu", "het dung cu"
        )
        val equipment = when {
            // A reported available alternative wins: no dumbbell but a band is still usable.
            hasDumbbell || hasBand -> 2
            hasMat -> 1
            explicitlyNoEquipment || noDumbbell || noBand -> 0
            else -> null
        }
        val duration = parseDuration(text)
        val detected = listOf(goal, level, energy, equipment, duration).count { it != null } +
            (if (injury || lowImpact) 1 else 0) +
            (if (avoidWristLoad != null) 1 else 0) +
            (if (avoidFloorPositions != null) 1 else 0) +
            (if (avoidOverheadReach != null) 1 else 0)
        val reply = when {
            injury -> "Mày báo đau hoặc có dấu hiệu cần thận trọng. Tao đã chọn mức hạn chế vận động cao nhất. Không bắt đầu tập khi đang có triệu chứng; hãy tham khảo chuyên gia y tế. Kiểm tra các lựa chọn trước khi tiếp tục."
            avoidWristLoad == true && avoidFloorPositions == true -> "Tao đã ghi nhận mày muốn tránh tải qua cổ tay và tránh nằm/quỳ sàn. Tao sẽ lọc các động tác đã được xác định trong thư viện, không tự bịa bài thay thế. Hãy xem lại giáo án trước khi bắt đầu."
            avoidWristLoad == true -> "Tao đã ghi nhận mày muốn tránh chống tay hoặc cầm tải qua cổ tay. Tao sẽ thay các động tác phù hợp trong thư viện và cho mày xem trước. Nếu đang đau, tê hoặc sưng, đừng bắt đầu tập; hãy xin tư vấn chuyên môn."
            avoidFloorPositions == true -> "Tao đã ghi nhận mày muốn tránh các động tác phải nằm hoặc quỳ sàn. Tao sẽ bỏ các động tác đã được xác định trong thư viện; có thể cần chọn chương trình khác nếu không còn đủ bài phù hợp."
            avoidOverheadReach == true -> "Tao đã ghi nhận mày muốn tránh đưa tay hoặc tải lên quá đầu. Tao sẽ lọc những động tác đã được đánh dấu trong thư viện và cho mày xem trước; đây là lựa chọn vận động, không phải chẩn đoán vai."
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
            reply = reply,
            avoidWristLoad = avoidWristLoad,
            avoidFloorPositions = avoidFloorPositions,
            avoidOverheadReach = avoidOverheadReach
        )
    }
}
