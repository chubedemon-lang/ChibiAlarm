package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutPlanPreviewEditorTest {
    @Test fun previewIsAnImmutableSnapshotAndCalculatesTheWholePlanDuration() {
        val source = TrainingCatalog.byId("advanced").moves.toMutableList()
        val draft = WorkoutPlanPreviewEditor.start(
            source, 25, 1, WorkoutConstraintProfile(), 0
        )!!

        source.clear()

        assertEquals(TrainingCatalog.byId("advanced").moves, draft.originalMoves)
        assertEquals(draft.originalMoves, draft.moves)
        val expected = draft.moves.sumOf { WorkoutDurationPlanner.activeSeconds(it) } +
            draft.moves.sumOf { WorkoutDurationPlanner.prescribedRestSeconds(it) } +
            (draft.moves.size - 1) * WorkoutDurationPlanner.transitionAllowance(1)
        assertEquals(expected, draft.estimatedSeconds)
        assertTrue(WorkoutPlanPreviewEditor.isValid(draft))
    }

    @Test fun replacementChangesOnlyTheSelectedMoveAndRecalculatesDuration() {
        val source = TrainingCatalog.byId("advanced").moves
        val index = source.indexOfFirst { it.name == "Chống đẩy sàn" }
        val draft = WorkoutPlanPreviewEditor.start(
            source, 25, 1, WorkoutConstraintProfile(), 0
        )!!
        val wallPush = WorkoutPlanPreviewEditor.alternatives(draft, index)
            .first { it.name == "Chống đẩy tường" }

        val updated = WorkoutPlanPreviewEditor.replace(draft, index, wallPush)!!

        assertEquals("Chống đẩy sàn", draft.moves[index].name)
        assertEquals("Chống đẩy tường", updated.moves[index].name)
        assertEquals(source.filterIndexed { i, _ -> i != index }, updated.moves.filterIndexed { i, _ -> i != index })
        assertTrue(updated.estimatedSeconds < draft.estimatedSeconds)
        assertEquals(mapOf(index to wallPush), updated.substitutions)
        assertTrue(WorkoutPlanPreviewEditor.isValid(updated))
    }

    @Test fun equipmentAndMovementConstraintsFilterPreviewOptions() {
        val source = listOf(TrainingCatalog.allMoves.first { it.name == "Squat không tạ" })
        val withoutChair = WorkoutPlanPreviewEditor.start(
            source, 10, 0, WorkoutConstraintProfile(), 0
        )!!
        assertTrue(WorkoutPlanPreviewEditor.alternatives(withoutChair, 0).isEmpty())

        val withChair = WorkoutPlanPreviewEditor.start(
            source, 10, 0, WorkoutConstraintProfile(), 0,
            setOf(TrainingEquipment.STABLE_CHAIR)
        )!!
        assertEquals(
            listOf("Đứng lên – ngồi xuống"),
            WorkoutPlanPreviewEditor.alternatives(withChair, 0).map { it.name }
        )
    }

    @Test fun replacementMustBeCuratedAndUsesCanonicalInstructions() {
        val source = listOf(TrainingCatalog.allMoves.first { it.name == "Squat không tạ" })
        val draft = WorkoutPlanPreviewEditor.start(
            source, 10, 0, WorkoutConstraintProfile(), 0,
            setOf(TrainingEquipment.STABLE_CHAIR)
        )!!
        val canonical = WorkoutPlanPreviewEditor.alternatives(draft, 0).single()
        val spoofed = canonical.copy(steps = "caller supplied exercise instructions")

        assertNull(WorkoutPlanPreviewEditor.replace(draft, 0, spoofed.copy(name = "Unknown exercise")))
        val updated = WorkoutPlanPreviewEditor.replace(draft, 0, spoofed)!!
        assertEquals(canonical.steps, updated.moves.single().steps)
    }

    @Test fun invalidIndexesPlansAndTargetsFailClosed() {
        val source = listOf(TrainingCatalog.allMoves.first { it.name == "Squat không tạ" })
        val draft = WorkoutPlanPreviewEditor.start(
            source, 10, 0, WorkoutConstraintProfile(), 0
        )!!
        assertTrue(WorkoutPlanPreviewEditor.alternatives(draft, -1).isEmpty())
        assertNull(WorkoutPlanPreviewEditor.replace(draft, 3, draft.moves.single()))
        assertNull(WorkoutPlanPreviewEditor.start(emptyList(), 10, 0, WorkoutConstraintProfile(), 0))
        assertNull(WorkoutPlanPreviewEditor.start(source, 4, 0, WorkoutConstraintProfile(), 0))
        assertNull(WorkoutPlanPreviewEditor.start(source, 10, 3, WorkoutConstraintProfile(), 0))
        assertNull(WorkoutPlanPreviewEditor.start(List(101) { source.single() }, 10, 0, WorkoutConstraintProfile(), 0))
    }

    @Test fun checkpointEncodingContainsOnlyApprovedSubstitutions() {
        val source = TrainingCatalog.byId("advanced").moves
        val index = source.indexOfFirst { it.name == "Chống đẩy sàn" }
        val draft = WorkoutPlanPreviewEditor.start(
            source, 25, 1, WorkoutConstraintProfile(), 0
        )!!
        val replacement = WorkoutPlanPreviewEditor.alternatives(draft, index)
            .first { it.name == "Chống đẩy tường" }
        val updated = WorkoutPlanPreviewEditor.replace(draft, index, replacement)!!
        val encoded = WorkoutPlanPreviewEditor.encodeSubstitutions(updated)
        val restored = WorkoutMoveSubstitution.decode(encoded, source, WorkoutConstraintProfile())

        assertEquals(mapOf(index to replacement), restored)
        assertTrue(WorkoutPlanPreviewEditor.isValid(updated))
    }

    @Test fun multiplePreviewReplacementsAccumulateWithoutLosingEarlierChoices() {
        val source = TrainingCatalog.byId("advanced").moves
        val squatIndex = source.indexOfFirst { it.name == "Squat chậm 3 giây" }
        val pushIndex = source.indexOfFirst { it.name == "Chống đẩy sàn" }
        val draft = WorkoutPlanPreviewEditor.start(
            source, 30, 1, WorkoutConstraintProfile(), 3
        )!!
        val chairSquat = WorkoutPlanPreviewEditor.alternatives(draft, squatIndex).single()
        val afterSquat = WorkoutPlanPreviewEditor.replace(draft, squatIndex, chairSquat)!!
        val wallPush = WorkoutPlanPreviewEditor.alternatives(afterSquat, pushIndex)
            .first { it.name == "Chống đẩy tường" }
        val afterBoth = WorkoutPlanPreviewEditor.replace(afterSquat, pushIndex, wallPush)!!

        assertEquals("Đứng lên – ngồi xuống", afterBoth.moves[squatIndex].name)
        assertEquals("Chống đẩy tường", afterBoth.moves[pushIndex].name)
        assertEquals(2, afterBoth.substitutions.size)
        assertTrue(WorkoutPlanPreviewEditor.isValid(afterBoth))
        assertEquals(2, WorkoutMoveSubstitution.decode(
            WorkoutPlanPreviewEditor.encodeSubstitutions(afterBoth),
            afterBoth.originalMoves,
            afterBoth.constraints
        ).size)
    }

    @Test fun draftReportsWhenAReplacementMakesThePlanExceedItsTarget() {
        val catalog = TrainingCatalog.byId("advanced").moves
        val source = List(12) { catalog[it % catalog.size] }
        val draft = WorkoutPlanPreviewEditor.start(
            source, 5, 0, WorkoutConstraintProfile(), 0
        )!!

        assertTrue(draft.exceedsTarget)
        assertTrue(draft.estimatedMinutes > draft.targetMinutes)
        assertEquals("0 phút 30 giây", WorkoutPlanPreviewEditor.durationLabel(30))
        assertEquals("2 phút", WorkoutPlanPreviewEditor.durationLabel(120))
    }
}
