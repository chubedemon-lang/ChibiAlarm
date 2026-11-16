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

    @Test fun combinedRepAndRestSpeechRemainsAConfirmedTargetEdit() {
        val utterance = "Giảm xuống 6 lần và nghỉ 90 giây"
        val parsed = WorkoutMidSessionRequest.parse(utterance)
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.isRestOnly)
        assertEquals(90, request.restSeconds)
        assertEquals(6, WorkoutTargetCommand.spokenReps(utterance))
        assertEquals(com.chibialarm.app.WorkoutVoiceCommands.Command.REST,
            com.chibialarm.app.WorkoutVoiceCommands.parse(utterance))
        assertNull(com.chibialarm.app.WorkoutSetReportCommand.parse(utterance))
    }

    @Test fun editorUsesTheExercisePrescriptionInsteadOfAHardCodedDefault() {
        assertEquals(12, WorkoutRemainingRepTarget.initialValue(null, 12))
        assertEquals(6, WorkoutRemainingRepTarget.initialValue(6, 12))
        assertNull(WorkoutRemainingRepTarget.initialValue(null, 150))
        assertNull(WorkoutRemainingRepTarget.initialValue(101, 12))
    }

    @Test fun malformedPreviousTargetCannotBeAccepted() {
        val proposal = WorkoutRemainingRepTarget.Preview(
            previousTarget = 101,
            requestedTarget = 6,
            completedSets = 1,
            maximumSets = 3
        )
        assertNull(WorkoutRemainingRepTarget.accept(proposal, 101, 1, 3))
    }

    @Test fun invalidCountsAndTargetsAreRejected() {
        assertNull(WorkoutRemainingRepTarget.preview(null, -1, 3, 6))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 0, 6))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 21, 6))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 3, 0))
        assertNull(WorkoutRemainingRepTarget.preview(null, 0, 3, 101))
    }
}
