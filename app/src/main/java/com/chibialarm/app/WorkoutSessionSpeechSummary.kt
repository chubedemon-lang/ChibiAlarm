package com.chibialarm.app

/**
 * Builds an opt-in spoken recap from the structured summaries already shown on the
 * workout reward screen. It deliberately excludes free-form workout notes and never
 * reads personal set data automatically.
 */
internal object WorkoutSessionSpeechSummary {
    const val MAX_SECTION_CHARS = 700
    const val MAX_TOTAL_CHARS = 1_800

    fun build(setValueSummary: String, effortSummary: String): String? {
        val sets = boundedSection(setValueSummary)
        val effort = boundedSection(effortSummary)
        if (sets.isBlank() && effort.isBlank()) return null

        return buildList {
            add("Kết quả buổi tập.")
            if (sets.isNotBlank()) {
                add("Giá trị từng hiệp bạn tự khai báo: $sets.")
                add("Đây là số liệu do bạn tự ghi, không được cảm biến xác minh.")
            }
            if (effort.isNotBlank()) {
                add("Cảm nhận các hiệp bạn tự ghi: $effort.")
                add("Đây là tự đánh giá, không phải phép đo thể lực hay khả năng hồi phục.")
            }
        }.joinToString(" ").take(MAX_TOTAL_CHARS)
    }

    private fun boundedSection(value: String): String {
        val normalized = value
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.length <= MAX_SECTION_CHARS) return normalized
        return normalized.take(MAX_SECTION_CHARS - 1).trimEnd() + "…"
    }
}
