package com.chibialarm.app

/**
 * Compact, on-demand spoken readback of the exact local plan profile shown to the user.
 * It contains only bounded UI selections; it never reads journal notes or health free text.
 */
internal object FitnessPlanSpeechSummary {
    const val MAX_CHARACTERS = 350

    fun fromProfile(
        profileSummary: String,
        programName: String,
        estimatedMinutes: Int? = null,
        exceedsTarget: Boolean = false,
        prescribedRestSeconds: Int? = null
    ): String {
        require(profileSummary.isNotBlank() && profileSummary.length <= 5_000)
        require(programName.isNotBlank() && programName.length <= 120)
        require(estimatedMinutes == null || estimatedMinutes in 1..240)
        require(prescribedRestSeconds == null || prescribedRestSeconds in 0..3_600)

        fun field(label: String): String = profileSummary.lineSequence()
            .firstOrNull { it.startsWith("$label:") }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()

        val goal = field("Mục tiêu")
        val level = field("Trình độ")
        val energy = field("Năng lượng tự đánh giá")
        val minutes = field("Thời lượng mục tiêu").removeSuffix(" phút").trim().toIntOrNull()
            ?: throw IllegalArgumentException("Missing plan duration")
        val equipment = compactEquipment(field("Dụng cụ"))
        val impactValue = field("Ưu tiên vận động")
        val impact = compactImpact(impactValue)
        val filters = compactFilters(field("Bộ lọc động tác"))

        require(goal.isNotBlank() && level.isNotBlank() && minutes in 5..120)
        require(equipment.isNotBlank() && impact.isNotBlank() && filters.isNotBlank())

        // A reported pain/injury is a stop state, not a plan to read back.
        // Never say that a program was selected or invite the user to start it.
        val reportedPain = impactValue.contains("đau", ignoreCase = true) ||
            impactValue.contains("chấn thương", ignoreCase = true)
        if (reportedPain) {
            return "Bạn đã báo đau hoặc chấn thương. Chibi không tạo hoặc bắt đầu giáo án. " +
                "Hãy hỏi chuyên gia y tế trước khi vận động; đây không phải tư vấn y khoa."
        }

        val required = listOf(
            "Chibi chọn ${clip(programName, 24)}.",
            "Mục tiêu ${clip(goal, 20)}.",
            "Trình độ ${clip(level, 18)}.",
            "${minutes} phút.",
            "Dụng cụ ${clip(equipment, 24)}.",
            "Ưu tiên ${clip(impact, 20)}.",
            "Bộ lọc ${clip(filters, 65)}.",
            "Gợi ý offline, không phải tư vấn y khoa."
        )
        val selected = required.toMutableList()

        val optional = buildList {
            estimatedMinutes?.let {
                add(
                    if (exceedsTarget) "Ước tính ${it} phút, vượt mục tiêu."
                    else "Ước tính ${it} phút."
                )
            }
            prescribedRestSeconds?.takeIf { it > 0 }?.let {
                add("Tổng thời gian nghỉ theo giáo án: ${formatDuration(it)}.")
            }
            if (energy.isNotBlank()) add("Năng lượng ${clip(energy, 14)}.")
        }

        optional.forEach { sentence ->
            val candidate = (selected + sentence).joinToString(" ")
            if (candidate.length <= MAX_CHARACTERS) selected.add(sentence)
        }

        return selected.joinToString(" ").also {
            check(it.length <= MAX_CHARACTERS) {
                "Required plan summary exceeded the speech limit"
            }
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes > 0 && seconds > 0 -> "${minutes} phút ${seconds} giây"
            minutes > 0 -> "${minutes} phút"
            else -> "${totalSeconds} giây"
        }
    }

    private fun compactEquipment(value: String): String = value
        .replace("Không có dụng cụ", "không có")
        .replace("Tạ đơn hoặc dây kháng lực", "tạ hoặc dây")
        .replace("Ghế vững chắc, không bánh xe", "ghế vững")
        .replace("Ghế vững chắc", "ghế vững")
        .replace("Thảm tập", "thảm")
        .replace("; bổ sung: ", ", thêm ")
        .trim()

    private fun compactImpact(value: String): String = when {
        value.startsWith("Chưa khai báo") -> "theo lựa chọn hiện tại"
        value.contains("Ưu tiên bài ít tác động") -> "ít tác động, không nhảy"
        value.contains("Đã báo đau hoặc chấn thương") -> "đã báo đau/chấn thương"
        else -> value
    }

    private fun compactFilters(value: String): String = when {
        value.startsWith("Chưa bật bộ lọc") -> "không có bộ lọc bổ sung"
        else -> value
            .replace("tránh chống tay/tải qua cổ tay", "tránh tải cổ tay")
            .replace("tránh tư thế nằm hoặc quỳ sàn", "tránh sàn")
            .replace("tránh đưa tay hoặc tải lên quá đầu", "tránh với cao")
            .trim()
    }

    private fun clip(value: String, max: Int): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= max) return normalized
        val limit = max - 1
        val boundary = normalized.lastIndexOf(' ', limit)
        val end = if (boundary >= max / 2) boundary else limit
        return normalized.take(end).trimEnd() + "…"
    }
}
