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
        val avoidOverheadReach: Boolean? = null,
        // Additional tools are explicit session choices; null means this utterance said nothing about them.
        val additionalEquipment: Set<TrainingEquipment>? = null,
        val avoidKneeling: Boolean? = null
    )

    private fun normalize(input: String): String {
        // Keep "đầu" distinct from "đau". Removing Vietnamese diacritics first made
        // "đầu gối" indistinguishable from "đau gối", causing false injury warnings.
        val protected = input.lowercase(Locale.ROOT)
            // Keep a numeric range explicit before punctuation normalization turns
            // "15–20" into two unrelated numbers. The duration parser will ask for
            // one exact target instead of silently selecting an endpoint.
            .replace(Regex("(?<=\\d)\\s*[-–—]\\s*(?=\\d)"), " den ")
            // Preserve lexical compounds before stripping Vietnamese diacritics:
            // "đầu gối" is a body part; "bắt đầu" is a beginner phrase, not "đau".
            .replace(Regex("đầu\\s+gối"), "kneecap")
            .replace(Regex("bắt\\s+đầu"), "beginning")
            .replace(Regex("đầu\\s+tiên"), "firstword")
            // "bổ sung" means adding workout volume, not swelling. Protect the compound
            // before accent removal so the standalone symptom token "sung" cannot match it.
            .replace(Regex("bổ\\s+sung"), "supplementword")
            // "đầu" means head in overhead-movement phrases, while "đau" means pain.
            // Protect the standalone body-part word before removing Vietnamese diacritics.
            .replace(Regex("(?<![\\p{L}\\p{N}])đầu(?![\\p{L}\\p{N}])"), "headword")
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
    private val negatedEnergyPrefix = Regex(
        "(?:^| )(?:khong(?:\\s+(?:con|bi|co|qua|that)){0,2}|chua|het)$"
    )

    /** Match a complete colloquial phrase without treating an explicit denial as fatigue. */
    private fun hasUnnegatedPhrase(text: String, phrases: List<String>): Boolean {
        for (phrase in phrases) {
            val pattern = Regex("(?:^| )" + Regex.escape(phrase) + "(?: |$)")
            for (match in pattern.findAll(text)) {
                val prefix = text.substring(0, match.range.first).trimEnd()
                if (!negatedEnergyPrefix.containsMatchIn(prefix)) return true
            }
        }
        return false
    }

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

    private const val DURATION_NUMBER =
        "(?:[0-9]{1,3}|mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin|muoi)(?:\\s+(?:tram|linh|le|muoi|mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin))*"
    private const val DURATION_UNIT = "(?:phut|minute|min|p|tieng|gio)"
    // Lookarounds keep the whitespace between adjacent mentions available to findAll().
    // Consuming the boundary space made "1 tiếng 30 phút" look like only one duration.
    private val durationMention = Regex("(?<![a-z0-9])($DURATION_NUMBER)\\s*($DURATION_UNIT)(?![a-z0-9])")
    private val halfHourMention = Regex("(?<![a-z0-9])nua (?:tieng|gio)(?![a-z0-9])")
    private val durationRange = Regex(
        "(?<![a-z0-9])(?:tu\\s+)?($DURATION_NUMBER)(?:\\s*$DURATION_UNIT)?\\s+(?:den|toi|hoac)\\s+($DURATION_NUMBER)(?:\\s*$DURATION_UNIT)?(?![a-z0-9])"
    )
    private val durationBound = Regex(
        "(?<![a-z0-9])(?:tu|toi da|it nhat|it hon|duoi|tren|tro len|hon|khong qua)\\s+$DURATION_NUMBER(?:\\s*$DURATION_UNIT)?(?![a-z0-9])"
    )
    private val durationTailBound = Regex(
        "(?<![a-z0-9])$DURATION_NUMBER\\s*$DURATION_UNIT\\s+(?:tro len|tro xuong|hon|hoac it nhat|hoac it hon)(?![a-z0-9])"
    )

    /**
     * A duration range, bound ("at most/at least"), or fractional/multiple duration
     * is deliberately left unresolved. A personalized plan needs one exact time;
     * choosing the first number from speech recognition would silently change intent.
     */
    fun needsDurationClarification(input: String): Boolean =
        hasAmbiguousDuration(normalize(input.take(1000)))

    private fun hasAmbiguousDuration(text: String): Boolean {
        val mentions = durationMention.findAll(text).toList()
        val halfHours = halfHourMention.findAll(text).toList()
        val fractional = (mentions + halfHours).any { match ->
            text.substring(match.range.last + 1).trimStart().startsWith("ruoi")
        }
        return durationRange.containsMatchIn(text) ||
            durationBound.containsMatchIn(text) ||
            durationTailBound.containsMatchIn(text) ||
            mentions.size > 1 ||
            halfHours.size > 1 ||
            (mentions.isNotEmpty() && halfHours.isNotEmpty()) ||
            fractional
    }

    private fun parseDuration(text: String): Int? {
        if (hasAmbiguousDuration(text)) return null
        if (halfHourMention.containsMatchIn(text)) return 30
        val match = durationMention.find(text) ?: return null
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
            "khong nam", "tranh nam", "dung nam",
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
            "bo han che dua tay qua headword", "bo tranh dua tay qua headword",
            "khong can tranh dua tay qua headword", "khong con can tranh dua tay qua headword",
            "co the dua tay qua headword", "dua tay qua headword duoc",
            "co the nang tay qua headword", "nang tay qua headword duoc",
            "co the voi tay qua headword"
        )
        val overheadRestrictionText = overheadRelaxations.fold(text) { remaining, phrase ->
            remaining.replace(phrase, " ")
        }
        val overheadRestrictions = listOf(
            "khong dua tay qua headword", "tranh dua tay qua headword", "dung dua tay qua headword",
            "khong nang tay qua headword", "tranh nang tay qua headword",
            "khong day ta qua headword", "dung day ta qua headword", "tranh dong tac qua headword",
            "khong voi tay qua headword", "han che dua tay qua headword",
            "han che dong tac qua headword"
        )
        val avoidOverheadReach = when {
            overheadRestrictions.any(overheadRestrictionText::contains) -> true
            overheadRelaxations.any(text::contains) -> false
            else -> null
        }

        // Kneeling is a distinct session preference: it must not ban supine work.
        // Explicit pain such as "đau đầu gối" remains handled by the safety-stop parser above.
        val kneelingRelaxations = listOf(
            "bo han che quy goi", "bo tranh quy goi", "khong can tranh quy goi",
            "co the quy goi", "quy goi duoc"
        )
        val kneelingRestrictionText = kneelingRelaxations.fold(text) { remaining, phrase ->
            remaining.replace(phrase, " ")
        }
        val kneelingRestrictions = listOf(
            "khong quy goi", "tranh quy goi", "dung quy goi",
            "khong muon quy goi", "khong the quy goi", "han che quy goi"
        )
        val avoidKneeling = when {
            kneelingRestrictions.any(kneelingRestrictionText::contains) -> true
            kneelingRelaxations.any(text::contains) -> false
            else -> null
        }

        val lowEnergyPhrases = listOf(
            "met", "het suc", "it nang luong", "tap nhe hon",
            "buoi tap nhe hon", "uu tien van dong nhe", "hoi duoi",
            "duoi suc", "het pin", "moi co", "co bap moi", "chan moi", "hoi moi"
        )
        val highEnergyPhrases = listOf("khoe", "nhieu nang luong", "sung suc")
        val muscleSoreness = hasUnnegatedPhrase(
            text, listOf("moi co", "co bap moi", "chan moi", "hoi moi")
        )
        val energy = when {
            hasUnnegatedPhrase(text, lowEnergyPhrases) -> 0
            hasUnnegatedPhrase(text, highEnergyPhrases) -> 2
            else -> null
        }
        val goal = when {
            has("gian co", "linh hoat", "yoga") -> 2
            has("ngoi lau", "van phong", "moi lung") -> 3
            has("tim mach", "cardio", "dot mo", "giam can", "giam mo", "suc ben", "ben bi") -> 1
            has("suc manh", "tang co", "co bap", "tang suc manh", "phat trien co") &&
                !muscleSoreness -> 0
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
        val noChair = hasWholePhrase(
            "khong co ghe", "khong con ghe", "khong dung ghe", "bo ghe", "het ghe"
        )
        val hasChair = hasWholePhrase(
            "ghe vung chac", "ghe chac chan", "ghe khong banh xe", "ghe on dinh",
            "co ghe", "dung ghe"
        ) && !noChair
        val explicitlyNoEquipment = hasWholePhrase(
            "khong dung cu", "khong co dung cu", "tay khong",
            "khong con dung cu", "het dung cu"
        )
        val hasResistanceChoice = hasWholePhrase(
            "ta hoac day", "ta don hoac day khang luc",
            "co ta hoac day", "co ta don hoac day khang luc"
        )
        val equipment = when {
            // Preserve exact resistance-tool availability instead of treating a band as a dumbbell.
            (hasDumbbell && hasBand) || hasResistanceChoice -> 2
            hasDumbbell -> 4
            hasBand -> 5
            hasMat -> 1
            hasChair -> 3
            explicitlyNoEquipment || noDumbbell || noBand || noChair -> 0
            else -> null
        }
        val primaryEquipment = equipment?.let(TrainingEquipmentAvailability::fromPreference).orEmpty()
        val additionalEquipment = equipment?.let {
            val reported = buildSet {
                if (hasMat) add(TrainingEquipment.MAT)
                if (hasDumbbell) add(TrainingEquipment.DUMBBELL)
                if (hasBand) add(TrainingEquipment.RESISTANCE_BAND)
                if (hasChair) add(TrainingEquipment.STABLE_CHAIR)
            }
            reported - primaryEquipment
        }
        val duration = parseDuration(text)
        val detected = listOf(goal, level, energy, equipment, duration).count { it != null } +
            (if (injury || lowImpact) 1 else 0) +
            (if (avoidWristLoad != null) 1 else 0) +
            (if (avoidFloorPositions != null) 1 else 0) +
            (if (avoidOverheadReach != null) 1 else 0) +
            (if (avoidKneeling != null) 1 else 0)
        val reply = when {
            injury -> "Mày báo đau hoặc có dấu hiệu cần thận trọng. Tao đã chọn mức hạn chế vận động cao nhất. Không bắt đầu tập khi đang có triệu chứng; hãy tham khảo chuyên gia y tế. Kiểm tra các lựa chọn trước khi tiếp tục."
            avoidWristLoad == true && avoidFloorPositions == true -> "Tao đã ghi nhận mày muốn tránh tải qua cổ tay và tránh nằm/quỳ sàn. Tao sẽ lọc các động tác đã được xác định trong thư viện, không tự bịa bài thay thế. Hãy xem lại giáo án trước khi bắt đầu."
            avoidWristLoad == true -> "Tao đã ghi nhận mày muốn tránh chống tay hoặc cầm tải qua cổ tay. Tao sẽ thay các động tác phù hợp trong thư viện và cho mày xem trước. Nếu đang đau, tê hoặc sưng, đừng bắt đầu tập; hãy xin tư vấn chuyên môn."
            avoidFloorPositions == true -> "Tao đã ghi nhận mày muốn tránh các động tác phải nằm hoặc quỳ sàn. Tao sẽ bỏ các động tác đã được xác định trong thư viện; có thể cần chọn chương trình khác nếu không còn đủ bài phù hợp."
            avoidKneeling == true -> "Tao đã ghi nhận mày muốn tránh động tác phải quỳ gối. Lựa chọn này được lọc riêng với các bài nằm ngửa hoặc ngồi; hãy kiểm tra giáo án trước khi bắt đầu."
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
            avoidOverheadReach = avoidOverheadReach,
            additionalEquipment = additionalEquipment,
            avoidKneeling = avoidKneeling
        )
    }
}
