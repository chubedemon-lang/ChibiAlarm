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
        val energy: Int? = null,
        val minutes: Int? = null,
        val equipment: Int? = null,
        val impact: Int? = null,
        val reply: String
    )

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun parse(input: String): Result {
        val text = normalize(input.take(1000))
        fun has(vararg phrases: String) = phrases.any { text.contains(it) }
        val injury = has("dau nguc", "chan thuong", "dang dau", "bi dau", "dau khop", "chong mat", "kho tho")
        val lowImpact = has("khong nhay", "it tac dong", "nhe nhang", "tranh nhay")
        val energy = when {
            has("met", "het suc", "it nang luong") -> 0
            has("khoe", "nhieu nang luong", "sung suc") -> 2
            else -> null
        }
        val goal = when {
            has("gian co", "linh hoat", "yoga") -> 2
            has("ngoi lau", "van phong", "moi lung") -> 3
            has("tim mach", "cardio", "dot mo", "giam can", "giam mo", "suc ben", "ben bi") -> 1
            has("suc manh", "tang co", "co bap", "tang suc manh", "phat trien co") -> 0
            has("moi bat dau", "tan thu") -> 4
            else -> null
        }
        val equipment = when {
            has("khong dung cu", "khong co dung cu", "tay khong") -> 0
            has("ta don", "day khang luc", "co ta") -> 2
            has("tham tap", "co tham") -> 1
            else -> null
        }
        val duration = Regex("\\b(\\d{1,3})\\s*(phut|minute|min)\\b")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 5..120 }
        val detected = listOf(goal, energy, equipment, duration).count { it != null } +
            if (injury || lowImpact) 1 else 0
        val reply = when {
            injury -> "Mày báo đau hoặc có dấu hiệu cần thận trọng. Tao đã chọn mức hạn chế vận động cao nhất. Không bắt đầu tập khi đang có triệu chứng; hãy tham khảo chuyên gia y tế. Kiểm tra các lựa chọn trước khi tiếp tục."
            detected == 0 -> "Tao chưa hiểu rõ mục tiêu. Hãy thử: 'giãn cơ 15 phút không dụng cụ'. Mày vẫn có thể chọn thủ công."
            else -> "Tao đã điền những thông tin nhận ra vào biểu mẫu. Kiểm tra lại mục tiêu, thời gian, dụng cụ và hạn chế vận động rồi bấm 'Gợi ý chương trình phù hợp'. Đây là gợi ý offline, không phải tư vấn y khoa."
        }
        return Result(goal, energy, duration, equipment, if (injury) 2 else if (lowImpact) 1 else null, reply)
    }
}
