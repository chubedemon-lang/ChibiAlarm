package com.chibialarm.app

/**
 * Explains the muscle emphasis of the exact, already-filtered workout plan.
 *
 * Counts catalogued exercise entries, not sets, repetitions, load or physiological
 * training volume. Unknown exercises are not guessed from their names.
 */
internal object WorkoutMusclePlanSummary {
    data class GroupCount(
        val group: TrainingMuscleGroup,
        val exerciseCount: Int
    )

    data class Summary(
        val eligibleMoveCount: Int,
        val cataloguedMoveCount: Int,
        val primary: List<GroupCount>,
        val supporting: List<GroupCount>
    )

    private val nonMainMoves = setOf("Khởi động tại chỗ", "Thả lỏng", "Giãn cơ và thở chậm")

    fun summarize(moves: List<TrainingMove>): Summary? {
        if (moves.isEmpty() || moves.size > 200) return null
        val mainMoves = moves.filterNot { it.name in nonMainMoves }
        if (mainMoves.isEmpty()) return null
        val catalogued = mainMoves.filter { TrainingMuscleCatalog.isCurated(it.name) }
        if (catalogued.isEmpty()) return null

        return Summary(
            eligibleMoveCount = mainMoves.size,
            cataloguedMoveCount = catalogued.size,
            primary = count(catalogued) { it.muscleFocus.primary },
            supporting = count(catalogued) { it.muscleFocus.supporting }
        )
    }

    fun format(moves: List<TrainingMove>): String? {
        val summary = summarize(moves) ?: return null
        val primary = render(summary.primary, 4).ifBlank { "chưa có nhãn" }
        val supporting = render(summary.supporting, 3).ifBlank { "chưa có nhãn hỗ trợ" }
        return buildString {
            append("Nhóm cơ theo nhãn thư viện (đếm động tác, không phải khối lượng tập):")
            append("\n• Nhóm chính: ").append(primary)
            append("\n• Nhóm hỗ trợ: ").append(supporting)
            append("\nDữ liệu nhóm cơ có cho ")
            append(summary.cataloguedMoveCount).append("/")
            append(summary.eligibleMoveCount).append(" động tác chính.")
        }
    }

    fun spoken(moves: List<TrainingMove>): String? {
        val summary = summarize(moves) ?: return null
        val primary = summary.primary.take(3).joinToString(", ") { it.group.label }
        if (primary.isBlank()) return null
        val supporting = summary.supporting.take(2).joinToString(", ") { it.group.label }
        return buildString {
            append("Nhóm cơ chính: ").append(primary).append(".")
            if (supporting.isNotBlank()) append(" Nhóm hỗ trợ: ").append(supporting).append(".")
            append(" Đây là nhãn thư viện, không phải phép đo khối lượng tập.")
        }.take(180)
    }

    private fun count(
        moves: List<TrainingMove>,
        groups: (TrainingMove) -> List<TrainingMuscleGroup>
    ): List<GroupCount> = TrainingMuscleGroup.values().mapNotNull { group ->
        val total = moves.count { group in groups(it) }
        if (total == 0) null else GroupCount(group, total)
    }.sortedWith(compareByDescending<GroupCount> { it.exerciseCount }.thenBy { it.group.ordinal })

    private fun render(counts: List<GroupCount>, limit: Int): String =
        counts.take(limit).joinToString(" • ") { "${it.group.label}: ${it.exerciseCount}" }
}
