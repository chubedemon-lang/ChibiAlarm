package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.TrainingMove
import com.chibialarm.app.WorkoutMoveSubstitution
import com.chibialarm.app.WorkoutMusclePlanSummary
import com.chibialarm.app.WorkoutSetTracker

/**
 * User-facing, deterministic preview of the exact remaining workout sequence.
 * This formatter is read-only: it never mutates the active session or awards XP.
 */
object WorkoutMidSessionPreviewFormatter {
    private const val MAX_VISIBLE_MOVES = 12

    fun format(preview: WorkoutMidSessionPlanner.Preview): String = buildString {
        append("Giáo án sau điều chỉnh:\n")
        preview.currentSetLimit?.let { limit ->
            val activeMove = preview.after.getOrNull(preview.currentIndex)
            val activePlan = activeMove?.let { WorkoutSetTracker.parse(it.dose, it.restSeconds) }
            if (activeMove != null && activePlan != null) {
                append("Giới hạn ${activeMove.name} tối đa còn $limit hiệp; giữ nguyên ${activePlan.requiredSets} hiệp bắt buộc, chỉ bỏ hiệp tùy chọn.\n")
            }
        }
        preview.currentRepLimit?.let { limit ->
            val activeMove = preview.after.getOrNull(preview.currentIndex)
            val activePlan = activeMove?.let { WorkoutSetTracker.parse(it.dose, it.restSeconds) }
            if (activeMove != null && activePlan != null) {
                append("Giới hạn ${activeMove.name} tối đa còn $limit ${activePlan.unit} mỗi hiệp cho phần chưa thực hiện; các hiệp đã xác nhận giữ nguyên.\n")
            }
        }
        preview.currentDurationLimit?.let { limit ->
            val activeMove = preview.after.getOrNull(preview.currentIndex)
            if (activeMove != null) {
                append("Giới hạn thời lượng ${activeMove.name} tối đa còn $limit giây mỗi hiệp cho phần chưa thực hiện; các hiệp đã xác nhận giữ nguyên.\n")
            }
        }
        if (preview.addedMoves.isNotEmpty()) {
            append("Bổ sung động tác tùy chọn theo yêu cầu:\n")
            preview.addedMoves.forEach { added ->
                append("• Thêm: ").append(added).append('\n')
            }
            append("Động tác bổ sung không thay thế hiệp bắt buộc; hãy kiểm tra hướng dẫn trước khi tập.\n\n")
        }
        if (preview.replacements.isNotEmpty() || preview.removedMoves.isNotEmpty()) {
            append("Thay đổi đề xuất:\n")
            preview.replacements.forEach { replacement ->
                append("• ").append(replacement).append('\n')
                appendReplacementExplanation(replacement, this)
            }
            if (preview.replacements.isNotEmpty()) {
                append("Lưu ý: bài thay thế có thể khác nhóm cơ hoặc cường độ; hãy đọc hướng dẫn và xác nhận trước khi áp dụng.\n")
            }
            preview.removedMoves.distinct().forEach { removed ->
                append("• Bỏ khỏi phần còn lại: ").append(removed).append('\n')
            }
            append('\n')
        }
        if (preview.replacements.isNotEmpty() || preview.removedMoves.isNotEmpty() ||
            preview.addedMoves.isNotEmpty()
        ) {
            WorkoutMusclePlanSummary.formatComparison(preview.before, preview.after)?.let {
                append(it).append('\n')
            }
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
            if (position == preview.currentIndex) {
                appendCurrentSetProgress(move, preview.completedCurrentSets, this)
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

    /**
     * Explain a replacement only when both names resolve to canonical catalog moves
     * and the pair has a reviewed explanation. Unknown pairs never receive invented reasons.
     */
    private fun appendReplacementExplanation(replacement: String, output: StringBuilder) {
        val names = replacement.split(" → ", limit = 2)
        if (names.size != 2) return
        val original = TrainingCatalog.allMoves.singleOrNull { it.name == names[0] } ?: return
        val selected = TrainingCatalog.allMoves.singleOrNull { it.name == names[1] } ?: return
        val explanation = WorkoutMoveSubstitution.explanationFor(original, selected) ?: return
        output.append("  Lý do: ").append(explanation).append('\n')
    }

    private fun appendCurrentSetProgress(
        move: TrainingMove,
        completedSets: Int,
        output: StringBuilder
    ) {
        if (completedSets <= 0) return
        val plan = WorkoutSetTracker.parse(move.dose, move.restSeconds) ?: return
        if (completedSets > plan.maximumSets) return

        val requiredRemaining = (plan.requiredSets - completedSets).coerceAtLeast(0)
        val optionalRemaining = (plan.maximumSets - maxOf(plan.requiredSets, completedSets))
            .coerceAtLeast(0)

        output.append(" · ")
        if (requiredRemaining > 0) {
            output.append("đã tự xác nhận ")
                .append(completedSets).append('/').append(plan.requiredSets)
                .append(" hiệp bắt buộc; còn ")
                .append(requiredRemaining).append(" hiệp bắt buộc")
        } else {
            output.append("đã tự xác nhận ")
                .append(completedSets).append('/').append(plan.maximumSets)
                .append(" hiệp; đã đủ ")
                .append(plan.requiredSets).append(" hiệp bắt buộc")
        }
        if (optionalRemaining > 0) {
            output.append("; còn tối đa ")
                .append(optionalRemaining).append(" hiệp tùy chọn")
        }
    }

    /**
     * Short, plain-language version for the in-session TTS button.
     * Keep it within the Activity's 350-character speech limit and state clearly that
     * the proposal has not been applied yet.
     */
    fun spokenSummary(preview: WorkoutMidSessionPlanner.Preview): String {
        val parts = mutableListOf<String>()
        parts += "Bản xem trước điều chỉnh."
        val currentBefore = preview.before.getOrNull(preview.currentIndex)?.name
        val currentAfter = preview.after.getOrNull(preview.currentIndex)?.name
        if (preview.currentMoveChanged && currentBefore != null && currentAfter != null) {
            parts += "Động tác đang tập: " + currentBefore + " đổi sang " + currentAfter + "."
        } else if (preview.currentMoveChanged) {
            parts += "Động tác đang tập được đề xuất thay thế."
        }
        if (preview.replacements.isNotEmpty()) {
            val other = preview.replacements
                .filterNot { currentBefore != null && it.startsWith(currentBefore + " → ") }
                .take(2)
            if (other.isNotEmpty()) {
                parts += "Thay thêm: " + other.joinToString("; ") { it.replace(" → ", " sang ") } + "."
            }
        }
        if (preview.removedMoves.isNotEmpty()) {
            parts += "Bỏ bài chưa bắt đầu: " +
                preview.removedMoves.take(2).joinToString(", ") + "."
        }
        if (preview.addedMoves.isNotEmpty()) {
            parts += "Thêm bài tùy chọn: " +
                preview.addedMoves.take(2).joinToString(", ") + "."
        }
        preview.currentSetLimit?.let { limit ->
            val activeMove = preview.after.getOrNull(preview.currentIndex)
            val activePlan = activeMove?.let { WorkoutSetTracker.parse(it.dose, it.restSeconds) }
            if (activeMove != null && activePlan != null) {
                parts += "Giới hạn ${activeMove.name} tối đa còn $limit hiệp; giữ nguyên ${activePlan.requiredSets} hiệp bắt buộc."
            }
        }
        preview.currentRepLimit?.let { limit ->
            val activeMove = preview.after.getOrNull(preview.currentIndex)
            val activePlan = activeMove?.let { WorkoutSetTracker.parse(it.dose, it.restSeconds) }
            if (activeMove != null && activePlan != null) {
                parts += "Giới hạn ${activeMove.name} còn tối đa $limit ${activePlan.unit} mỗi hiệp cho phần chưa thực hiện."
            }
        }
        preview.currentDurationLimit?.let { limit ->
            val activeMove = preview.after.getOrNull(preview.currentIndex)
            if (activeMove != null) {
                parts += "Giới hạn thời lượng ${activeMove.name} còn tối đa $limit giây mỗi hiệp cho phần chưa thực hiện."
            }
        }
        if (!preview.changed && preview.restSeconds == 0 && preview.currentSetLimit == null &&
            preview.currentRepLimit == null && preview.currentDurationLimit == null) {
            parts += "Không có thay đổi nào được đề xuất."
        }
        parts += "Thời gian còn lại ước tính " + durationLabel(preview.estimatedRemainingSeconds) + "."
        if (preview.targetExceeded) {
            parts += "Mục tiêu thời gian chưa đạt; hiệp bắt buộc vẫn được giữ."
        }
        if (preview.restSeconds > 0) {
            parts += "Sau khi áp dụng sẽ nghỉ riêng " + preview.restSeconds + " giây."
        }
        val details = parts.joinToString(" ").take(270)
        return (details + " Các hiệp đã xác nhận được giữ nguyên. Chưa áp dụng.").take(350)
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
