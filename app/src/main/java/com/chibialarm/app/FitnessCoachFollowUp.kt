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

    data class QuickReply(val label: String, val utterance: String)

    /**
     * Short, optional answers for the current question. These only prefill the input;
     * the user must review and submit them. A safety stop deliberately has no bypass chip.
     */
    fun quickReplies(parsed: FitnessNaturalRequest.Result): List<QuickReply> =
        when (nextQuestionType(parsed)) {
            Question.GOAL -> listOf(
                QuickReply("Sức mạnh", "sức mạnh"),
                QuickReply("Tim mạch", "tim mạch"),
                QuickReply("Giãn cơ", "giãn cơ")
            )
            Question.LEVEL -> listOf(
                QuickReply("Mới bắt đầu", "mới bắt đầu"),
                QuickReply("Đang tập đều", "đang tập đều"),
                QuickReply("Có kinh nghiệm", "có kinh nghiệm")
            )
            Question.DURATION -> listOf(
                QuickReply("10 phút", "10 phút"),
                QuickReply("15 phút", "15 phút"),
                QuickReply("20 phút", "20 phút"),
                QuickReply("30 phút", "30 phút")
            )
            Question.EQUIPMENT -> listOf(
                QuickReply("Không dụng cụ", "tay không"),
                QuickReply("Thảm tập", "thảm tập"),
                QuickReply("Tạ đơn", "tạ đơn"),
                QuickReply("Dây kháng lực", "dây kháng lực"),
                QuickReply("Ghế vững", "ghế vững chắc"),
                QuickReply("Thảm + ghế", "thảm tập và ghế vững chắc"),
                QuickReply("Tạ + dây + thảm", "thảm tập, tạ đơn và dây kháng lực")
            )
            Question.MOVEMENT_LIMITS -> listOf(
                QuickReply("Tránh nhảy", "tránh nhảy"),
                QuickReply("Không hạn chế nhảy", "không có hạn chế vận động")
            )
            Question.WRIST_LOAD -> listOf(
                QuickReply("Cần tránh", "có"),
                QuickReply("Không cần tránh", "không")
            )
            Question.FLOOR_POSITIONS -> listOf(
                QuickReply("Tránh nằm/quỳ sàn", "có"),
                QuickReply("Có thể nằm/quỳ sàn", "không")
            )
            Question.OVERHEAD_REACH -> listOf(
                QuickReply("Tránh đưa tay quá đầu", "có"),
                QuickReply("Có thể đưa tay quá đầu", "không")
            )
            Question.ENERGY -> listOf(
                QuickReply("Hơi mệt", "hơi mệt"),
                QuickReply("Hơi đuối", "hơi đuối"),
                QuickReply("Mỏi cơ", "mỏi cơ"),
                QuickReply("Bình thường", "bình thường"),
                QuickReply("Nhiều năng lượng", "nhiều năng lượng")
            )
            Question.SAFETY_STOP, Question.COMPLETE -> emptyList()
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
                "Mày có thảm, tạ đơn, dây kháng lực, ghế vững chắc hay muốn tập tay không?"
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
