package com.chibialarm.app.fitness

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

    @Test fun helperNeverSubmitsOrChangesSessionState() {
        val first = WorkoutCoachQuickPrompts.forSession(false, true, true)
        val second = WorkoutCoachQuickPrompts.forSession(false, true, true)
        assertEquals(first, second)
        assertFalse(first.any { it.utterance.isBlank() })
    }
}
