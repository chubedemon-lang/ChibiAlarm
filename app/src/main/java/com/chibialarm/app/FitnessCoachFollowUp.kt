package com.chibialarm.app

/** Offline follow-up prompts, not medical advice or remote AI. */
object FitnessCoachFollowUp {
    fun nextQuestion(parsed: FitnessNaturalRequest.Result): String = when {
        parsed.impact == 2 -> "Nếu đang đau hoặc chấn thương, đừng cố tập. Hãy hỏi chuyên gia y tế trước khi tiếp tục."
        parsed.goal == null -> "Mày muốn tập sức mạnh, tim mạch hay giãn cơ hôm nay?"
        parsed.minutes == null -> "Mày có bao nhiêu phút? Ví dụ: 15 phút."
        parsed.equipment == null -> "Mày có thảm, tạ, dây kháng lực hay muốn tập tay không?"
        parsed.impact == null -> "Mày có cần tránh nhảy hoặc hạn chế vận động nào không?"
        parsed.energy == null -> "Hôm nay mày thấy mệt hay còn nhiều năng lượng?"
        else -> "Tao đã có đủ thông tin cơ bản. Kiểm tra các lựa chọn rồi xem giáo án nhé."
    }
}
