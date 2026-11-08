package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPoseMotionTest {
    @Test fun dynamicExercisesHaveTwoPhases() {
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.SQUAT))
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.PUSH_UP))
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.LUNGE))
    }

    @Test fun movementCuesDescribeBothPhases() {
        assertEquals("Đứng lên", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SQUAT, false))
        assertEquals("Hạ hông", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SQUAT, true))
        assertEquals("Đẩy lên", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.PUSH_UP, false))
        assertEquals("Hạ ngực", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.PUSH_UP, true))
        assertEquals("Đứng lên", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.LUNGE, false))
        assertEquals("Hạ gối", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.LUNGE, true))
    }

    @Test fun staticCuesDoNotDependOnAnimationPhase() {
        for (pose in listOf(WorkoutPoseGuide.Pose.PLANK, WorkoutPoseGuide.Pose.REST, WorkoutPoseGuide.Pose.STANDING)) {
            assertEquals(WorkoutPoseMotion.phaseCue(pose, false), WorkoutPoseMotion.phaseCue(pose, true))
        }
    }

    @Test fun staticAndFallbackCuesNeverAnimate() {
        assertFalse(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.PLANK))
        assertFalse(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.REST))
        assertFalse(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.STANDING))
    }
}
