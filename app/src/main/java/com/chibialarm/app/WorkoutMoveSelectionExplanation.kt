package com.chibialarm.app

/**
 * Explains the observable reasons a catalog move remains in the current preview.
 *
 * This is deliberately not a claim that the move is optimal for a goal or that the
 * user's technique is correct. It reports only catalog metadata and filters that
 * have already been checked for this exact preview.
 */
internal object WorkoutMoveSelectionExplanation {
    private const val MAX_MOVES = 200
    private const val MAX_CHARS = 900

    private val warmUpNames = setOf("Khởi động tại chỗ")
    private val coolDownNames = setOf("Thả lỏng", "Giãn cơ và thở chậm")

    fun explain(
        program: TrainingProgram,
        move: TrainingMove,
        position: Int,
        total: Int,
        constraints: WorkoutConstraintProfile,
        availableEquipment: Set<TrainingEquipment>
    ): String? {
        if (program.name.isBlank() || program.name.length > 100 ||
            program.category.isBlank() || program.category.length > 80 ||
            total !in 1..MAX_MOVES || position !in 1..total ||
            availableEquipment.size > TrainingEquipment.values().size
        ) return null

        // Fail closed: never describe a move as compatible if current constraints changed.
        if (!WorkoutConstraintFilter.isAllowed(move, constraints) ||
            !TrainingEquipmentAvailability.allows(move, availableEquipment)
        ) return null

        val name = safe(move.name, 90).ifBlank { "Động tác hiện tại" }
        val programName = safe(program.name, 100)
        val category = safe(program.category, 80)
        val role = when (move.name) {
            in warmUpNames -> "Khởi động"
            in coolDownNames -> "Thả lỏng"
            else -> "Động tác chính"
        }

        val equipment = if (move.equipmentOptions.isEmpty()) {
            "Không cần dụng cụ chuyên dụng theo thư viện."
        } else {
            val matching = move.equipmentOptions
                .filter(availableEquipment::contains)
                .sortedBy { it.ordinal }
            if (matching.isEmpty()) return null
            "Dụng cụ tương thích trong lựa chọn hiện tại: " +
                matching.joinToString(" hoặc ") { equipmentLabel(it) } +
                ". Đây là các lựa chọn thay thế, không cần có tất cả."
        }

        val activeFilters = buildList {
            if (constraints.avoidWristLoad) add("tránh tải qua cổ tay")
            if (constraints.avoidFloorPositions) add("tránh tư thế sàn")
            if (constraints.avoidOverheadReach) add("tránh đưa tay quá đầu")
            if (constraints.avoidJumping) add("tránh bật nhảy")
            if (constraints.avoidKneeling) add("tránh quỳ gối")
        }
        val filterSummary = if (activeFilters.isEmpty()) {
            "Không có bộ lọc vận động bổ sung được bật cho buổi này."
        } else {
            "Động tác đã vượt qua bộ lọc đang bật: " + activeFilters.joinToString(", ") + "."
        }

        val muscleSummary = if (TrainingMuscleCatalog.isCurated(move.name)) {
            val focus = move.muscleFocus
            val primary = focus.primary.joinToString(", ") { it.label }
            val supporting = focus.supporting.joinToString(", ") { it.label }
            if (supporting.isBlank()) {
                "Nhóm cơ chính theo thư viện: $primary."
            } else {
                "Nhóm cơ chính theo thư viện: $primary; hỗ trợ: $supporting."
            }
        } else {
            "Thư viện chưa có nhãn nhóm cơ đã duyệt cho bài này; Chibi không suy đoán từ tên."
        }

        val dose = safe(move.dose, 120).ifBlank { "Theo hướng dẫn trên màn hình" }
        val rest = WorkoutSetTracker.restGuidance(move.dose, move.restSeconds)
            ?.let { safe(it, 180) }
            ?: "Không có cấu trúc hiệp lặp cố định; nghỉ thêm khi cần."

        return buildString {
            append("Vì sao bài này phù hợp với lựa chọn hiện tại?\n")
            append("Bài ").append(position).append("/").append(total)
                .append(" trong ").append(programName).append(" (nhóm ").append(category).append(").\n")
            append("Vai trò: ").append(role).append(" — ").append(name).append(".\n")
            append("Dụng cụ: ").append(equipment).append('\n')
            append("Bộ lọc vận động: ").append(filterSummary).append('\n')
            append(muscleSummary).append('\n')
            append("Liều lượng: ").append(dose).append(". Nghỉ: ").append(rest).append('\n')
            append("Đây là giải thích từ dữ liệu thư viện và điều kiện lọc hiện tại, không phải đánh giá tư thế, phép đo hiệu quả hay khẳng định bài tập tối ưu.")
        }.take(MAX_CHARS)
    }

    private fun equipmentLabel(value: TrainingEquipment): String = when (value) {
        TrainingEquipment.DUMBBELL -> "tạ đơn"
        TrainingEquipment.RESISTANCE_BAND -> "dây kháng lực"
        TrainingEquipment.STABLE_CHAIR -> "ghế vững chắc"
        TrainingEquipment.MAT -> "thảm tập"
    }

    private fun safe(value: String, limit: Int): String = value
        .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(limit)
}
