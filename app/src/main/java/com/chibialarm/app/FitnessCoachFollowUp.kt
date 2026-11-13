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
        WRIST_LOAD,
        FLOOR_POSITIONS,
        OVERHEAD_REACH,
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
        parsed.avoidWristLoad == null -> Question.WRIST_LOAD
        parsed.avoidFloorPositions == null -> Question.FLOOR_POSITIONS
        parsed.avoidOverheadReach == null -> Question.OVERHEAD_REACH
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
            Question.MOVEMENT_LIMITS -> {
                val noted = buildList {
                    if (parsed.avoidWristLoad == true) add("cổ tay")
                    if (parsed.avoidFloorPositions == true) add("tư thế nằm hoặc quỳ sàn")
                    if (parsed.avoidOverheadReach == true) add("đưa tay hoặc tải lên quá đầu")
                }
                if (noted.isEmpty()) {
                    "Mày có hạn chế vận động nào khiến cần tránh nhảy hoặc ưu tiên bài ít tác động không? Trả lời có/không."
                } else {
                    "Tao đã ghi nhận hạn chế ${noted.joinToString(" và ")}. Còn về nhảy, mày có cần tránh nhảy hoặc ưu tiên bài ít tác động không?"
                }
            }
            Question.WRIST_LOAD ->
                "Mày có cần tránh chống tay hoặc chịu tải qua cổ tay không? Trả lời có/không."
            Question.FLOOR_POSITIONS ->
                "Mày có cần tránh các tư thế nằm hoặc quỳ sàn không? Trả lời có/không."
            Question.OVERHEAD_REACH ->
                "Mày có cần tránh đưa tay hoặc tải lên quá đầu không? Trả lời có/không."
            Question.ENERGY ->
                "Hôm nay mày thấy mệt hay còn nhiều năng lượng?"
            Question.COMPLETE ->
                "Tao đã có đủ thông tin cơ bản. Kiểm tra các lựa chọn rồi xem giáo án nhé."
        }
}
