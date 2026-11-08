package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPoseGuideTest {
    @Test fun recognizesCommonExerciseNames() {
        assertEquals(WorkoutPoseGuide.Pose.SQUAT, WorkoutPoseGuide.classify("Squat cơ bản"))
        assertEquals(WorkoutPoseGuide.Pose.PLANK, WorkoutPoseGuide.classify("Plank 30 giây"))
        assertEquals(WorkoutPoseGuide.Pose.PUSH_UP, WorkoutPoseGuide.classify("Chống đẩy"))
        assertEquals(WorkoutPoseGuide.Pose.LUNGE, WorkoutPoseGuide.classify("Chùng chân"))
        assertEquals(WorkoutPoseGuide.Pose.REST, WorkoutPoseGuide.classify("Nghỉ và thở đều"))
    }

    @Test fun unknownExercisesUseHonestGenericCue() {
        val pose = WorkoutPoseGuide.classify("Động tác chưa có hình riêng")
        assertEquals(WorkoutPoseGuide.Pose.STANDING, pose)
        assertTrue(WorkoutPoseGuide.label(pose).contains("chung"))
    }
}
