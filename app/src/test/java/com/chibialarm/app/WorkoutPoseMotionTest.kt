package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPoseMotionTest {
    @Test fun dynamicExercisesHaveTwoPhases() {
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.SQUAT))
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.PUSH_UP))
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.LUNGE))
    }

    @Test fun staticAndFallbackCuesNeverAnimate() {
        assertFalse(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.PLANK))
        assertFalse(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.REST))
        assertFalse(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.STANDING))
    }
}
