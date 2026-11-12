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

    /**
     * Interpret short answers only in the context of the question Chibi last asked.
     * Never infer that a bare "không" means there is no injury: safety constraints
     * must be reviewed explicitly in the form.
     */
    fun parseReply(previous: FitnessNaturalRequest.Result?, input: String): FitnessNaturalRequest.Result {
        val parsed = FitnessNaturalRequest.parse(input)
        if (previous == null) return parsed
        val answer = input.trim().lowercase(Locale.ROOT)
        val question = FitnessCoachFollowUp.nextQuestionType(previous)
        // Speech-to-text replies often omit the unit ("mười lăm" instead of
        // "mười lăm phút"). Add the unit only while Chibi is explicitly asking
        // for duration; the same words in other conversation turns stay ambiguous.
        val contextualMinutes = if (question == FitnessCoachFollowUp.Question.DURATION) {
            parsed.minutes ?: FitnessNaturalRequest.parse("$input phút").minutes
        } else null
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
                parsed.copy(equipment = 0, reply = "Đã ghi nhận tập không dụng cụ. Tao sẽ ưu tiên bài phù hợp.")
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf("thảm", "tham", "thảm tập", "tham tap") ->
                parsed.copy(equipment = 1, reply = "Đã ghi nhận có thảm tập.")
            question == FitnessCoachFollowUp.Question.EQUIPMENT &&
                answer in setOf("tạ", "ta", "tạ đơn", "ta don", "dây", "day", "dây kháng lực", "day khang luc") ->
                parsed.copy(equipment = 2, reply = "Đã ghi nhận có tạ hoặc dây kháng lực. Hãy kiểm tra dụng cụ được chọn.")
            question == FitnessCoachFollowUp.Question.MOVEMENT_LIMITS && parsed.impact != null ->
                parsed
            question == FitnessCoachFollowUp.Question.MOVEMENT_LIMITS && answer in negativeReplies ->
                parsed.copy(impact = 0, reply = "Đã ghi nhận không có hạn chế về nhảy. Tao vẫn sẽ kiểm tra riêng cổ tay và tư thế sàn.")
            question == FitnessCoachFollowUp.Question.MOVEMENT_LIMITS && answer in affirmativeReplies ->
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
            avoidFloorPositions = next.avoidFloorPositions ?: previous.avoidFloorPositions
        )
    }
}
