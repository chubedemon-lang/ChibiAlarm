package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.WorkoutConstraintProfile
import org.junit.Assert.*
import org.junit.Test

class WorkoutMidSessionEffortLanguageTest {
    @Test fun difficultyFeedbackRequestsAReviewableEasierPlanAndRest() {
        listOf("Bài này khó quá", "Tao không theo kịp", "Tôi đuối rồi").forEach { utterance ->
            val result = WorkoutMidSessionRequest.parse(utterance)
            assertTrue("$utterance should be understood", result is WorkoutMidSessionRequest.Result.Proposed)
            val request = (result as WorkoutMidSessionRequest.Result.Proposed).request
            assertTrue("$utterance should mark low energy", request.lowEnergy)
            assertTrue("$utterance should prefer an easier option", request.preferEasier)
            assertEquals("$utterance should suggest a bounded recovery pause", 60, request.restSeconds)
        }
    }

    @Test fun explicitNotDifficultFeedbackDoesNotTriggerAnEasierPlan() {
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Bài này không khó")
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Hôm nay tao không mệt")
        )
    }

    @Test fun painStillOverridesDifficultyAndStopsTheSession() {
        assertSame(
            WorkoutMidSessionRequest.Result.SafetyStop,
            WorkoutMidSessionRequest.parse("Bài này khó quá, đau gối")
        )
    }

    @Test fun difficultyPreviewUsesOnlyCuratedAlternativesAndKeepsCompletedPrefix() {
        fun move(name: String) = TrainingCatalog.allMoves.first { it.name == name }
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Đẩy vai với tạ đơn hoặc dây"),
            move("Kéo tạ đơn hoặc dây kháng lực"),
            move("Thả lỏng")
        )
        val parsed = WorkoutMidSessionRequest.parse("Bài này khó quá")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves.first(), preview.after.first())
        assertTrue(preview.currentMoveChanged)
        assertTrue(preview.replacements.isNotEmpty())
        assertEquals(60, preview.restSeconds)
        assertNotEquals(moves[1].name, preview.after[1].name)
        assertTrue(preview.after.any { candidate ->
            TrainingCatalog.allMoves.any { it == candidate }
        })
    }

    @Test fun difficultyFeedbackNeverRewritesASetAlreadyConfirmed() {
        fun move(name: String) = TrainingCatalog.allMoves.first { it.name == name }
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Đẩy vai với tạ đơn hoặc dây"),
            move("Kéo tạ đơn hoặc dây kháng lực"),
            move("Thả lỏng")
        )
        val parsed = WorkoutMidSessionRequest.parse("Bài này khó quá")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves[1], preview.after[1])
        assertFalse(preview.currentMoveChanged)
        assertEquals(60, preview.restSeconds)
    }
}
