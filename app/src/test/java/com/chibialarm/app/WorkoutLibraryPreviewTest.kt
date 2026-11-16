package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLibraryPreviewTest {
    private fun move(name: String, dose: String = "2 hiệp × 8 lần") = TrainingMove(
        name = name,
        dose = dose,
        steps = "Đứng vững. Hạ người có kiểm soát.\nThở đều.",
        easier = "Giảm biên độ.",
        avoid = "Dừng nếu đau."
    )

    @Test fun startsAtFirstMoveAndExposesTheCurrentDoseAndInstructions() {
        val preview = WorkoutLibraryPreview.start(listOf(move("Squat")))!!

        assertEquals(1, preview.position)
        assertEquals(1, preview.size)
        assertEquals("Squat", preview.currentMove.name)
        assertEquals("2 hiệp × 8 lần", preview.currentMove.dose)
        assertEquals(
            listOf("Đứng vững.", "Hạ người có kiểm soát.", "Thở đều."),
            WorkoutStepGuide.split(preview.currentMove.steps)
        )
        assertFalse(preview.canGoPrevious)
        assertFalse(preview.canGoNext)
    }

    @Test fun navigationMovesOneExerciseAtATimeAndNeverLeavesThePlan() {
        val preview = WorkoutLibraryPreview.start(
            listOf(move("Squat"), move("March"), move("Stretch"))
        )!!

        val second = preview.next()
        assertEquals(2, second.position)
        assertEquals("March", second.currentMove.name)
        assertTrue(second.canGoPrevious)
        assertTrue(second.canGoNext)

        val third = second.next()
        assertEquals(3, third.position)
        assertEquals("Stretch", third.currentMove.name)
        assertFalse(third.canGoNext)
        assertSame(third, third.next())

        val backToFirst = third.previous().previous()
        assertEquals(1, backToFirst.position)
        assertEquals("Squat", backToFirst.currentMove.name)
        assertFalse(backToFirst.canGoPrevious)
        assertSame(backToFirst, backToFirst.previous())
    }

    @Test fun previewOwnsASnapshotOfThePlan() {
        val source = mutableListOf(move("Squat"), move("March"))
        val preview = WorkoutLibraryPreview.start(source)!!

        source.clear()

        assertEquals(2, preview.size)
        assertEquals("Squat", preview.currentMove.name)
        assertEquals("March", preview.next().currentMove.name)
    }

    @Test fun selectedPreviewMoveProvidesItsOwnAnimatedPhaseCaption() {
        val first = WorkoutLibraryPreview.start(
            listOf(move("Squat"), move("Chống đẩy tường"))
        )!!

        val squatPose = WorkoutPoseGuide.classify(first.currentMove.name)
        assertEquals("Đứng lên", WorkoutPoseMotion.phaseCue(squatPose, false))
        assertEquals("Hạ hông", WorkoutPoseMotion.phaseCue(squatPose, true))

        val wallPushPose = WorkoutPoseGuide.classify(first.next().currentMove.name)
        assertEquals("Đẩy ra khỏi tường", WorkoutPoseMotion.phaseCue(wallPushPose, false))
        assertEquals("Gập khuỷu về tường", WorkoutPoseMotion.phaseCue(wallPushPose, true))
    }

    @Test fun emptyAndOversizedPlansCannotOpenAPreview() {
        assertNull(WorkoutLibraryPreview.start(emptyList()))
        assertNull(
            WorkoutLibraryPreview.start(
                List(WorkoutLibraryPreview.MAX_MOVES + 1) { move("Move $it") }
            )
        )
    }
}
