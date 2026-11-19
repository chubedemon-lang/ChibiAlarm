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

    @Test fun contextualTargetsInferDroppedUnitsOnlyFromTheActiveExercise() {
        assertEquals(
            WorkoutTargetCommand.SpokenTarget(8, "lần"),
            WorkoutTargetCommand.spokenTarget("Giảm xuống còn tám", expectedUnit = "lần")
        )
        assertEquals(
            WorkoutTargetCommand.SpokenTarget(8, "lần"),
            WorkoutTargetCommand.spokenTarget("Giảm còn 8", expectedUnit = "lần")
        )
        assertNull(WorkoutTargetCommand.spokenTarget("Giảm còn 8"))
        assertNull(WorkoutTargetCommand.spokenTarget("Nghỉ thêm 30 giây", expectedUnit = "lần"))
    }

    @Test fun contextualTargetCanFollowRestAndKeepsSafetyPriority() {
        assertEquals(
            WorkoutTargetCommand.SpokenTarget(8, "lần"),
            WorkoutTargetCommand.spokenTarget(
                "Nghỉ thêm 30 giây rồi giảm xuống còn 8",
                expectedUnit = "lần"
            )
        )
        assertEquals(
            WorkoutTargetCommand.SpokenTarget(8, "giây"),
            WorkoutTargetCommand.spokenTarget("Giảm xuống còn 8", expectedUnit = "giây")
        )
        assertNull(WorkoutTargetCommand.spokenTarget("Đau gối rồi giảm còn 8", expectedUnit = "lần"))
        assertNull(WorkoutTargetCommand.spokenTarget("Dừng tập rồi giảm còn 8", expectedUnit = "lần"))
        assertNull(WorkoutTargetCommand.spokenTarget("Giảm còn 8", expectedUnit = "unknown"))
    }

    @Test fun spokenTargetsPreserveTheExerciseUnitAndDoNotConfuseRestWithPlankTime() {
        assertEquals(WorkoutTargetCommand.SpokenTarget(6, "lần"),
            WorkoutTargetCommand.spokenTarget("Giảm còn 6 lần"))
        assertEquals(WorkoutTargetCommand.SpokenTarget(8, "nhịp"),
            WorkoutTargetCommand.spokenTarget("Đổi thành tám nhịp"))
        assertEquals(WorkoutTargetCommand.SpokenTarget(20, "giây"),
            WorkoutTargetCommand.spokenTarget("Giảm còn hai mươi giây"))
        assertNull(WorkoutTargetCommand.spokenTarget("Nghỉ thêm 30 giây"))
        assertEquals(WorkoutTargetCommand.SpokenTarget(6, "lần"),
            WorkoutTargetCommand.spokenTarget("Nghỉ thêm 30 giây rồi giảm còn 6 lần"))
        assertNull(WorkoutTargetCommand.spokenTarget("Đau gối, giảm còn 20 giây"))
        assertNull(WorkoutTargetCommand.spokenTarget("Dừng tập và giảm còn 6 lần"))
    }


    @Test fun spokenRelativeTargetsResolveOnlyFromAnExactActiveRepBaseline() {
        assertEquals(
            WorkoutTargetCommand.SpokenTarget(10, "lần"),
            WorkoutTargetCommand.spokenTarget(
                "Giảm 2 lần", expectedUnit = "lần", currentTarget = 12
            )
        )
        assertEquals(
            WorkoutTargetCommand.SpokenTarget(14, "lần"),
            WorkoutTargetCommand.spokenTarget(
                "Thêm hai lần", expectedUnit = "lần", currentTarget = 12
            )
        )
        assertEquals(
            WorkoutTargetCommand.SpokenTarget(10, "lần"),
            WorkoutTargetCommand.spokenTarget(
                "Bớt hai lần và nghỉ 90 giây", expectedUnit = "lần", currentTarget = 12
            )
        )
        assertNull(WorkoutTargetCommand.spokenTarget("Giảm 2 lần", expectedUnit = "nhịp", currentTarget = 12))
        assertNull(WorkoutTargetCommand.spokenTarget("Giảm 2 lần", expectedUnit = "lần"))
        assertNull(WorkoutTargetCommand.spokenTarget("Giảm 2 lần", expectedUnit = "lần", currentTarget = 0))
        assertNull(WorkoutTargetCommand.spokenTarget("Giảm 13 lần", expectedUnit = "lần", currentTarget = 12))
        assertNull(WorkoutTargetCommand.spokenTarget("Thêm 100 lần", expectedUnit = "lần", currentTarget = 12))
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

    @Test fun stepGuideAndRepeatStepRequestsCannotBeMisroutedIntoRepetitionEditor() {
        val utterances = listOf(
            "Hướng dẫn từng bước và giảm còn 6 lần",
            "Đọc lại bước vừa rồi và giảm còn 6 lần",
            "Nhắc lại bước này rồi đổi thành 8 lần"
        )
        for (utterance in utterances) {
            assertNull(utterance, WorkoutTargetCommand.spokenReps(utterance))
            assertSame(utterance, WorkoutTargetCommand.Result.Unsupported,
                WorkoutTargetCommand.propose(utterance, "squat", session, 0))
        }
    }

    @Test fun restAndRepetitionRemainValidTogether() {
        val result = WorkoutTargetCommand.propose(
            "Giảm còn 6 lần và nghỉ 90 giây", "squat", session, 0)
        assertTrue(result is WorkoutTargetCommand.Result.Proposed)
        val after = (result as WorkoutTargetCommand.Result.Proposed).preview.after
        assertEquals(6, after.sets[0].reps)
        assertEquals(90, after.sets[0].restSeconds)
    }

    @Test fun skipRestCommandsCannotBeMisroutedIntoRepetitionEditor() {
        for (utterance in listOf("Bỏ qua nghỉ và giảm còn 6 lần", "Kết thúc nghỉ rồi đổi thành 8 lần")) {
            assertEquals(WorkoutVoiceCommands.Command.SKIP_REST, WorkoutVoiceCommands.parse(utterance))
            assertNull(utterance, WorkoutTargetCommand.spokenReps(utterance))
            assertSame(utterance, WorkoutTargetCommand.Result.Unsupported,
                WorkoutTargetCommand.propose(utterance, "squat", session, 0))
        }
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

    @Test fun parsesVietnameseHundredsAndNaturalSpokenRestDurations() {
        assertEquals(21, WorkoutTargetCommand.spokenReps("Giảm còn hai mươi mốt lần"))
        assertEquals(100, WorkoutTargetCommand.spokenReps("Đổi thành một trăm lần"))

        val sixHundredSeconds = WorkoutTargetCommand.propose(
            "Nghỉ sáu trăm giây", "squat", session, 0
        )
        assertTrue(sixHundredSeconds is WorkoutTargetCommand.Result.Proposed)
        assertEquals(
            600,
            (sixHundredSeconds as WorkoutTargetCommand.Result.Proposed)
                .preview.after.sets[0].restSeconds
        )

        val oneHundredFive = WorkoutTargetCommand.propose(
            "Nghỉ một trăm linh năm giây", "squat", session, 0
        )
        assertTrue(oneHundredFive is WorkoutTargetCommand.Result.Proposed)
        assertEquals(
            105,
            (oneHundredFive as WorkoutTargetCommand.Result.Proposed)
                .preview.after.sets[0].restSeconds
        )

        val oneHundredFifteen = WorkoutTargetCommand.propose(
            "Nghỉ một trăm mười lăm giây", "squat", session, 0
        )
        assertTrue(oneHundredFifteen is WorkoutTargetCommand.Result.Proposed)
        assertEquals(
            115,
            (oneHundredFifteen as WorkoutTargetCommand.Result.Proposed)
                .preview.after.sets[0].restSeconds
        )

        val mixedUnits = WorkoutTargetCommand.propose(
            "Nghỉ một phút ba mươi giây", "squat", session, 0
        )
        assertTrue(mixedUnits is WorkoutTargetCommand.Result.Proposed)
        assertEquals(
            90,
            (mixedUnits as WorkoutTargetCommand.Result.Proposed)
                .preview.after.sets[0].restSeconds
        )
    }

    @Test fun ambiguousAndOutOfRangeSpokenHundredsAreRejected() {
        assertSame(
            WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Nghỉ một trăm năm giây", "squat", session, 0)
        )
        assertSame(
            WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Nghỉ sáu trăm linh một giây", "squat", session, 0)
        )
        assertSame(
            WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Đổi thành một trăm linh một lần", "squat", session, 0)
        )
    }

    private val uniformSquatSession = CoachSessionPlanner.Session(listOf(
        CoachSessionPlanner.SetPlan("squat", 12, 45),
        CoachSessionPlanner.SetPlan("walk", 20, 30),
        CoachSessionPlanner.SetPlan("squat", 12, 60)
    ))

    @Test fun relativeDecreaseUsesOnlyTheActivePlanAndNeverRewritesCompletedSets() {
        val result = WorkoutTargetCommand.propose(
            "Giảm 2 lần", "squat", uniformSquatSession, completedSets = 1
        )
        assertTrue(result is WorkoutTargetCommand.Result.Proposed)
        val preview = (result as WorkoutTargetCommand.Result.Proposed).preview
        assertEquals(uniformSquatSession.sets[0], preview.after.sets[0])
        assertEquals(uniformSquatSession.sets[1], preview.after.sets[1])
        assertEquals(10, preview.after.sets[2].reps)
        assertNull(WorkoutTargetCommand.spokenTarget("Giảm 2 lần", expectedUnit = "lần"))
    }

    @Test fun relativeIncreaseIsPreviewedAgainstTheCurrentUnfinishedTarget() {
        val result = WorkoutTargetCommand.propose(
            "Thêm hai lần", "squat", uniformSquatSession, completedSets = 0
        )
        assertTrue(result is WorkoutTargetCommand.Result.Proposed)
        val after = (result as WorkoutTargetCommand.Result.Proposed).preview.after
        assertEquals(14, after.sets[0].reps)
        assertEquals(14, after.sets[2].reps)
    }

    @Test fun relativeEditsRejectMixedBaselinesAndOutOfRangeResults() {
        assertSame(
            WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Bớt 2 lần", "squat", session, completedSets = 0)
        )
        assertSame(
            WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Bớt 13 lần", "squat", uniformSquatSession, completedSets = 0)
        )
        assertSame(
            WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Tăng thêm 100 lần", "squat", uniformSquatSession, completedSets = 0)
        )
    }

    @Test fun relativeRepChangeCanIncludeRestButSafetyAndNavigationStillWin() {
        val result = WorkoutTargetCommand.propose(
            "Bớt hai lần và nghỉ 90 giây", "squat", uniformSquatSession, completedSets = 0
        )
        assertTrue(result is WorkoutTargetCommand.Result.Proposed)
        val after = (result as WorkoutTargetCommand.Result.Proposed).preview.after
        assertEquals(10, after.sets[0].reps)
        assertEquals(90, after.sets[0].restSeconds)
        assertEquals(10, after.sets[2].reps)
        assertEquals(90, after.sets[2].restSeconds)
        assertSame(
            WorkoutTargetCommand.Result.SafetyStop,
            WorkoutTargetCommand.propose("Đau gối, bớt hai lần", "squat", uniformSquatSession, 0)
        )
        assertSame(
            WorkoutTargetCommand.Result.Unsupported,
            WorkoutTargetCommand.propose("Dừng tập rồi bớt hai lần", "squat", uniformSquatSession, 0)
        )
    }

}
