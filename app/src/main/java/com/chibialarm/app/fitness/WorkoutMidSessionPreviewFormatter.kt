package com.chibialarm.app.fitness

/**
 * User-facing, deterministic preview of the exact remaining workout sequence.
 * This formatter is read-only: it never mutates the active session or awards XP.
 */
object WorkoutMidSessionPreviewFormatter {
    private const val MAX_VISIBLE_MOVES = 12

    fun format(preview: WorkoutMidSessionPlanner.Preview): String = buildString {
        append("Giáo án sau điều chỉnh:\n")
        if (preview.replacements.isNotEmpty() || preview.removedMoves.isNotEmpty()) {
            append("Thay đổi đề xuất:\n")
            preview.replacements.forEach { replacement ->
                append("• ").append(replacement).append('\n')
            }
            if (preview.replacements.isNotEmpty()) {
                append("Lưu ý: bài thay thế có thể khác nhóm cơ hoặc cường độ; hãy đọc hướng dẫn và xác nhận trước khi áp dụng.\n")
            }
            preview.removedMoves.distinct().forEach { removed ->
                append("• Bỏ khỏi phần còn lại: ").append(removed).append('\n')
            }
            append('\n')
        }
        val startIndex = if (preview.currentIndex >= MAX_VISIBLE_MOVES) preview.currentIndex else 0
        if (startIndex > 0) {
            append("… ")
                .append(startIndex)
                .append(" bước trước trong phiên được giữ nguyên\n")
        }
        val visible = preview.after.drop(startIndex).take(MAX_VISIBLE_MOVES)
        visible.forEachIndexed { offset, move ->
            val position = startIndex + offset
            val state = when {
                position < preview.currentIndex -> "Bước trước"
                position == preview.currentIndex -> "Đang tập"
                else -> "Tiếp theo"
            }
            append(position + 1)
                .append(". ")
                .append(state)
                .append(" — ")
                .append(move.name)
            if (move.dose.isNotBlank()) {
                append(" · ").append(move.dose)
            }
            append('\n')
        }
        val remainingCount = preview.after.size - startIndex - visible.size
        if (remainingCount > 0) {
            append("… còn ").append(remainingCount).append(" động tác khác\n")
        }

        append("\nThời gian còn lại ước tính: ")
            .append(durationLabel(preview.estimatedRemainingSeconds))
            .append(". Đây là ước tính tham khảo, không phải cam kết thời gian hoàn thành.")

        preview.requestedMinutes?.let {
            append("\nMục tiêu thời gian của bạn: ").append(it).append(" phút.")
        }
        if (preview.targetExceeded) {
            append("\nPhần bài chính và nghỉ bắt buộc vẫn dài hơn mục tiêu; Chibi không tự cắt hiệp bắt buộc.")
        }
        if (preview.restSeconds > 0) {
            append("\nSau khi áp dụng sẽ nghỉ riêng ")
                .append(preview.restSeconds)
                .append(" giây; thời gian nghỉ không tính là vận động.")
        }
    }

    private fun durationLabel(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        return if (minutes == 0) {
            "$seconds giây"
        } else {
            "$minutes phút $seconds giây"
        }
    }
}
