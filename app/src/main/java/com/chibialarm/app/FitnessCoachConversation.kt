package com.chibialarm.app

import java.util.Locale

/**
 * In-memory, offline multi-turn coaching state. A new conversation starts on Activity creation;
 * no health-related free text is persisted or sent to a server.
 */
object FitnessCoachConversation {
    private val affirmativeReplies = setOf(
        "có", "co", "cần", "can", "đúng", "dung", "phải", "phai", "có nhé", "co nhe"
    )
    private val negativeReplies = setOf(
        "không", "khong", "không có", "khong co", "không cần", "khong can",
        "không hạn chế", "khong han che", "không nhé", "khong nhe"
    )
    // Full natural replies are accepted only while the coach is asking about movement limits.
    // Keeping these contextual prevents a phrase like "không có hạn chế" from clearing
    // an unrelated field or being mistaken for a global medical clearance.
    private val noMovementRestrictionReplies = negativeReplies + setOf(
        "không có hạn chế", "khong co han che",
        "không bị hạn chế", "khong bi han che",
        "không có hạn chế vận động", "khong co han che van dong",
        "không bị hạn chế vận động", "khong bi han che van dong",
        "vận động bình thường", "van dong binh thuong"
    )
    private val yesMovementRestrictionReplies = affirmativeReplies + setOf(
        "có hạn chế", "co han che",
        "có hạn chế vận động", "co han che van dong"
    )
    private val noWristRestrictionReplies = negativeReplies + setOf(
        "không có hạn chế cổ tay", "khong co han che co tay",
        "không cần tránh cổ tay", "khong can tranh co tay",
        "cổ tay bình thường", "co tay binh thuong"
    )
    private val noFloorRestrictionReplies = negativeReplies + setOf(
        "không có hạn chế nằm sàn", "khong co han che nam san",
        "không cần tránh nằm sàn", "khong can tranh nam san",
        "có thể nằm sàn", "co the nam san", "nằm sàn được", "nam san duoc"
    )

    private fun normalizedEquipmentUtterance(input: String): String =
        java.text.Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            java.text.Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun additiveEquipmentReply(
        previous: FitnessNaturalRequest.Result,
        input: String,
        parsed: FitnessNaturalRequest.Result
    ): FitnessNaturalRequest.Result? {
        val normalized = normalizedEquipmentUtterance(input)
        fun containsPhrase(phrase: String) =
            Regex("(?:^| )" + Regex.escape(phrase) + "(?: |$)").containsMatchIn(normalized)

        val additive = listOf("co them", "ngoai ra co", "them nua", "bo sung")
            .any(::containsPhrase)
        // A sentence that also removes/denies a tool is a replacement, not an additive update.
        val removesEquipment = listOf(
            "khong co ta", "khong co ta don", "khong dung ta", "bo ta", "bo ta don",
            "khong co day", "khong co day khang luc", "khong dung day", "bo day", "bo day khang luc",
            "khong co tham", "khong dung tham", "bo tham",
            "khong co ghe", "khong dung ghe", "bo ghe", "khong dung cu", "khong co dung cu"
        ).any(::containsPhrase)
        val reportedPrimary = parsed.equipment?.takeIf { it in 0..5 } ?: return null
        if (!additive || removesEquipment || parsed.impact == 2) return null

        val reportedTools = TrainingEquipmentAvailability.fromPreference(reportedPrimary) +
            parsed.additionalEquipment.orEmpty()
        if (reportedTools.isEmpty()) return null

        val previousPrimary = previous.equipment?.takeIf { it in 0..5 }
        val existingTools = (previousPrimary?.let(TrainingEquipmentAvailability::fromPreference).orEmpty()) +
            previous.additionalEquipment.orEmpty()
        val combinedTools = existingTools + reportedTools
        val retainedPrimary = previousPrimary?.takeIf { it != 0 &&
            TrainingEquipmentAvailability.fromPreference(it).all(combinedTools::contains)
        }
        val primary = retainedPrimary ?: reportedPrimary.takeIf { it != 0 }
            ?: when {
                TrainingEquipment.DUMBBELL in combinedTools && TrainingEquipment.RESISTANCE_BAND in combinedTools -> 2
                TrainingEquipment.DUMBBELL in combinedTools -> 4
                TrainingEquipment.RESISTANCE_BAND in combinedTools -> 5
                TrainingEquipment.MAT in combinedTools -> 1
                TrainingEquipment.STABLE_CHAIR in combinedTools -> 3
                else -> 0
            }
        val extras = combinedTools - TrainingEquipmentAvailability.fromPreference(primary)
        return parsed.copy(
            equipment = primary,
            additionalEquipment = extras,
            reply = "Đã bổ sung dụng cụ mới và giữ các lựa chọn trước đó. Kiểm tra lại danh sách dụng cụ trước khi tạo giáo án."
        )
    }

