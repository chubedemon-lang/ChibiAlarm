package com.chibialarm.app.fitness

import org.junit.Assert.*
import org.junit.Test
import com.chibialarm.app.WorkoutVoiceCommands

class WorkoutTargetCommandTest {
    @Test fun spokenRepsAcceptsVietnameseWordsAndRejectsPainOrInvalidNumbers() {
        assertEquals(25, WorkoutTargetCommand.spokenReps("Giảm xuống hai mươi lăm lần"))
        assertEquals(6, WorkoutTargetCommand.spokenReps("Đổi thành 6 lần"))
        assertNull(WorkoutTargetCommand.spokenReps("Tôi đau, giảm xuống 6 lần"))
        assertNull(WorkoutTargetCommand.spokenReps("Tăng lên 101 lần"))
        assertNull(WorkoutTargetCommand.spokenReps("Nghỉ thêm 30 giây"))
    }

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
    @Test fun spokenVietnameseNumbersWorkOffline() {
        val result = WorkoutTargetCommand.propose(
            "Giảm xuống sáu lần và nghỉ hai phút", "squat", session, 1)
        assertTrue(result is WorkoutTargetCommand.Result.Proposed)
        val after = (result as WorkoutTargetCommand.Result.Proposed).preview.after
        assertEquals(session.sets[0], after.sets[0])
        assertEquals(6, after.sets[2].reps)
        assertEquals(120, after.sets[2].restSeconds)
    }

    @Test fun spokenNumbersRespectSafetyAndRange() {
        assertSame(WorkoutTargetCommand.Result.SafetyStop,
            WorkoutTargetCommand.propose("Đau gối, nghỉ hai phút", "squat", session, 0))
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Tập mười phút", "squat", session, 0))
    }

    @Test fun understandsCompoundVietnameseNumbersAndMixedRestUnits() {
        val proposal = WorkoutTargetCommand.propose(
            "Giảm xuống hai mươi lăm lần và nghỉ một phút ba mươi giây",
            "squat", session, 1)
        assertTrue(proposal is WorkoutTargetCommand.Result.Proposed)
        val after = (proposal as WorkoutTargetCommand.Result.Proposed).preview.after
        assertEquals(session.sets[0], after.sets[0])
        assertEquals(25, after.sets[2].reps)
        assertEquals(90, after.sets[2].restSeconds)
    }

    @Test fun acceptsTeenCountsAndRejectsInvalidSeconds() {
        val teen = WorkoutTargetCommand.propose("Còn mười lăm lần", "squat", session, 0)
        assertEquals(15, (teen as WorkoutTargetCommand.Result.Proposed).preview.after.sets[0].reps)
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Nghỉ hai phút bảy mươi giây", "squat", session, 0))
        assertSame(WorkoutTargetCommand.Result.SafetyStop,
            WorkoutTargetCommand.propose("Đau gối, nghỉ một phút ba mươi giây", "squat", session, 0))
    }

    @Test fun competingNavigationOrPauseNeverOpensTargetEditor() {
        val utterances = listOf(
            "Dừng tập và giảm còn 6 lần",
            "Chuyển bài và giảm còn 6 lần",
            "Tiếp tục và giảm còn 6 lần",
            "Bài trước rồi giảm còn 6 lần",
            "Tập dễ hơn và giảm còn 6 lần"
        )
        for (spoken in utterances) {
            assertNull(spoken, WorkoutTargetCommand.spokenReps(spoken))
            assertSame(spoken, WorkoutTargetCommand.Result.Unsupported,
                WorkoutTargetCommand.propose(spoken, "squat", session, 0))
        }
        assertSame(WorkoutTargetCommand.Result.SafetyStop,
            WorkoutTargetCommand.propose("Khó thở và giảm còn 6 lần", "squat", session, 0))
    }

    @Test fun statusRequestCannotBeMistakenForARepetitionEdit() {
        val utterance = "Đọc tiến độ và giảm còn 6 lần"
        assertNull(WorkoutTargetCommand.spokenReps(utterance))
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose(utterance, "squat", session, 0))
    }

    @Test fun spokenSetCompletionCannotBeMisroutedIntoTargetEditor() {
        for (utterance in listOf(
            "Xong hiệp và giảm còn 6 lần",
            "Hoàn thành hiệp rồi tăng lên 8 lần",
            "Xác nhận hiệp, đổi thành 10 lần"
        )) {
            assertNull(utterance, WorkoutTargetCommand.spokenReps(utterance))
            assertSame(utterance, WorkoutTargetCommand.Result.Unsupported,
                WorkoutTargetCommand.propose(utterance, "squat", session, 0))
        }
    }

    @Test fun stepGuideRequestCannotBeMisroutedIntoRepetitionEditor() {
        val utterance = "Hướng dẫn từng bước và giảm còn 6 lần"
        assertNull(WorkoutTargetCommand.spokenReps(utterance))
        assertSame(WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose(utterance, "squat", session, 0))
    }

    @Test fun restAndRepetitionRemainValidTogether() {
        val result = WorkoutTargetCommand.propose(
            "Giảm còn 6 lần và nghỉ 90 giây", "squat", session, 0)
        assertTrue(result is WorkoutTargetCommand.Result.Proposed)
        val after = (result as WorkoutTargetCommand.Result.Proposed).preview.after
        assertEquals(6, after.sets[0].reps)
        assertEquals(90, after.sets[0].restSeconds)
    }


    @Test fun noEquipmentCommandsCannotBeMisroutedIntoRepetitionEditor() {
        val utterances = listOf(
            "Hôm nay không có dụng cụ, đổi sang bài tay không",
            "Không có tạ, đổi bài tập"
        )
        for (utterance in utterances) {
            assertEquals(WorkoutVoiceCommands.Command.NO_EQUIPMENT,
                WorkoutVoiceCommands.parse(utterance))
            assertNull(utterance, WorkoutTargetCommand.spokenReps(utterance))
            assertSame(WorkoutTargetCommand.Result.Unsupported,
                WorkoutTargetCommand.propose(utterance, "squat", session, 0))
        }
    }
}
