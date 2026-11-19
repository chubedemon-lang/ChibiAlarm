package com.chibialarm.app

/**
 * Compact, on-demand spoken readback of the exact local plan profile shown to the user.
 * It contains only bounded UI selections; it never reads journal notes or health free text.
 */
internal object FitnessPlanSpeechSummary {
    const val MAX_CHARACTERS = 350

    fun fromProfile(profileSummary: String, programName: String): String {
        require(profileSummary.isNotBlank() && profileSummary.length <= 5_000)
        require(programName.isNotBlank() && programName.length <= 120)

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
        val impact = compactImpact(field("Ưu tiên vận động"))
        val filters = compactFilters(field("Bộ lọc động tác"))

        require(goal.isNotBlank() && level.isNotBlank() && minutes in 5..120)
        require(equipment.isNotBlank() && impact.isNotBlank() && filters.isNotBlank())

        val required = listOf(
            "Chibi chọn ${clip(programName, 40)}.",
            "Mục tiêu ${clip(goal, 30)}.",
            "Trình độ ${clip(level, 25)}.",
            "${minutes} phút.",
            "Dụng cụ ${clip(equipment, 40)}.",
            "Ưu tiên ${clip(impact, 28)}.",
            "Bộ lọc ${clip(filters, 65)}.",
            "Gợi ý offline, không phải tư vấn y khoa."
        )
        val withEnergy = required.toMutableList().apply {
            add(3, "Năng lượng ${clip(energy, 18)}.")
        }
        val spoken = withEnergy.joinToString(" ")
        return if (spoken.length <= MAX_CHARACTERS) {
            spoken
        } else {
            required.joinToString(" ").also {
                check(it.length <= MAX_CHARACTERS) { "Required plan summary exceeded the speech limit" }
            }
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
        value.contains("Đã báo đau hoặc chấn thương") -> "đã báo đau/chấn thương; không tạo giáo án"
        else -> value
    }

    private fun compactFilters(value: String): String = when {
        value.startsWith("Chưa bật bộ lọc") -> "không có bộ lọc bổ sung"
        else -> value
            .replace("tránh chống tay/tải qua cổ tay", "tránh tải cổ tay")
            .replace("tránh tư thế nằm hoặc quỳ sàn", "tránh tư thế sàn")
            .replace("tránh đưa tay hoặc tải lên quá đầu", "tránh với quá đầu")
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
