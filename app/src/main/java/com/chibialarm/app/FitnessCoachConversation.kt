package com.chibialarm.app

/**
 * In-memory, offline multi-turn coaching state. A new conversation starts on Activity creation;
 * no health-related free text is persisted or sent to a server.
 */
object FitnessCoachConversation {
    /**
     * Interpret short answers only in the context of the question Chibi last asked.
     * Never infer that a bare "không" means there is no injury: safety constraints
     * must be reviewed explicitly in the form.
     */
    fun parseReply(previous: FitnessNaturalRequest.Result?, input: String): FitnessNaturalRequest.Result {
        val parsed = FitnessNaturalRequest.parse(input)
        if (previous == null) return parsed
        val answer = input.trim().lowercase()
        val question = FitnessCoachFollowUp.nextQuestion(previous)
        val shortMinutes = Regex("^([0-9]{1,3})$").matchEntire(answer)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 5..120 }
        return when {
            previous.impact == 2 -> parsed
            previous.minutes == null && question.contains("bao nhiêu phút") && shortMinutes != null ->
                parsed.copy(minutes = shortMinutes, reply = "Tao đã ghi nhận $shortMinutes phút cho buổi tập. Mày có thể trả lời câu hỏi tiếp theo.")
            previous.equipment == null && question.contains("thảm, tạ") &&
                answer in setOf("không", "khong", "không có", "khong co", "tay không", "tay khong") ->
                parsed.copy(equipment = 0, reply = "Đã ghi nhận tập không dụng cụ. Tao sẽ ưu tiên bài phù hợp.")
            previous.equipment == null && question.contains("thảm, tạ") &&
                answer in setOf("thảm", "tham", "thảm tập", "tham tap") ->
                parsed.copy(equipment = 1, reply = "Đã ghi nhận có thảm tập.")
            previous.equipment == null && question.contains("thảm, tạ") &&
                answer in setOf("tạ", "ta", "tạ đơn", "ta don", "dây", "day", "dây kháng lực", "day khang luc") ->
                parsed.copy(equipment = 2, reply = "Đã ghi nhận có tạ hoặc dây kháng lực. Hãy kiểm tra dụng cụ được chọn.")
            previous.energy == null && question.contains("mệt") &&
                answer in setOf("bình thường", "binh thuong", "vừa", "vua") ->
                parsed.copy(energy = 1, reply = "Đã ghi nhận mức năng lượng bình thường.")
            else -> parsed
        }
    }

    fun merge(previous: FitnessNaturalRequest.Result?, next: FitnessNaturalRequest.Result): FitnessNaturalRequest.Result {
        if (previous == null) return next
        return FitnessNaturalRequest.Result(
            goal = next.goal ?: previous.goal,
            energy = next.energy ?: previous.energy,
            minutes = next.minutes ?: previous.minutes,
            equipment = next.equipment ?: previous.equipment,
            // Never silently downgrade an earlier injury warning in the same conversation.
            impact = if (previous.impact == 2 || next.impact == 2) 2 else next.impact ?: previous.impact,
            reply = next.reply
        )
    }
}
