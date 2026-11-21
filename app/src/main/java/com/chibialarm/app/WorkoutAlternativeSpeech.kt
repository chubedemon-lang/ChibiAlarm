package com.chibialarm.app

/**
 * Produces a short, catalog-grounded spoken list for the replacement picker.
 * It never claims a substitution was applied; the user must still select and confirm.
 */
internal object WorkoutAlternativeSpeech {
    private const val MAX_SPOKEN_OPTIONS = 4
    private const val MAX_FIELD_CHARS = 80

    fun summary(options: List<TrainingMove>): String? {
        if (options.isEmpty()) return null
        val visible = options.take(MAX_SPOKEN_OPTIONS)
        val count = if (options.size > 99) "nhiều" else options.size.toString()
        val entries = visible.mapIndexed { index, move ->
            "Phương án " + (index + 1) + ": " + clean(move.name) +
                ". Mục tiêu: " + clean(move.dose) + "."
        }.joinToString(" ")
        val remaining = options.size - visible.size
        val more = if (remaining > 0) {
            " Còn " + remaining + " phương án khác trong danh sách."
        } else {
            ""
        }
        return "Chibi tìm thấy " + count +
            " phương án thay thế đã được lọc theo dụng cụ và hạn chế vận động. " +
            entries + more +
            " Hãy chọn trên màn hình để xem trước. Chibi chưa thay đổi giáo án."
    }

    private fun clean(value: String): String {
        val normalized = value.map { character ->
            if (character.isISOControl()) ' ' else character
        }.joinToString("")
        val collapsed = normalized.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(MAX_FIELD_CHARS)
        return collapsed.ifBlank { "chưa rõ" }
    }
}
