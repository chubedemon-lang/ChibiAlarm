package com.chibialarm.app.fitness

import org.junit.Assert.*
import org.junit.Test

class WorkoutTargetCommandTest {
    private val session = CoachSessionPlanner.Session(listOf(
        CoachSessionPlanner.SetPlan("squat", 12, 45),
        CoachSessionPlanner.SetPlan("walk", 20, 30),
        CoachSessionPlanner.SetPlan("squat", 10, 60)
    ))

    @Test fun parsesCombinedRepsAndRestWithoutMutatingCompletedSets() {
        val result = WorkoutTargetCommand.propose("Giảm xuống 6 lần và nghỉ 90 giây", "squat", session, 1)
        assertTrue(result is WorkoutTargetCommand.Result.Proposed)
        val preview = (result as WorkoutTargetCommand.Result.Proposed).preview
        assertEquals(session.sets[0], preview.after.sets[0])
        assertEquals(session.sets[1], preview.after.sets[1])
        assertEquals(6, preview.after.sets[2].reps)
        assertEquals(90, preview.after.sets[2].restSeconds)
        assertEquals(session, preview.before)
        assertEquals(preview.after, WorkoutTargetEdit.accept(preview, session, 1))
    }

    @Test fun parsesMinutesAndRejectsOutOfRangeValues() {
        val result = WorkoutTargetCommand.propose("Nghỉ 2 phút", "squat", session, 0)
        assertEquals(120, (result as WorkoutTargetCommand.Result.Proposed).preview.after.sets[0].restSeconds)
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Nghỉ 11 phút", "squat", session, 0))
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Đổi thành 0 lần", "squat", session, 0))
    }

    @Test fun painAndSafetyStopAlwaysWin() {
        assertSame(WorkoutTargetCommand.Result.SafetyStop,
            WorkoutTargetCommand.propose("Đau gối, giảm còn 5 lần", "squat", session, 0))
        assertSame(WorkoutTargetCommand.Result.SafetyStop,
            WorkoutTargetCommand.propose("Nghỉ 60 giây", "squat",
                session.copy(pausedForSafety = true), 0))
    }

    @Test fun doesNotInterpretUnrelatedOrMissingExercise() {
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Hôm nay tôi tập 20 phút", "squat", session, 0))
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Giảm còn 5 lần", "pushup", session, 0))
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Giảm còn 5 lần", "squat", session, 3))
    }

    @Test fun staleProposalCannotOverwriteProgress() {
        val proposal = (WorkoutTargetCommand.propose("Còn 5 lần", "squat", session, 0)
            as WorkoutTargetCommand.Result.Proposed).preview
        assertNull(WorkoutTargetEdit.accept(proposal, session, 1))
        assertNull(WorkoutTargetEdit.accept(proposal, session.copy(pausedForSafety = true), 0))
    }
}
