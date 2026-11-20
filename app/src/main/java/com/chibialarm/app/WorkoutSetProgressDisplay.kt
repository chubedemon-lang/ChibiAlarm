package com.chibialarm.app

/**
 * Separates required set progress from optional volume in the guided workout UI.
 *
 * The bar represents only the prescribed minimum. Optional sets are shown separately
 * and never make the required progress appear incomplete or encourage extra work.
 * Counts are self-confirmed by the user, not measured by sensors.
 */
object WorkoutSetProgressDisplay {
    data class State(
        val progressMax: Int,
        val progress: Int,
        val optionalCompleted: Int,
        val optionalTotal: Int,
        val summary: String,
        val contentDescription: String
    )

    fun from(plan: WorkoutSetTracker.Plan, completedSets: Int): State {
        val completed = completedSets.coerceIn(0, plan.maximumSets)
        val requiredCompleted = minOf(completed, plan.requiredSets)
        val optionalTotal = (plan.maximumSets - plan.requiredSets).coerceAtLeast(0)
        val optionalCompleted = (completed - plan.requiredSets).coerceIn(0, optionalTotal)
        val summary = buildString {
            append("Bắt buộc: $requiredCompleted/${plan.requiredSets}")
            if (optionalTotal > 0) {
                append(" • Tùy chọn: $optionalCompleted/$optionalTotal")
            }
        }
        val accessibility = buildString {
            append("Tiến độ hiệp bắt buộc: $requiredCompleted trên ${plan.requiredSets}.")
            if (optionalTotal > 0) {
                append(" Hiệp tùy chọn đã ghi: $optionalCompleted trên $optionalTotal; không bắt buộc.")
            }
            append(" Số hiệp do người tập tự xác nhận.")
        }
        return State(
            progressMax = plan.requiredSets,
            progress = requiredCompleted,
            optionalCompleted = optionalCompleted,
            optionalTotal = optionalTotal,
            summary = summary,
            contentDescription = accessibility
        )
    }
}
