package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingMove
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSessionTimeEstimateTest {
    private val moves = listOf(
        TrainingMove("Khởi động", "1 phút", "Bắt đầu chậm", "Đi chậm", "Dừng nếu chóng mặt", 60),
        TrainingMove("Squat", "2 hiệp × 8 lần", "Đứng vững", "Giảm biên độ", "Dừng nếu đau", 90, 30),
        TrainingMove("Thả lỏng", "1 phút", "Thở chậm", "Ngồi nghỉ", "Không kéo đến đau", 60)
    )

    @Test fun countsCurrentTimerFutureDoseRestsAndTransitions() {
        val estimate = WorkoutSessionTimeEstimate.seconds(
            moves = moves,
            currentIndex = 0,
            completedMoves = emptySet(),
            completedSets = emptyMap(),
            currentExerciseRemainingSeconds = 20,
            resting = false,
            restSecondsRemaining = 0,
            level = 0
        )
        // 20 current + (90 active + 30 between-set rest) + 60 cooldown + 40 transitions.
        assertEquals(240, estimate)
    }

    @Test fun usesOnlyTheRemainingCurrentRestAndFutureBetweenSetRests() {
        val estimate = WorkoutSessionTimeEstimate.seconds(
            moves = moves,
            currentIndex = 1,
            completedMoves = setOf(0),
            completedSets = mapOf(1 to 1),
            currentExerciseRemainingSeconds = 0,
            resting = true,
            restSecondsRemaining = 18,
            level = 1
        )
        // 45 seconds for the one unconfirmed set + 18 rest + 60 cool-down + 15 transition.
        assertEquals(138, estimate)
    }

    @Test fun completedMovesAreSkippedButTheNextTransitionIsIncluded() {
        val estimate = WorkoutSessionTimeEstimate.seconds(
            moves = moves,
            currentIndex = 1,
            completedMoves = setOf(0, 1),
            completedSets = mapOf(1 to 2),
            currentExerciseRemainingSeconds = 0,
            resting = false,
            restSecondsRemaining = 0,
            level = 2
        )
        assertEquals(70, estimate)
    }

    @Test fun currentRestRemainsVisibleAfterMinimumSetsWereRecorded() {
        val estimate = WorkoutSessionTimeEstimate.seconds(
            moves = moves,
            currentIndex = 1,
            completedMoves = setOf(0, 1),
            completedSets = mapOf(1 to 2),
            currentExerciseRemainingSeconds = 0,
            resting = true,
            restSecondsRemaining = 25,
            level = 0
        )
        // The optional-set rest is visible, followed by the cooldown and transition.
        assertEquals(105, estimate)
    }

    @Test fun pausedExerciseTimerIsCountedAlongsideTheCurrentRest() {
        val estimate = WorkoutSessionTimeEstimate.seconds(
            moves = moves,
            currentIndex = 1,
            completedMoves = setOf(0),
            completedSets = mapOf(1 to 1),
            // startSetRest pauses the exercise clock; its remaining time is still pending.
            currentExerciseRemainingSeconds = 42,
            resting = true,
            restSecondsRemaining = 18,
            level = 1
        )
        // 42 active + 18 current rest + 60 cool-down + 15 transition.
        assertEquals(135, estimate)
    }

    @Test fun expiredTimerEstimatesRemainingSetWorkFromThePrescription() {
        val estimate = WorkoutSessionTimeEstimate.seconds(
            moves = moves,
            currentIndex = 1,
            completedMoves = setOf(0),
            completedSets = emptyMap(),
            currentExerciseRemainingSeconds = 0,
            resting = false,
            restSecondsRemaining = 0,
            level = 1
        )
        // The full 90-second active block + 30 prescribed rest + 60 cool-down + 15 transition.
        assertEquals(195, estimate)
    }

    @Test fun estimateIsBoundedAndRejectsInvalidSessionState() {
        val huge = List(500) {
            TrainingMove("Timed $it", "60 phút", "Move", "Easy", "Stop if painful", 3_600)
        }
        val estimate = WorkoutSessionTimeEstimate.seconds(
            moves = huge,
            currentIndex = 0,
            completedMoves = emptySet(),
            completedSets = emptyMap(),
            currentExerciseRemainingSeconds = 600,
            resting = false,
            restSecondsRemaining = 0,
            level = 0
        )
        assertEquals(86_400, estimate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCurrentIndex() {
        WorkoutSessionTimeEstimate.seconds(
            moves = moves,
            currentIndex = 3,
            completedMoves = emptySet(),
            completedSets = emptyMap(),
            currentExerciseRemainingSeconds = 0,
            resting = false,
            restSecondsRemaining = 0,
            level = 0
        )
    }
}
