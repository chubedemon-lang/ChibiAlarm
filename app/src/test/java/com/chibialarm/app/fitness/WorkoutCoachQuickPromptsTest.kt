package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.WorkoutCoachQuestionRouter
import com.chibialarm.app.WorkoutEffortCorrectionRequest
import com.chibialarm.app.WorkoutVoiceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCoachQuickPromptsTest {
    @Test fun activePromptsAreValidMidSessionRequestsAndDoNotAutoApply() {
        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = true,
            hasOptionalCurrentSets = true
        )

        assertEquals(
            listOf("Còn 5 phút", "Hôm nay hơi mệt", "Bỏ bài bổ sung", "Chỉ hiệp bắt buộc", "Thở thế nào?", "Hướng dẫn từng bước"),
            prompts.map { it.label }
        )

        val remaining = WorkoutMidSessionRequest.parse(prompts[0].utterance)
        assertTrue(remaining is WorkoutMidSessionRequest.Result.Proposed)
        assertEquals(5, (remaining as WorkoutMidSessionRequest.Result.Proposed).request.remainingMinutes)

        val easier = WorkoutMidSessionRequest.parse(prompts[1].utterance)
        assertTrue(easier is WorkoutMidSessionRequest.Result.Proposed)
        assertTrue((easier as WorkoutMidSessionRequest.Result.Proposed).request.lowEnergy)
        assertTrue(easier.request.preferEasier)

        val optionalExercises = WorkoutMidSessionRequest.parse(prompts[2].utterance)
        assertTrue(optionalExercises is WorkoutMidSessionRequest.Result.Proposed)
        assertTrue((optionalExercises as WorkoutMidSessionRequest.Result.Proposed).request.removeOptionalExercises)

        val optionalSets = WorkoutMidSessionRequest.parse(prompts[3].utterance)
        assertTrue(optionalSets is WorkoutMidSessionRequest.Result.Proposed)
        assertTrue((optionalSets as WorkoutMidSessionRequest.Result.Proposed).request.removeOptionalSets)

        assertTrue(WorkoutMidSessionRequest.parse(prompts[4].utterance) is WorkoutMidSessionRequest.Result.Unsupported)
        assertTrue(WorkoutMidSessionRequest.parse(prompts[5].utterance) is WorkoutMidSessionRequest.Result.Unsupported)
        assertEquals(WorkoutVoiceCommands.Command.UNKNOWN, WorkoutVoiceCommands.parse(prompts[4].utterance))
        assertEquals(WorkoutVoiceCommands.Command.STEP_GUIDE, WorkoutVoiceCommands.parse(prompts[5].utterance))
    }

    @Test fun unavailableOptionalActionsAreNotSuggested() {
        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false
        )

        assertEquals(listOf("Còn 5 phút", "Hôm nay hơi mệt", "Thở thế nào?", "Hướng dẫn từng bước"), prompts.map { it.label })
    }

    @Test fun restPromptsAreOnlyAvailableDuringRestAndParseAsRestOnly() {
        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = true,
            hasOptionalCurrentSets = true
        )

        assertEquals(2, prompts.size)
        assertEquals("Nghỉ thêm 30 giây", prompts[0].label)
        assertEquals("Nghỉ thêm 1 phút", prompts[1].label)

        prompts.forEachIndexed { index, prompt ->
            val parsed = WorkoutMidSessionRequest.parse(prompt.utterance)
            assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertTrue(request.isRestOnly)
            assertEquals(if (index == 0) 30 else 60, request.restSeconds)
        }
    }

    @Test fun safetyStopHidesEveryPlanChangePrompt() {
        assertTrue(
            WorkoutCoachQuickPrompts.forSession(
                resting = false,
                hasOptionalFutureExercises = true,
                hasOptionalCurrentSets = true,
                safetyStopped = true
            ).isEmpty()
        )
    }


    @Test fun upcomingExercisePromptAppearsOnlyWhenAvailableAndAnswersFromThatMove() {
        val prompt = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            hasNextExercise = true
        ).single { it.label == "Bài tiếp theo" }

        assertEquals("Bài tiếp theo tập thế nào?", prompt.utterance)
        val active = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val upcoming = TrainingCatalog.allMoves.single { it.name == "Đi bộ nhanh tại chỗ" }
        val answer = WorkoutCoachQuestionRouter.answer(
            input = prompt.utterance,
            currentMove = active,
            nextMove = upcoming
        )

        assertTrue(answer.orEmpty().contains("Hướng dẫn ${upcoming.name}"))
        assertFalse(answer.orEmpty().contains(active.name))
    }

    @Test fun upcomingExercisePromptIsHiddenDuringRestWithoutANextMoveOrAfterSafetyStop() {
        val noNext = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false
        )
        assertFalse(noNext.any { it.label == "Bài tiếp theo" })

        val duringRest = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            hasNextExercise = true
        )
        assertFalse(duringRest.any { it.label == "Bài tiếp theo" })

        val safetyStopped = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            safetyStopped = true,
            hasNextExercise = true
        )
        assertTrue(safetyStopped.isEmpty())
    }


    @Test fun effortHistoryPromptAppearsOnlyAfterAConfirmedSetAndUsesTheExistingCoachRouter() {
        val hidden = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false
        )
        assertFalse(hidden.any { it.label == "Xem cảm nhận" })

        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            hasCompletedSets = true
        )
        val historyPrompt = prompts.single { it.label == "Xem cảm nhận" }
        assertEquals("Tổng kết cảm nhận các hiệp đã ghi", historyPrompt.utterance)
        assertFalse(prompts.any { it.label == "Nghỉ theo cảm nhận" })

        val active = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutCoachQuestionRouter.answer(
            input = historyPrompt.utterance,
            currentMove = active,
            completedSets = 1,
            completedEffortLevels = listOf(com.chibialarm.app.WorkoutEffortFeedback.Level.HARD)
        )
        assertTrue(answer.orEmpty().contains("hiệp 1: Khó, cần nghỉ thêm"))
        assertTrue(answer.orEmpty().contains("tự đánh giá"))
    }

    @Test fun effortRestPromptRequiresTheLatestConfirmedRatingAndRemainsReadOnly() {
        val noRating = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            hasCompletedSets = true
        )
        assertFalse(noRating.any { it.label == "Nghỉ theo cảm nhận" })

        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            hasCompletedSets = true,
            hasLatestEffortCheckIn = true
        )
        val restPrompt = prompts.single { it.label == "Nghỉ theo cảm nhận" }
        val active = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }.copy(restSeconds = 45)
        val answer = WorkoutCoachQuestionRouter.answer(
            input = restPrompt.utterance,
            currentMove = active,
            completedSets = 1,
            completedEffortLevels = listOf(com.chibialarm.app.WorkoutEffortFeedback.Level.HARD)
        )

        assertTrue(answer.orEmpty().contains("60 giây"))
        assertTrue(answer.orEmpty().contains("đồng hồ không tự chạy"))
        assertEquals(
            listOf("Nghỉ thêm 30 giây", "Nghỉ thêm 1 phút", "Xem cảm nhận", "Sửa cảm nhận", "Nghỉ theo cảm nhận"),
            prompts.map { it.label }
        )
        val correction = prompts.single { it.label == "Sửa cảm nhận" }
        assertEquals("Sửa cảm nhận hiệp vừa rồi", correction.utterance)
        assertTrue(WorkoutEffortCorrectionRequest.isRequest(correction.utterance))
        assertTrue(WorkoutVoiceCommands.describe(WorkoutVoiceCommands.Command.UNKNOWN, correction.utterance)
            .contains("Sửa cảm nhận tự khai báo"))
    }


    @Test fun correctionPromptAppearsOnlyWhenTheLatestConfirmedSetHasARating() {
        val withoutRating = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            hasCompletedSets = true,
            hasLatestEffortCheckIn = false
        )
        assertFalse(withoutRating.any { it.label == "Sửa cảm nhận" })

        val withRating = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            hasCompletedSets = true,
            hasLatestEffortCheckIn = true
        )
        assertTrue(withRating.any { it.label == "Sửa cảm nhận" })
        assertTrue(withRating.any { it.label == "Nghỉ theo cảm nhận" })
    }

    @Test fun correctionRequestRequiresBothAnExplicitEditAndASetReference() {
        assertTrue(WorkoutEffortCorrectionRequest.isRequest("Sửa cảm nhận hiệp vừa rồi"))
        assertTrue(WorkoutEffortCorrectionRequest.isRequest("Đổi mức cảm nhận hiệp gần nhất"))
        assertTrue(WorkoutEffortCorrectionRequest.isRequest("Chọn nhầm mức cảm nhận hiệp vừa rồi"))
        assertTrue(WorkoutEffortCorrectionRequest.isRequest("Sửa đánh giá hiệp cuối"))
        assertTrue(WorkoutEffortCorrectionRequest.isRequest("Đổi mức gắng sức hiệp gần nhất"))
        assertFalse(WorkoutEffortCorrectionRequest.isRequest("Tổng kết cảm nhận các hiệp đã ghi"))
        assertFalse(WorkoutEffortCorrectionRequest.isRequest("Tôi cảm thấy khó ở hiệp vừa rồi"))
        assertFalse(WorkoutEffortCorrectionRequest.isRequest("Nghỉ theo cảm nhận"))
        assertFalse(WorkoutEffortCorrectionRequest.isRequest("Sửa cảm nhận"))
    }

    @Test fun safetyStopHidesEffortPromptsEvenWhenASetWasRated() {
        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            safetyStopped = true,
            hasCompletedSets = true,
            hasLatestEffortCheckIn = true
        )
        assertTrue(prompts.isEmpty())
    }

    @Test fun helperNeverSubmitsOrChangesSessionState() {
        val first = WorkoutCoachQuickPrompts.forSession(false, true, true)
        val second = WorkoutCoachQuickPrompts.forSession(false, true, true)
        assertEquals(first, second)
        assertFalse(first.any { it.utterance.isBlank() })
    }
}
