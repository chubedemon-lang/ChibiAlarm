package com.chibialarm.app

import java.text.Normalizer
import java.util.Locale

/**
 * Read-only review of the coarse choices collected by the offline fitness coach.
 *
 * This deliberately formats only structured selections. It never echoes the raw
 * utterance or FitnessNaturalRequest.reply, which may contain private free text.
 */
internal object FitnessCoachProfileReview {
    private const val MAX_SUMMARY_CHARS = 1_800

    private val requestPhrases = setOf(
        "tom tat ho so",
        "tom tat ho so tap",
        "tom tat lua chon",
        "tom tat cac lua chon",
        "doc lai ho so",
        "doc lai lua chon",
        "doc lai cac lua chon",
        "nhac lai ho so",
        "nhac lai lua chon",
        "xem lai ho so",
        "xem lai ho so tap",
        "kiem tra ho so tap",
        "ho so hien tai"
    )

    private val goals = listOf(
        "Tăng sức mạnh",
        "Tăng vận động tim mạch",
        "Giãn cơ và linh hoạt",
        "Giảm mỏi do ngồi nhiều",
        "Mới bắt đầu",
        "Thử thách mới"
    )
    private val levels = listOf("Mới bắt đầu", "Đang tập đều", "Có kinh nghiệm")
    private val energies = listOf("Hơi mệt", "Bình thường", "Nhiều năng lượng")
    private val equipment = listOf(
        "Không có dụng cụ",
        "Thảm tập",
        "Tạ đơn hoặc dây kháng lực",
        "Ghế vững chắc",
        "Tạ đơn",
        "Dây kháng lực"
    )

    fun isReviewRequest(input: String): Boolean {
        val normalized = normalize(input)
        return requestPhrases.any { phrase ->
            normalized == phrase || normalized.startsWith("$phrase ")
        }
    }

    fun answer(input: String, profile: FitnessNaturalRequest.Result?): String? {
        if (!isReviewRequest(input)) return null
        if (profile == null) {
            return "Chibi chưa có lựa chọn tập luyện nào trong phiên này. Hãy mô tả mục tiêu, trình độ hoặc thời lượng trước; Chibi sẽ hỏi tiếp từng mục. Chưa có giáo án nào được tạo."
        }

        val extraTools = when (val selected = profile.additionalEquipment) {
            null -> "Chưa nêu"
            emptySet<TrainingEquipment>() -> "Không có"
            else -> selected.sortedBy { it.ordinal }.joinToString(", ") { equipmentName(it) }
        }
        val movementChoices = listOf(
            "Cổ tay: " + restriction(profile.avoidWristLoad, "Tránh chống tay/tải qua cổ tay", "Không chọn tránh tải qua cổ tay"),
            "Tư thế sàn: " + restriction(profile.avoidFloorPositions, "Tránh nằm hoặc quỳ sàn", "Không chọn tránh tư thế sàn"),
            "Đưa tay quá đầu: " + restriction(profile.avoidOverheadReach, "Tránh đưa tay/tải quá đầu", "Không chọn tránh tầm với quá đầu"),
            "Quỳ gối: " + restriction(profile.avoidKneeling, "Tránh quỳ gối", "Không chọn tránh quỳ gối")
        )

        val missing = buildList {
            if (profile.goal == null) add("mục tiêu")
            if (profile.level == null) add("trình độ")
            if (profile.minutes == null) add("thời lượng")
            if (profile.equipment == null) add("dụng cụ")
            if (profile.impact == null) add("ưu tiên nhảy/ít tác động")
            if (profile.avoidWristLoad == null) add("lựa chọn cổ tay")
            if (profile.avoidFloorPositions == null) add("lựa chọn tư thế sàn")
            if (profile.avoidOverheadReach == null) add("lựa chọn đưa tay quá đầu")
            if (profile.energy == null) add("mức năng lượng")
        }

        return buildString {
            append("Tóm tắt lựa chọn tập luyện hiện tại\n")
            append("Mục tiêu: ").append(label(profile.goal, goals)).append('\n')
            append("Trình độ: ").append(label(profile.level, levels)).append('\n')
            append("Năng lượng tự đánh giá: ").append(label(profile.energy, energies)).append('\n')
            append("Thời lượng: ").append(profile.minutes?.let { "$it phút" } ?: "Chưa chọn").append('\n')
            append("Dụng cụ chính: ").append(label(profile.equipment, equipment)).append('\n')
            append("Dụng cụ bổ sung: ").append(extraTools).append('\n')
            append("Ưu tiên vận động: ").append(
                when (profile.impact) {
                    null -> "Chưa trả lời"
                    0 -> "Chưa khai báo ưu tiên hạn chế tác động; đây không phải xác nhận y tế"
                    1 -> "Ưu tiên ít tác động, không nhảy"
                    else -> "Đã báo đau hoặc chấn thương — dừng việc tạo giáo án"
                }
            ).append('\n')
            append("Bộ lọc vận động:\n")
            movementChoices.forEach { append("• ").append(it).append('\n') }

            if (profile.impact == 2) {
                append("An toàn: Chibi không tạo hoặc khởi chạy giáo án khi lựa chọn hiện tại báo đau/chấn thương. Hãy dừng tập và xin đánh giá chuyên môn phù hợp.")
            } else if (missing.isNotEmpty()) {
                append("Còn cần làm rõ: ").append(missing.joinToString(", ")).append(".\n")
                val next = FitnessCoachFollowUp.nextQuestionType(profile)
                if (next != FitnessCoachFollowUp.Question.COMPLETE &&
                    next != FitnessCoachFollowUp.Question.SAFETY_STOP
                ) {
                    append("Câu hỏi tiếp theo: ").append(FitnessCoachFollowUp.nextQuestion(profile))
                }
            } else {
                append("Đã đủ các lựa chọn cơ bản để mở bước xem giáo án. Hãy kiểm tra lại từng bài và xác nhận trước khi bắt đầu.")
            }
            append("\nCác mục trên là lựa chọn tự khai báo trong phiên, không phải chẩn đoán hay xác nhận y tế.")
        }.take(MAX_SUMMARY_CHARS)
    }

    private fun label(value: Int?, labels: List<String>): String =
        value?.let(labels::getOrNull) ?: "Chưa chọn"

    private fun restriction(value: Boolean?, restricted: String, unrestricted: String): String =
        when (value) {
            null -> "Chưa trả lời"
            true -> restricted
            false -> unrestricted
        }

    private fun equipmentName(value: TrainingEquipment): String = when (value) {
        TrainingEquipment.DUMBBELL -> "Tạ đơn"
        TrainingEquipment.RESISTANCE_BAND -> "Dây kháng lực"
        TrainingEquipment.STABLE_CHAIR -> "Ghế vững chắc"
        TrainingEquipment.MAT -> "Thảm tập"
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(
            input.lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
