package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.TrainingMove
import com.chibialarm.app.WorkoutConstraintProfile
import org.junit.Assert.*
import org.junit.Test

class WorkoutMidSessionPlannerTest {
    private fun move(name: String): TrainingMove =
        TrainingCatalog.allMoves.first { it.name == name }

    @Test fun compoundVietnameseRequestCapturesTimeEnergyAndWristPreference() {
        val result = WorkoutMidSessionRequest.parse(
            "Còn 10 phút, tao mệt rồi, đổi sang bài nhẹ hơn và đừng chống tay"
        )
        assertTrue(result is WorkoutMidSessionRequest.Result.Proposed)
        val request = (result as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(10, request.remainingMinutes)
        assertTrue(request.lowEnergy)
        assertTrue(request.preferEasier)
        assertTrue(request.avoidWristLoad)
        assertEquals(60, request.restSeconds)
    }

    @Test fun explicitRestIsBoundedAndDoesNotInventAnIntensityChange() {
        val result = WorkoutMidSessionRequest.parse("Nghỉ thêm 30 giây")
        assertTrue(result is WorkoutMidSessionRequest.Result.Proposed)
        val request = (result as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(30, request.restSeconds)
        assertFalse(request.preferEasier)
        assertFalse(request.avoidWristLoad)
    }

    @Test fun localizedPainStopsButBodyPartAloneDoesNot() {
        assertSame(
            WorkoutMidSessionRequest.Result.SafetyStop,
            WorkoutMidSessionRequest.parse("Đau đầu gối, đổi bài nhẹ hơn")
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Đầu gối")
        )
    }

    @Test fun negatedTirednessDoesNotTriggerAnAutomaticRest() {
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Hôm nay tao không mệt")
        )
    }

    @Test fun multiConstraintPreviewPreservesCompletedPrefixAndFiltersRemainingMoves() {
        val moves = listOf(
            move("Squat không tạ"),
            move("Chống đẩy sàn"),
            move("Bird-dog"),
            move("Thả lỏng")
        )
        val request = WorkoutMidSessionRequest.Request(
            remainingMinutes = 10,
            lowEnergy = true,
            avoidWristLoad = true,
            preferEasier = true,
            restSeconds = 60
        )

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
        assertEquals("Ép ngực tường bằng cẳng tay", preview.after[1].name)
        assertEquals("Dead bug cơ bụng", preview.after[2].name)
        assertEquals("Thả lỏng", preview.after.last().name)
        assertTrue(preview.after.drop(1).all {
            com.chibialarm.app.WorkoutConstraintFilter.isAllowed(
                it, WorkoutConstraintProfile(avoidWristLoad = true)
            )
        })
        assertEquals(60, preview.restSeconds)
        assertEquals(10, preview.requestedMinutes)
    }

    @Test fun wristConstraintCannotRewriteAnAlreadyStartedUnsafeExercise() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Chống đẩy sàn"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(avoidWristLoad = true),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun shorterTargetRemovesOptionalExtensionBeforeRequiredBaseWork() {
        val current = TrainingMove(
            "Squat không tạ", "2 hiệp × 8 lần", "Thực hiện có kiểm soát",
            "Giảm biên độ", "Dừng nếu đau", 120, 45
        )
        val extension = TrainingMove(
            "Khối bổ sung", "5 phút", "Vận động nhẹ",
            "Giảm tốc", "Dừng nếu khó chịu", 300
        )
        val cooldown = TrainingMove(
            "Thả lỏng", "2 phút", "Thở chậm",
            "Ngồi nghỉ", "Không kéo đến đau", 120
        )

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(current, extension, cooldown),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(remainingMinutes = 5),
            remainingCurrentSeconds = 300,
            extensionMoveNames = setOf(extension.name),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(listOf(current, cooldown), preview.after)
        assertTrue(preview.removedMoves.contains(extension.name))
        assertTrue(preview.targetExceeded)
        assertEquals(current, preview.after.first())
    }

    @Test fun replacingCurrentMoveUsesTheShorterTimerInPreviewEstimate() {
        val moves = listOf(move("Cat-cow nhẹ"), move("Thả lỏng"))
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(preferEasier = true),
            remainingCurrentSeconds = 70,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertTrue(preview.currentMoveChanged)
        assertEquals("Mở vai và ngực", preview.after.first().name)
        // 60s replacement timer + 180s minimum cool-down + 20s transition.
        // The previous implementation incorrectly retained the old 70s timer in its preview.
        assertEquals(260, preview.estimatedRemainingSeconds)
    }

    @Test fun checkpointPlanRoundTripsOnlyCanonicalCatalogExercises() {
        val moves = listOf(move("Squat không tạ"), move("Dead bug cơ bụng"), move("Thả lỏng"))
        val encoded = WorkoutMidSessionPlanner.encodePlan(moves)
        assertEquals(moves, WorkoutMidSessionPlanner.decodePlan(encoded))
        assertNull(WorkoutMidSessionPlanner.decodePlan("not-a-catalog-exercise"))
        assertNull(WorkoutMidSessionPlanner.decodePlan("x".repeat(8_193)))
    }
}
