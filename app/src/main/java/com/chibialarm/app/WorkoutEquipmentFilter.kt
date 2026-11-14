package com.chibialarm.app

/**
 * Filters base catalog moves against the selected equipment category.
 * Missing tools are never silently assumed and non-equivalent substitutions are not invented.
 */
object WorkoutEquipmentFilter {
    data class Result(
        val program: TrainingProgram,
        val removedMoves: List<String>
    ) {
        val changed: Boolean get() = removedMoves.isNotEmpty()
        val canStart: Boolean
            get() = program.moves.any {
                it.name !in setOf("Khởi động tại chỗ", "Thả lỏng", "Giãn cơ và thở chậm")
            }
        val notice: String
            get() {
                if (removedMoves.isEmpty()) return ""
                val distinct = removedMoves.distinct()
                val names = distinct.take(8).joinToString(" • ")
                val extra = (distinct.size - 8).coerceAtLeast(0)
                return buildString {
                    append("Đã bỏ động tác cần dụng cụ chưa chọn: ").append(names)
                    if (extra > 0) append(" • và ").append(extra).append(" động tác khác")
                    append(". Chibi không tự thay bằng bài không tương đương. ")
                    append("Hãy chọn đúng dụng cụ hoặc xem chương trình khác.")
                }
            }
    }

    fun apply(
        program: TrainingProgram,
        equipmentSelection: Int,
        additionalEquipment: Set<TrainingEquipment> = emptySet()
    ): Result {
        val available = TrainingEquipmentAvailability.fromPreference(equipmentSelection) + additionalEquipment
        val removed = mutableListOf<String>()
        val filtered = program.moves.filter { move ->
            if (TrainingEquipmentAvailability.allows(move, available)) true
            else {
                removed += move.name
                false
            }
        }
        return Result(program.copy(moves = filtered), removed)
    }
}
