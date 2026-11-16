package com.chibialarm.app.fitness

import org.junit.Assert.*
import org.junit.Test

class WorkoutRemainingRepTargetTest {
    @Test fun targetAppliesOnlyToRemainingSetsAfterConfirmedWork() {
        val proposal = WorkoutRemainingRepTarget.preview(
            currentTarget = null,
            completedSets = 1,
            maximumSets = 3,
            requestedReps = 6
        )!!

        assertEquals(2, proposal.remainingSets)
        assertEquals(1, proposal.completedSets)
        assertEquals(3, proposal.maximumSets)
        assertEquals(6, WorkoutRemainingRepTarget.accept(proposal, null, 1, 3))
    }

    @Test fun allConfirmedSetsLeaveNoEditableTarget() {
        assertNull(WorkoutRemainingRepTarget.preview(
            currentTarget = 8, completedSets = 3, maximumSets = 3, requestedReps = 5
        ))
    }

    @Test fun unchangedTargetIsANoOp() {
        assertNull(WorkoutRemainingRepTarget.preview(
            currentTarget = 8, completedSets = 1, maximumSets = 3, requestedReps = 8
        ))
    }

    @Test fun staleSetProgressRejectsProposal() {
        val proposal = WorkoutRemainingRepTarget.preview(null, 1, 3, 6)!!
        assertNull(WorkoutRemainingRepTarget.accept(proposal, null, 2, 3))
    }

    @Test fun changedSetPlanOrTargetRejectsProposal() {
        val proposal = WorkoutRemainingRepTarget.preview(null, 1, 3, 6)!!
        assertNull(WorkoutRemainingRepTarget.accept(proposal, 8, 1, 3))
        assertNull(WorkoutRemainingRepTarget.accept(proposal, null, 1, 4))
    }

    @Test fun invalidCountsAndTargetsAreRejected() {
        assertNull(WorkoutRemainingRepTarget.preview(null, -1, 3, 6))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 0, 6))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 21, 6))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 3, 0))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 3, 101))
    }
}
