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

    @Test fun mapsMoreCatalogExercisesToSpecificIllustrations() {
        assertEquals(WorkoutPoseGuide.Pose.BRIDGE, WorkoutPoseGuide.classify("Nâng hông trên sàn"))
        assertEquals(WorkoutPoseGuide.Pose.BIRD_DOG, WorkoutPoseGuide.classify("Bird-dog"))
        assertEquals(WorkoutPoseGuide.Pose.DEAD_BUG, WorkoutPoseGuide.classify("Dead bug cơ bụng"))
        assertEquals(WorkoutPoseGuide.Pose.CAT_COW, WorkoutPoseGuide.classify("Cat-cow nhẹ"))
        assertEquals(WorkoutPoseGuide.Pose.MARCH, WorkoutPoseGuide.classify("Đi bộ nhanh tại chỗ"))
        assertEquals(WorkoutPoseGuide.Pose.STRETCH, WorkoutPoseGuide.classify("Mở vai và ngực"))
        assertEquals(WorkoutPoseGuide.Pose.SIDE_PLANK, WorkoutPoseGuide.classify("Side plank gối"))
        assertEquals(WorkoutPoseGuide.Pose.CALF_RAISE, WorkoutPoseGuide.classify("Nhón gót"))
        assertEquals(WorkoutPoseGuide.Pose.BALANCE, WorkoutPoseGuide.classify("Đứng thăng bằng có hỗ trợ"))
    }

    @Test fun pushUpVariantsUseTheirOwnIllustrations() {
        assertEquals(WorkoutPoseGuide.Pose.PUSH_UP, WorkoutPoseGuide.classify("Chống đẩy sàn"))
        assertEquals(WorkoutPoseGuide.Pose.KNEE_PUSH_UP, WorkoutPoseGuide.classify("Chống đẩy gối"))
        assertEquals(WorkoutPoseGuide.Pose.WALL_PUSH_UP, WorkoutPoseGuide.classify("Chống đẩy tường"))
        assertEquals(WorkoutPoseGuide.Pose.INCLINE_PUSH_UP,
            WorkoutPoseGuide.classify("Chống đẩy trên bề mặt cao"))
    }

    @Test fun specificSidePlankIsNotCapturedByGenericPlankRule() {
        assertEquals(WorkoutPoseGuide.Pose.SIDE_PLANK, WorkoutPoseGuide.classify("Side plank"))
    }

    @Test fun unknownExercisesUseHonestGenericCue() {
        val pose = WorkoutPoseGuide.classify("Động tác chưa có hình riêng")
        assertEquals(WorkoutPoseGuide.Pose.STANDING, pose)
        assertTrue(WorkoutPoseGuide.label(pose).contains("chung"))
    }
}
