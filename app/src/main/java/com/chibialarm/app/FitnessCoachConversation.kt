package com.chibialarm.app

import java.util.Locale

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
        val answer = input.trim().lowercase(Locale.ROOT)
        val question = FitnessCoachFollowUp.nextQuestion(previous).lowercase(Locale.ROOT)
        val shortMinutes = Regex("^([0-9]{1,3})$").matchEntire(answer)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 5..120 }
        return when {
            previous.impact == 2 -> parsed
            previous.goal == null && question.contains("sức mạnh, tim mạch") &&
                answer in setOf("sức mạnh", "suc manh", "tăng cơ", "tang co") ->
                parsed.copy(goal = 0, reply = "Đã ghi nhận mục tiêu sức mạnh. Tao sẽ gợi ý bài phù hợp với thể lực.")
            previous.goal == null && question.contains("sức mạnh, tim mạch") &&
                answer in setOf("tim mạch", "tim mach", "cardio", "giảm cân", "giam can") ->
                parsed.copy(goal = 1, reply = "Đã ghi nhận mục tiêu tim mạch. Tao sẽ hỏi thêm về thời gian và dụng cụ.")
            previous.goal == null && question.contains("sức mạnh, tim mạch") &&
                answer in setOf("giãn cơ", "gian co", "yoga") ->
                parsed.copy(goal = 2, reply = "Đã ghi nhận mục tiêu giãn cơ. Tao sẽ ưu tiên vận động nhẹ.")
            previous.level == null && question.contains("trình độ") && parsed.level != null ->
                parsed.copy(
                    goal = null,
                    reply = when (parsed.level) {
                        0 -> "Đã ghi nhận mày mới bắt đầu. Tao sẽ ưu tiên cách tập cơ bản."
                        1 -> "Đã ghi nhận mày đang tập đều. Tao sẽ chọn mức vừa sức."
                        else -> "Đã ghi nhận mày có kinh nghiệm. Tao sẽ vẫn để mày kiểm tra cường độ trước khi tập."
                    }
                )
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
            previous.impact == null && question.contains("hạn chế vận động") &&
                answer in setOf("không", "khong", "không có", "khong co", "không hạn chế", "khong han che") ->
                parsed.copy(impact = 0, reply = "Đã ghi nhận mày không khai báo hạn chế vận động. Nếu thấy đau hoặc chóng mặt, hãy dừng tập.")
            previous.impact == null && question.contains("hạn chế vận động") &&
                answer in setOf("tránh nhảy", "tranh nhay", "không nhảy", "khong nhay", "ít tác động", "it tac dong") ->
                parsed.copy(impact = 1, reply = "Đã ghi nhận ưu tiên bài ít tác động, không nhảy.")
            previous.energy == null && question.contains("mệt") &&
                answer in setOf("mệt", "met", "hơi mệt", "hoi met", "ít năng lượng", "it nang luong") ->
                parsed.copy(energy = 0, reply = "Đã ghi nhận mức năng lượng thấp. Tao sẽ ưu tiên buổi tập nhẹ.")
            previous.energy == null && question.contains("mệt") &&
                answer in setOf("khỏe", "khoe", "nhiều năng lượng", "nhieu nang luong") ->
                parsed.copy(energy = 2, reply = "Đã ghi nhận mày còn nhiều năng lượng.")
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
            level = next.level ?: previous.level,
            energy = next.energy ?: previous.energy,
            minutes = next.minutes ?: previous.minutes,
            equipment = next.equipment ?: previous.equipment,
            // Never silently downgrade an earlier injury warning in the same conversation.
            impact = if (previous.impact == 2 || next.impact == 2) 2 else next.impact ?: previous.impact,
            reply = next.reply
        )
    }
}
