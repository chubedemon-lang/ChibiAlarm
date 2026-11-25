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
            listOf("Còn 5 phút", "Còn 10 phút", "Bài này khó quá", "Hôm nay hơi mệt", "Bỏ bài bổ sung", "Chỉ hiệp bắt buộc", "Thở thế nào?", "Hướng dẫn từng bước"),
            prompts.map { it.label }
        )

        val remaining = WorkoutMidSessionRequest.parse(
            prompts.single { it.label == "Còn 5 phút" }.utterance
        )
        assertTrue(remaining is WorkoutMidSessionRequest.Result.Proposed)
        assertEquals(5, (remaining as WorkoutMidSessionRequest.Result.Proposed).request.remainingMinutes)

        val tenMinuteTarget = WorkoutMidSessionRequest.parse(
            prompts.single { it.label == "Còn 10 phút" }.utterance
        )
        assertTrue(tenMinuteTarget is WorkoutMidSessionRequest.Result.Proposed)
        assertEquals(
            10,
            (tenMinuteTarget as WorkoutMidSessionRequest.Result.Proposed).request.remainingMinutes
        )
        assertFalse(tenMinuteTarget.request.lowEnergy)

        val difficult = WorkoutMidSessionRequest.parse(
            prompts.single { it.label == "Bài này khó quá" }.utterance
        )
        assertTrue(difficult is WorkoutMidSessionRequest.Result.Proposed)
        assertTrue((difficult as WorkoutMidSessionRequest.Result.Proposed).request.preferEasier)
        assertTrue(difficult.request.lowEnergy)
        assertEquals(60, difficult.request.restSeconds)

        val easier = WorkoutMidSessionRequest.parse(
            prompts.single { it.label == "Hôm nay hơi mệt" }.utterance
        )
        assertTrue(easier is WorkoutMidSessionRequest.Result.Proposed)
        assertTrue((easier as WorkoutMidSessionRequest.Result.Proposed).request.lowEnergy)
        assertTrue(easier.request.preferEasier)

        val optionalExercises = WorkoutMidSessionRequest.parse(
            prompts.single { it.label == "Bỏ bài bổ sung" }.utterance
        )
        assertTrue(optionalExercises is WorkoutMidSessionRequest.Result.Proposed)
        assertTrue((optionalExercises as WorkoutMidSessionRequest.Result.Proposed).request.removeOptionalExercises)

        val optionalSets = WorkoutMidSessionRequest.parse(
            prompts.single { it.label == "Chỉ hiệp bắt buộc" }.utterance
        )
        assertTrue(optionalSets is WorkoutMidSessionRequest.Result.Proposed)
        assertTrue((optionalSets as WorkoutMidSessionRequest.Result.Proposed).request.removeOptionalSets)

        val breathingPrompt = prompts.single { it.label == "Thở thế nào?" }
        val stepGuidePrompt = prompts.single { it.label == "Hướng dẫn từng bước" }
        assertTrue(WorkoutMidSessionRequest.parse(breathingPrompt.utterance) is WorkoutMidSessionRequest.Result.Unsupported)
        assertTrue(WorkoutMidSessionRequest.parse(stepGuidePrompt.utterance) is WorkoutMidSessionRequest.Result.Unsupported)
        assertEquals(WorkoutVoiceCommands.Command.UNKNOWN, WorkoutVoiceCommands.parse(breathingPrompt.utterance))
        assertEquals(WorkoutVoiceCommands.Command.STEP_GUIDE, WorkoutVoiceCommands.parse(stepGuidePrompt.utterance))
    }

    @Test fun unavailableOptionalActionsAreNotSuggested() {
        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false
        )

        assertEquals(
            listOf("Còn 5 phút", "Còn 10 phút", "Bài này khó quá", "Hôm nay hơi mệt", "Thở thế nào?", "Hướng dẫn từng bước"),
            prompts.map { it.label }
        )
    }

    @Test fun remainingPlanShowsOnlyRelevantKneelingAndFloorConstraintShortcuts() {
        val birdDog = TrainingCatalog.allMoves.single { it.name == "Bird-dog" }
        val bridge = TrainingCatalog.allMoves.single { it.name == "Nâng hông trên sàn" }
        val prompts = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(birdDog, bridge)
        )

        val kneelingPrompt = prompts.single { it.label == "Không quỳ gối" }
        val floorPrompt = prompts.single { it.label == "Không nằm sàn" }
        assertEquals("Từ giờ không quỳ gối", kneelingPrompt.utterance)
        assertEquals("Từ giờ không nằm sàn", floorPrompt.utterance)

        val kneeling = WorkoutMidSessionRequest.parse(kneelingPrompt.utterance)
        assertTrue(kneeling is WorkoutMidSessionRequest.Result.Proposed)
        val kneelingRequest = (kneeling as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(kneelingRequest.avoidKneeling)
        assertFalse(kneelingRequest.avoidFloorPositions)

        val floor = WorkoutMidSessionRequest.parse(floorPrompt.utterance)
        assertTrue(floor is WorkoutMidSessionRequest.Result.Proposed)
        val floorRequest = (floor as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(floorRequest.avoidFloorPositions)
        assertFalse(floorRequest.avoidKneeling)
    }

    @Test fun constraintShortcutsStayHiddenWhenNoRemainingMoveNeedsThemOrDuringRest() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val birdDog = TrainingCatalog.allMoves.single { it.name == "Bird-dog" }
        val bridge = TrainingCatalog.allMoves.single { it.name == "Nâng hông trên sàn" }

        val unaffected = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(squat)
        )
        assertFalse(unaffected.any { it.label == "Không quỳ gối" || it.label == "Không nằm sàn" })

        val duringRest = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(birdDog, bridge)
        )
        assertFalse(duringRest.any { it.label == "Không quỳ gối" || it.label == "Không nằm sàn" })
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
    @Test fun noJumpingShortcutAppearsOnlyWhenTheRemainingPlanContainsImpactExercise() {
        val jumpingJacks = TrainingCatalog.allMoves.single { it.name == "Bật nhảy dang tay chân" }
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }

        val relevant = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(squat, jumpingJacks)
        )
        val shortcut = relevant.single { it.label == "Không bật nhảy" }
        assertEquals("Từ giờ không bật nhảy", shortcut.utterance)
        val parsed = WorkoutMidSessionRequest.parse(shortcut.utterance)
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.avoidJumping)
        assertFalse(request.avoidKneeling)
        assertFalse(request.avoidFloorPositions)

        val irrelevant = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(squat)
        )
        assertFalse(irrelevant.any { it.label == "Không bật nhảy" })

        val duringRest = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(jumpingJacks)
        )
        assertFalse(duringRest.any { it.label == "Không bật nhảy" })
    }

    @Test fun overheadReachShortcutAppearsOnlyForRemainingMovesThatNeedIt() {
        val overheadMove = TrainingCatalog.allMoves.single { it.requiresOverheadReach }
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val relevant = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(squat, overheadMove)
        )
        val shortcut = relevant.single { it.label == "Không với tay quá đầu" }
        assertEquals("Từ giờ không với tay quá đầu", shortcut.utterance)
        val parsed = WorkoutMidSessionRequest.parse(shortcut.utterance)
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.avoidOverheadReach)
        assertFalse(request.allowOverheadReach)

        val irrelevant = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(squat)
        )
        assertFalse(irrelevant.any { it.label == "Không với tay quá đầu" })

        val duringRest = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(overheadMove)
        )
        assertFalse(duringRest.any { it.label == "Không với tay quá đầu" })
    }


    @Test fun wristLoadingShortcutAppearsOnlyWhenRemainingMoveNeedsWristSupportOrExternalLoad() {
        val row = TrainingCatalog.allMoves.single { it.name == "Kéo tạ đơn hoặc dây kháng lực" }
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }

        val relevant = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(squat, row)
        )
        val shortcut = relevant.single { it.label == "Không chống tay chịu lực" }
        assertEquals("Từ giờ tránh các bài phải chống tay chịu lực", shortcut.utterance)
        val avoidWrist = WorkoutConstraintProfile(avoidWristLoad = true)
        assertFalse(WorkoutConstraintFilter.isAllowed(row, avoidWrist))
        assertTrue(WorkoutConstraintFilter.isAllowed(squat, avoidWrist))

        val irrelevant = WorkoutCoachQuickPrompts.forSession(
            resting = false,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(squat)
        )
        assertFalse(irrelevant.any { it.label == "Không chống tay chịu lực" })

        val duringRest = WorkoutCoachQuickPrompts.forSession(
            resting = true,
            hasOptionalFutureExercises = false,
            hasOptionalCurrentSets = false,
            remainingUnstartedMoves = listOf(row)
        )
        assertFalse(duringRest.any { it.label == "Không chống tay chịu lực" })
    }

}
