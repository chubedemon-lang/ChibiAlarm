package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class WorkoutLibraryStepPreviewTest {
    private fun move(name: String, steps: String) = TrainingMove(
        name = name,
        dose = "2 hiệp × 8–12 lần",
        steps = steps,
        easier = "Giảm biên độ.",
        avoid = "Dừng nếu đau."
    )

    @Test fun selectedStepDrivesMatchingSpokenCueAndIllustrationPhase() {
        val guide = WorkoutLibraryStepPreview.start(
            move("Squat không tạ", "Chân rộng bằng vai.\nHạ xuống theo khả năng.\nĐứng lên.")
        )

        assertEquals(1, guide.position)
        assertNull(guide.illustrationPhase())
        assertTrue(guide.spokenCue().contains("Bước 1 trên 3"))

        val lowering = guide.next()
        assertEquals(2, lowering.position)
        assertEquals(true, lowering.illustrationPhase())
        assertTrue(lowering.spokenCue().contains("Hạ hông"))
        assertTrue(lowering.spokenCue().contains("Bước 2 trên 3"))

        val standing = lowering.next()
        assertEquals(3, standing.position)
        assertEquals(false, standing.illustrationPhase())
        assertTrue(standing.spokenCue().contains("Đứng lên"))
        assertFalse(standing.canGoNext)
    }

    @Test fun stepNavigationNeverLeavesTheExerciseInstructions() {
        val first = WorkoutLibraryStepPreview.start(
            move("Chống đẩy tường", "Đặt tay ngang ngực.\nGập khuỷu.\nĐẩy ra.")
        )
        assertSame(first, first.previous())

        val last = first.next().next()
        assertEquals(3, last.position)
        assertSame(last, last.next())
        assertEquals(2, last.previous().position)
    }

    @Test fun aNewExerciseStartsItsOwnWalkthroughAtStepOne() {
        val squat = WorkoutLibraryStepPreview.start(
            move("Squat không tạ", "Chân rộng.\nHạ xuống.\nĐứng lên.")
        ).next().next()
        val wallPush = WorkoutLibraryStepPreview.start(
            move("Chống đẩy tường", "Đặt tay lên tường.\nGập khuỷu.\nĐẩy ra.")
        )

        assertEquals(3, squat.position)
        assertEquals(1, wallPush.position)
        assertTrue(wallPush.spokenCue().contains("Đặt tay lên tường"))
    }

    @Test fun unknownMovementPhaseRemainsUnclassified() {
        val guide = WorkoutLibraryStepPreview.start(
            move("Động tác chưa có minh họa riêng", "Giữ tư thế thoải mái.")
        )
        assertNull(guide.illustrationPhase())
    }
}
