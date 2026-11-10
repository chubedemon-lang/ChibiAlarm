package com.chibialarm.app

/**
 * Offline, deterministic workout suggestion. This is not a medical assessment or a
 * remote AI response. Inputs are intentionally coarse and never leave the device.
 */
object FitnessPlanAdvisor {
    data class Preferences(
        val goal: Int,
        val energy: Int,
        val level: Int,
        val minutes: Int,
        val equipment: Int,
        val impact: Int
    )
    data class Recommendation(
        val programId: String,
        val explanation: String,
        val minutes: Int,
        val requiresProfessionalReview: Boolean = false
    )

    /** impact: 0 = no restriction reported, 1 = prefer low impact, 2 = pain/injury. */
    fun recommend(p: Preferences): Recommendation {
        require(p.goal in 0..5 && p.energy in 0..2 && p.level in 0..2)
        require(p.minutes in 5..120 && p.equipment in 0..2 && p.impact in 0..2)
        if (p.impact == 2) return Recommendation(
            programId = "",
            explanation = "Bạn báo đang đau hoặc có chấn thương. Chibi tạm dừng việc tạo giáo án và không khởi chạy bài tập. Hãy hỏi chuyên gia y tế để được đánh giá trước khi vận động; nếu có đau ngực, khó thở bất thường, choáng hoặc triệu chứng nghiêm trọng, hãy tìm trợ giúp y tế ngay.",
            minutes = 0,
            requiresProfessionalReview = true
        )
        val id = when {
            p.energy == 0 -> "recovery"
            p.impact == 1 && p.goal == 1 -> "lowcardio"
            p.impact == 1 -> "mobility"
            p.minutes <= 10 && p.goal != 2 -> "starter"
            p.goal == 0 && p.level >= 1 && p.equipment == 2 -> "weighted"
            p.goal == 0 && p.level == 2 && p.equipment == 1 -> "advanced"
            p.goal == 0 && p.level == 0 -> "starter"
            p.goal == 0 -> "full"
            p.goal == 1 -> "lowcardio"
            p.goal == 2 -> "mobility"
            p.goal == 3 -> "desk"
            p.goal == 4 -> "starter"
            p.level == 0 -> "starter"
            else -> "advanced"
        }
        val why = when {
            p.energy == 0 -> "Hôm nay bạn ít năng lượng nên ưu tiên vận động nhẹ."
            p.impact == 1 -> "Bạn muốn tránh tác động mạnh nên ưu tiên bài nhẹ, không nhảy."
            p.minutes <= 10 -> "Thời gian ngắn nên ưu tiên buổi khởi động cơ bản."
            p.goal == 0 && p.level >= 1 && p.equipment == 2 ->
                "Bạn có tạ đơn nhẹ hoặc dây kháng lực và đã có nền tảng; ưu tiên bài sức mạnh với tải nhẹ, có kiểm soát."
            p.goal == 0 && p.level == 0 -> "Mới bắt đầu nên ưu tiên động tác cơ bản trước khi dùng tải."
            p.equipment == 0 -> "Ưu tiên bài tập không cần dụng cụ."
            else -> "Gợi ý theo mục tiêu và trình độ bạn chọn."
        }
        return Recommendation(id, why, p.minutes)
    }
}
