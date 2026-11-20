package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExerciseVoiceCueTest {
    @Test fun announcesOnlyInsideTheLastTenSeconds() {
        assertTrue(WorkoutExerciseVoiceCue.shouldAnnounce(10_000L, false, false, false, true))
        assertTrue(WorkoutExerciseVoiceCue.shouldAnnounce(1L, false, false, false, true))
        assertFalse(WorkoutExerciseVoiceCue.shouldAnnounce(10_001L, false, false, false, true))
        assertFalse(WorkoutExerciseVoiceCue.shouldAnnounce(0L, false, false, false, true))
        assertFalse(WorkoutExerciseVoiceCue.shouldAnnounce(-1L, false, false, false, true))
    }

    @Test fun retriesWhileMicrophoneIsActiveWithoutConsumingTheCue() {
        assertFalse(WorkoutExerciseVoiceCue.shouldAnnounce(5_000L, false, false, true, true))
        assertTrue(WorkoutExerciseVoiceCue.shouldAnnounce(4_700L, false, false, false, true))
    }

    @Test fun retriesWhenSpeechEngineIsBusyOrUnavailable() {
        assertFalse(WorkoutExerciseVoiceCue.shouldAnnounce(5_000L, false, false, false, false))
        assertTrue(WorkoutExerciseVoiceCue.shouldAnnounce(4_700L, false, false, false, true))
    }

    @Test fun suppressesCueAfterAnnouncementAndDuringSafetyPause() {
        assertFalse(WorkoutExerciseVoiceCue.shouldAnnounce(5_000L, true, false, false, true))
        assertFalse(WorkoutExerciseVoiceCue.shouldAnnounce(5_000L, false, true, false, true))
    }

    @Test fun messageUsesTheActualRoundedCountdownAndNextCatalogMove() {
        assertEquals(
            "Còn 10 giây. Tiếp theo: Squat không tạ.",
            WorkoutExerciseVoiceCue.message(10_000L, "Squat không tạ")
        )
        assertEquals(
            "Còn 2 giây. Tiếp theo: Squat không tạ.",
            WorkoutExerciseVoiceCue.message(1_001L, "Squat không tạ")
        )
        assertEquals(
            "Còn 1 giây. Tiếp theo: Squat không tạ.",
            WorkoutExerciseVoiceCue.message(1L, "Squat không tạ")
        )
    }

    @Test fun sanitizesAndBoundsTheNextMoveLabel() {
        assertEquals(
            "Còn 5 giây. Tiếp theo: Đi bộ tại chỗ.",
            WorkoutExerciseVoiceCue.message(5_000L, "  Đi bộ\n tại chỗ\u0000 ")
        )
        assertTrue(WorkoutExerciseVoiceCue.message(5_000L, "x".repeat(200))!!.length < 120)
    }

    @Test fun finalMoveHasACompletionCueWithoutInventingAnotherExercise() {
        assertEquals(
            "Còn 3 giây. Sắp hoàn thành động tác này.",
            WorkoutExerciseVoiceCue.message(3_000L, null)
        )
        assertNull(WorkoutExerciseVoiceCue.message(10_001L, "Squat"))
        assertNull(WorkoutExerciseVoiceCue.message(0L, "Squat"))
    }
}
