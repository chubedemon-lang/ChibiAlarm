package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutMoveSelectionExplanationTest {
    @Test fun explainsCatalogMoveUsingProgramRoleEquipmentAndActiveFilters() {
        val program = TrainingCatalog.programs.first {
            it.moves.any { move -> move.name == "Đứng lên – ngồi xuống" }
        }
        val move = program.moves.first { it.name == "Đứng lên – ngồi xuống" }
        val explanation = WorkoutMoveSelectionExplanation.explain(
            program = program,
            move = move,
            position = 2,
            total = program.moves.size,
            constraints = WorkoutConstraintProfile(avoidJumping = true),
            availableEquipment = setOf(TrainingEquipment.STABLE_CHAIR)
        )!!

        assertTrue(explanation.contains("Bài 2/"))
        assertTrue(explanation.contains(program.name))
        assertTrue(explanation.contains(program.category))
        assertTrue(explanation.contains("Dụng cụ tương thích"))
        assertTrue(explanation.contains("ghế vững chắc"))
        assertTrue(explanation.contains("tránh bật nhảy"))
        assertTrue(explanation.contains("Đùi trước"))
        assertTrue(explanation.contains("Nghỉ:"))
        assertTrue(explanation.contains("không phải đánh giá tư thế"))
    }

    @Test fun refusesToCallAChairMoveCompatibleWhenTheChairWasNotSelected() {
        val program = TrainingCatalog.programs.first {
            it.moves.any { move -> move.name == "Đứng lên – ngồi xuống" }
        }
        val move = program.moves.first { it.name == "Đứng lên – ngồi xuống" }

        assertNull(
            WorkoutMoveSelectionExplanation.explain(
                program = program,
                move = move,
                position = 1,
                total = program.moves.size,
                constraints = WorkoutConstraintProfile(),
                availableEquipment = emptySet()
            )
        )
    }

    @Test fun doesNotInventMuscleLabelsForCustomMoves() {
        val program = TrainingProgram(
            id = "custom",
            name = "Chương trình thử",
            subtitle = "Không dùng cho phát hành",
            category = "Thử nghiệm",
            moves = emptyList()
        )
        val custom = TrainingMove(
            name = "Động tác tự tạo",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện chậm.",
            easier = "Giảm biên độ.",
            avoid = "Dừng nếu khó chịu."
        )

        val explanation = WorkoutMoveSelectionExplanation.explain(
            program = program,
            move = custom,
            position = 1,
            total = 1,
            constraints = WorkoutConstraintProfile(),
            availableEquipment = emptySet()
        )!!

        assertTrue(explanation.contains("chưa có nhãn nhóm cơ đã duyệt"))
        assertFalse(explanation.contains("Ngực"))
    }

    @Test fun refusesToExplainAMoveThatViolatesTheCurrentMovementFilter() {
        val program = TrainingCatalog.programs.first {
            it.moves.any { move -> move.name == "Bật nhảy dang tay chân" }
        }
        val move = program.moves.first { it.name == "Bật nhảy dang tay chân" }

        assertNull(
            WorkoutMoveSelectionExplanation.explain(
                program = program,
                move = move,
                position = 1,
                total = program.moves.size,
                constraints = WorkoutConstraintProfile(avoidJumping = true),
                availableEquipment = emptySet()
            )
        )
    }
}
