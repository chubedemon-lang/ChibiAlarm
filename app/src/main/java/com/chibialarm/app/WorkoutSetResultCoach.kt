package com.chibialarm.app

/**
 * Turns a self-reported set value into a short, non-judgmental coaching cue.
 *
 * The comparison is against the current catalog/edited target only. It does not
 * verify technique, infer effort, change the next target, or award XP.
 */
object WorkoutSetResultCoach {
    enum class Comparison {
        BELOW_TARGET,
        IN_TARGET,
        ABOVE_TARGET
    }

    data class Feedback(
        val comparison: Comparison,
        val message: String
    )

    fun feedback(
        moveName: String,
        setNumber: Int,
        actualValue: Int,
        plan: WorkoutSetTracker.Plan,
        completedSetsAfter: Int
    ): Feedback? {
        if (moveName.isBlank() || moveName.length > 200 ||
            setNumber !in 1..plan.maximumSets ||
            completedSetsAfter != setNumber
        ) return null

        val maximumValue = if (plan.unit == "giây") 600 else 200
        if (actualValue !in 1..maximumValue) return null

        val safeName = moveName
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        if (safeName.isBlank()) return null

        val comparison = when {
            actualValue < plan.targetMin -> Comparison.BELOW_TARGET
            actualValue > plan.targetMax -> Comparison.ABOVE_TARGET
            else -> Comparison.IN_TARGET
        }
        val target = if (plan.targetMin == plan.targetMax) {
            "${plan.targetMin}"
        } else {
            "${plan.targetMin} đến ${plan.targetMax}"
        }
        val valueUnit = if (plan.unit == "giây") "giây giữ" else plan.unit
        val progress = when {
            completedSetsAfter >= plan.maximumSets ->
                "Đã đủ số hiệp tối đa; không cần tập thêm động tác này."
            completedSetsAfter >= plan.requiredSets ->
                "Đã đủ hiệp bắt buộc; hiệp tiếp theo chỉ là tùy chọn nếu còn sức."
            else -> {
                val remaining = plan.requiredSets - completedSetsAfter
                "Còn $remaining hiệp bắt buộc theo giáo án."
            }
        }
        val assessment = when (comparison) {
            Comparison.BELOW_TARGET ->
                "Kết quả tự báo thấp hơn khoảng mục tiêu $target ${plan.unit}; không cần cố bù ngay. Hãy chọn cảm nhận của bạn để Chibi cân nhắc thời gian nghỉ hoặc cho xem trước điều chỉnh."
            Comparison.IN_TARGET ->
                "Kết quả tự báo nằm trong khoảng mục tiêu $target ${plan.unit}. Không cần tăng mục tiêu; giữ nhịp thoải mái."
            Comparison.ABOVE_TARGET ->
                "Kết quả tự báo cao hơn khoảng mục tiêu $target ${plan.unit}. Chibi chỉ ghi nhận điều bạn báo, không tự nâng mục tiêu hoặc khuyến khích lặp lại mức cao hơn."
        }
        return Feedback(
            comparison = comparison,
            message = "Đã ghi nhận hiệp $setNumber/${plan.maximumSets} của $safeName: $actualValue $valueUnit (tự khai báo). $assessment $progress"
        )
    }
}