    /**
     * Interpret short answers only in the context of the question Chibi last asked.
     * Never infer that a bare "không" means there is no injury: safety constraints
     * must be reviewed explicitly in the form.
     */
    fun parseReply(previous: FitnessNaturalRequest.Result?, input: String): FitnessNaturalRequest.Result {
        val parsed = FitnessNaturalRequest.parse(input)
        if (previous == null) return parsed
        // Android speech recognizers commonly append sentence punctuation.
        // Normalize only the outer punctuation so contextual answers still match exact phrases.
        val answer = input.trim().lowercase(Locale.ROOT)
            .replace(Regex("^[\\s.,!?;:…]+|[\\s.,!?;:…]+$"), "")
            .replace(Regex("\\s+"), " ")
        val question = FitnessCoachFollowUp.nextQuestionType(previous)
        // Speech-to-text replies often omit the unit ("mười lăm" instead of
        // "mười lăm phút"). Add the unit only while Chibi is explicitly asking
        // for duration; the same words in other conversation turns stay ambiguous.
        val contextualMinutes = if (question == FitnessCoachFollowUp.Question.DURATION) {
            parsed.minutes ?: FitnessNaturalRequest.parse("$input phút").minutes
        } else null
        if (previous != null) {
            additiveEquipmentReply(previous, input, parsed)?.let { return it }
        }
        return when {
            question == FitnessCoachFollowUp.Question.SAFETY_STOP -> parsed
            question == FitnessCoachFollowUp.Question.GOAL &&
                answer in setOf("sức mạnh", "suc manh", "tăng cơ", "tang co") ->
                parsed.copy(goal = 0, reply = "Đã ghi nhận mục tiêu sức mạnh. Tao sẽ gợi ý bài phù hợp với thể lực.")
            question == FitnessCoachFollowUp.Question.GOAL &&
                answer in setOf("tim mạch", "tim mach", "cardio", "giảm cân", "giam can") ->
                parsed.copy(goal = 1, reply = "Đã ghi nhận mục tiêu tim mạch. Tao sẽ hỏi thêm về thời gian và dụng cụ.")
            question == FitnessCoachFollowUp.Question.GOAL &&
                answer in setOf("giãn cơ", "gian co", "yoga") ->
                parsed.copy(goal = 2, reply = "Đã ghi nhận mục tiêu giãn cơ. Tao sẽ ưu tiên vận động nhẹ.")
            question == FitnessCoachFollowUp.Question.LEVEL && parsed.level != null ->
                parsed.copy(
                    goal = null,
                    reply = when (parsed.level) {
                        0 -> "Đã ghi nhận mày mới bắt đầu. Tao sẽ ưu tiên cách tập cơ bản."
                        1 -> "Đã ghi nhận mày đang tập đều. Tao sẽ chọn mức vừa sức."
                        else -> "Đã ghi nhận mày có kinh nghiệm. Tao sẽ vẫn để mày kiểm tra cường độ trước khi tập."
                    }
                )
            question == FitnessCoachFollowUp.Question.DURATION && contextualMinutes != null ->
                parsed.copy(minutes = contextualMinutes, reply = "Tao đã ghi nhận $contextualMinutes phút cho buổi tập. Mày có thể trả lời câu hỏi tiếp theo.")
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf("không", "khong", "không có", "khong co", "tay không", "tay khong") ->
                parsed.copy(equipment = 0, additionalEquipment = emptySet(), reply = "Đã ghi nhận tập không dụng cụ. Tao sẽ ưu tiên bài phù hợp.")
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf("thảm", "tham", "thảm tập", "tham tap") ->
                parsed.copy(equipment = 1, additionalEquipment = emptySet(), reply = "Đã ghi nhận có thảm tập.")
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf("tạ", "ta", "tạ đơn", "ta don", "có tạ", "co ta", "có tạ đơn", "co ta don") ->
                parsed.copy(
                    equipment = 4,
                    additionalEquipment = emptySet(),
                    reply = "Đã ghi nhận có tạ đơn; tao không giả định mày có dây kháng lực."
                )
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf("dây", "day", "dây kháng lực", "day khang luc", "có dây", "co day", "có dây kháng lực", "co day khang luc") ->
                parsed.copy(
                    equipment = 5,
                    additionalEquipment = emptySet(),
                    reply = "Đã ghi nhận có dây kháng lực; tao không giả định mày có tạ đơn."
                )
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf("tạ hoặc dây", "ta hoac day", "tạ đơn hoặc dây kháng lực", "ta don hoac day khang luc") ->
                parsed.copy(
                    equipment = 2,
                    additionalEquipment = emptySet(),
                    reply = "Đã ghi nhận có tạ đơn hoặc dây kháng lực. Hãy kiểm tra lựa chọn trước khi tạo giáo án."
                )
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf(
                    "ghế", "ghe", "ghế vững", "ghe vung", "ghế vững chắc", "ghe vung chac",
                    "ghế chắc chắn", "ghe chac chan", "ghế không bánh xe", "ghe khong banh xe",
                    "ghế ổn định", "ghe on dinh"
                ) ->
                parsed.copy(
                    equipment = 3,
                    additionalEquipment = emptySet(),
                    reply = "Đã ghi nhận có ghế vững chắc. Tao sẽ ưu tiên bài có điểm tựa và cho mày xem trước."
                )
            question == FitnessCoachFollowUp.Question.MOVEMENT_LIMITS && parsed.impact != null ->
                parsed
            question == FitnessCoachFollowUp.Question.MOVEMENT_LIMITS && answer in noMovementRestrictionReplies ->
                parsed.copy(impact = 0, reply = "Đã ghi nhận không có hạn chế về nhảy. Tao vẫn sẽ kiểm tra riêng cổ tay và tư thế sàn.")
            question == FitnessCoachFollowUp.Question.MOVEMENT_LIMITS && answer in yesMovementRestrictionReplies ->
                parsed.copy(impact = 1, reply = "Đã ghi nhận ưu tiên bài ít tác động, không nhảy.")
            question == FitnessCoachFollowUp.Question.WRIST_LOAD && answer in noWristRestrictionReplies ->
                parsed.copy(avoidWristLoad = false, reply = "Đã ghi nhận không cần tránh tải qua cổ tay.")
            question == FitnessCoachFollowUp.Question.WRIST_LOAD && parsed.avoidWristLoad != null ->
                parsed.copy(reply = if (parsed.avoidWristLoad) {
                    "Đã ghi nhận cần tránh chống tay và tải qua cổ tay."
                } else {
                    "Đã ghi nhận không cần tránh tải qua cổ tay."
                })
            question == FitnessCoachFollowUp.Question.WRIST_LOAD && answer in affirmativeReplies ->
                parsed.copy(avoidWristLoad = true, reply = "Đã ghi nhận cần tránh chống tay và tải qua cổ tay.")
            question == FitnessCoachFollowUp.Question.FLOOR_POSITIONS && answer in noFloorRestrictionReplies ->
                parsed.copy(avoidFloorPositions = false, reply = "Đã ghi nhận có thể thực hiện các tư thế nằm hoặc quỳ sàn.")
            question == FitnessCoachFollowUp.Question.FLOOR_POSITIONS && parsed.avoidFloorPositions != null ->
                parsed.copy(reply = if (parsed.avoidFloorPositions) {
                    "Đã ghi nhận cần tránh các tư thế nằm hoặc quỳ sàn."
                } else {
                    "Đã ghi nhận có thể thực hiện các tư thế nằm hoặc quỳ sàn."
                })
            question == FitnessCoachFollowUp.Question.FLOOR_POSITIONS && answer in affirmativeReplies ->
                parsed.copy(avoidFloorPositions = true, reply = "Đã ghi nhận cần tránh các tư thế nằm hoặc quỳ sàn.")
            question == FitnessCoachFollowUp.Question.OVERHEAD_REACH && parsed.avoidOverheadReach != null ->
                parsed.copy(reply = if (parsed.avoidOverheadReach) {
                    "Đã ghi nhận cần tránh đưa tay hoặc tải lên quá đầu."
                } else {
                    "Đã ghi nhận có thể đưa tay hoặc tải lên quá đầu."
                })
            question == FitnessCoachFollowUp.Question.OVERHEAD_REACH && answer in negativeReplies ->
                parsed.copy(
                    avoidOverheadReach = false,
                    reply = "Đã ghi nhận không cần tránh đưa tay hoặc tải lên quá đầu."
                )
            question == FitnessCoachFollowUp.Question.OVERHEAD_REACH && answer in affirmativeReplies ->
                parsed.copy(
                    avoidOverheadReach = true,
                    reply = "Đã ghi nhận cần tránh đưa tay hoặc tải lên quá đầu."
                )
            question == FitnessCoachFollowUp.Question.ENERGY &&
                answer in setOf("mệt", "met", "hơi mệt", "hoi met", "ít năng lượng", "it nang luong") ->
                parsed.copy(energy = 0, reply = "Đã ghi nhận mức năng lượng thấp. Tao sẽ ưu tiên buổi tập nhẹ.")
            question == FitnessCoachFollowUp.Question.ENERGY &&
                answer in setOf("khỏe", "khoe", "nhiều năng lượng", "nhieu nang luong") ->
                parsed.copy(energy = 2, reply = "Đã ghi nhận mày còn nhiều năng lượng.")
            question == FitnessCoachFollowUp.Question.ENERGY &&
                answer in setOf("bình thường", "binh thuong", "vừa", "vua") ->
                parsed.copy(energy = 1, reply = "Đã ghi nhận mức năng lượng bình thường.")
            else -> parsed
        }
    }

    fun merge(previous: FitnessNaturalRequest.Result?, next: FitnessNaturalRequest.Result): FitnessNaturalRequest.Result {
        if (previous == null) return next
        return FitnessNaturalRequest.Result(
            goal = next.goal ?: previous.goal,
            level = next.level ?: previous.level,
            energy = next.energy ?: previous.energy,
            minutes = next.minutes ?: previous.minutes,
            equipment = next.equipment ?: previous.equipment,
            // Never silently downgrade an earlier injury warning in the same conversation.
            impact = if (previous.impact == 2 || next.impact == 2) 2 else next.impact ?: previous.impact,
            reply = next.reply,
            avoidWristLoad = next.avoidWristLoad ?: previous.avoidWristLoad,
            avoidFloorPositions = next.avoidFloorPositions ?: previous.avoidFloorPositions,
            avoidOverheadReach = next.avoidOverheadReach ?: previous.avoidOverheadReach,
            additionalEquipment = next.additionalEquipment ?: previous.additionalEquipment
        )
    }
}
