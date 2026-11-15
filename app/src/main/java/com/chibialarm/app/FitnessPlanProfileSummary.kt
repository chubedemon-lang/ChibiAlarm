package com.chibialarm.app

/**
 * Human-readable audit of the exact choices used for a local workout recommendation.
 * Values are coarse UI selections only; movement constraints are session-only and
 * this summary is never persisted or sent to a service.
 */
object FitnessPlanProfileSummary {
    fun format(
        goalLabel: String,
        energyLabel: String,
        levelLabel: String,
        minutes: Int,
        equipmentSelection: Int,
        additionalEquipment: Set<TrainingEquipment>,
        impact: Int,
        constraints: WorkoutConstraintProfile
    ): String {
        require(goalLabel.isNotBlank() && goalLabel.length <= 80)
        require(energyLabel.isNotBlank() && energyLabel.length <= 80)
        require(levelLabel.isNotBlank() && levelLabel.length <= 80)
        require(minutes in 5..120)
        require(equipmentSelection in 0..5)
        require(impact in 0..2)
        require(additionalEquipment.size <= TrainingEquipment.values().size)

        val primaryEquipment = TrainingEquipmentAvailability.fromPreference(equipmentSelection)
        val extraEquipment = (additionalEquipment - primaryEquipment)
            .sortedBy { it.ordinal }
            .map(::equipmentLabel)
        val equipment = when (equipmentSelection) {
            0 -> "Không có dụng cụ"
            1 -> "Thảm tập"
            2 -> "Tạ đơn hoặc dây kháng lực"
            3 -> "Ghế vững chắc, không bánh xe"
            4 -> "Tạ đơn"
            else -> "Dây kháng lực"
        } + if (extraEquipment.isEmpty()) "" else "; bổ sung: " + extraEquipment.joinToString(", ")

        val impactDescription = when (impact) {
            0 -> "Chưa khai báo ưu tiên hạn chế tác động (không phải xác nhận y tế)"
            1 -> "Ưu tiên bài ít tác động, không nhảy"
            else -> "Đã báo đau hoặc chấn thương — không tạo giáo án"
        }

        val movementFilters = buildList {
            if (constraints.avoidWristLoad) add("tránh chống tay/tải qua cổ tay")
            if (constraints.avoidFloorPositions) add("tránh tư thế nằm hoặc quỳ sàn")
            if (constraints.avoidOverheadReach) add("tránh đưa tay hoặc tải lên quá đầu")
        }
        val filterDescription = if (movementFilters.isEmpty()) {
            "Chưa bật bộ lọc cổ tay, tư thế sàn hoặc đưa tay quá đầu."
        } else {
            movementFilters.joinToString("; ")
        }

        return buildString {
            append("Hồ sơ dùng để tạo giáo án — kiểm tra trước khi bắt đầu\n")
            append("Mục tiêu: ").append(goalLabel).append('\n')
            append("Trình độ: ").append(levelLabel).append('\n')
            append("Năng lượng tự đánh giá: ").append(energyLabel).append('\n')
            append("Thời lượng mục tiêu: ").append(minutes).append(" phút\n")
            append("Dụng cụ: ").append(equipment).append('\n')
            append("Ưu tiên vận động: ").append(impactDescription).append('\n')
            append("Bộ lọc động tác: ").append(filterDescription).append('\n')
            append("Các bộ lọc cổ tay/sàn/tầm với chỉ áp dụng cho buổi hiện tại, không lưu thành hồ sơ sức khỏe. Đây là gợi ý ngoại tuyến, không phải đánh giá y tế.")
        }
    }

    private fun equipmentLabel(equipment: TrainingEquipment): String = when (equipment) {
        TrainingEquipment.DUMBBELL -> "Tạ đơn"
        TrainingEquipment.RESISTANCE_BAND -> "Dây kháng lực"
        TrainingEquipment.STABLE_CHAIR -> "Ghế vững chắc"
        TrainingEquipment.MAT -> "Thảm tập"
    }
}
