package com.chibialarm.app

/** Offline follow-up prompts, not medical advice or remote AI. */
object FitnessCoachFollowUp {
    /**
     * A stable semantic question identifier for contextual replies.
     *
     * Keep parsing decisions independent from the localized prompt wording so copy edits
     * cannot silently break spoken follow-up recognition.
     */
    enum class Question {
        SAFETY_STOP,
        GOAL,
        LEVEL,
        DURATION,
        EQUIPMENT,
        MOVEMENT_LIMITS,
        ENERGY,
        COMPLETE
    }

    fun nextQuestionType(parsed: FitnessNaturalRequest.Result): Question = when {
        parsed.impact == 2 -> Question.SAFETY_STOP
        parsed.goal == null -> Question.GOAL
        parsed.level == null -> Question.LEVEL
        parsed.minutes == null -> Question.DURATION
        parsed.equipment == null -> Question.EQUIPMENT
        parsed.impact == null -> Question.MOVEMENT_LIMITS
        parsed.energy == null -> Question.ENERGY
        else -> Question.COMPLETE
    }

    fun nextQuestion(parsed: FitnessNaturalRequest.Result): String =
        when (nextQuestionType(parsed)) {
            Question.SAFETY_STOP ->
                "Nếu đang đau hoặc chấn thương, đừng cố tập. Hãy hỏi chuyên gia y tế trước khi tiếp tục."
            Question.GOAL ->
                "Mày muốn tập sức mạnh, tim mạch hay giãn cơ hôm nay?"
            Question.LEVEL ->
                "Mày đang ở trình độ nào: mới bắt đầu, đang tập đều hay đã có kinh nghiệm?"
            Question.DURATION ->
                "Mày có bao nhiêu phút? Ví dụ: 15 phút."
            Question.EQUIPMENT ->
                "Mày có thảm, tạ, dây kháng lực hay muốn tập tay không?"
            Question.MOVEMENT_LIMITS ->
                "Mày có cần tránh nhảy hoặc hạn chế vận động nào không?"
            Question.ENERGY ->
                "Hôm nay mày thấy mệt hay còn nhiều năng lượng?"
            Question.COMPLETE ->
                "Tao đã có đủ thông tin cơ bản. Kiểm tra các lựa chọn rồi xem giáo án nhé."
        }
}
