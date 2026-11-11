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
        assertEquals(WorkoutPoseGuide.Pose.HIP_HINGE, WorkoutPoseGuide.classify("Gập hông không dụng cụ"))
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.HIP_HINGE))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.HIP_HINGE).contains("lưng trung lập"))
        assertTrue(WorkoutPoseGuide.label(WorkoutPoseGuide.Pose.HIP_HINGE).contains("đẩy hông"))
        assertEquals(WorkoutPoseGuide.Pose.BIRD_DOG, WorkoutPoseGuide.classify("Bird-dog"))
        assertEquals(WorkoutPoseGuide.Pose.DEAD_BUG, WorkoutPoseGuide.classify("Dead bug cơ bụng"))
        assertEquals(WorkoutPoseGuide.Pose.CAT_COW, WorkoutPoseGuide.classify("Cat-cow nhẹ"))
        assertEquals(WorkoutPoseGuide.Pose.MARCH, WorkoutPoseGuide.classify("Đi bộ nhanh tại chỗ"))
        assertEquals(WorkoutPoseGuide.Pose.SUPPORTED_MARCH,
            WorkoutPoseGuide.classify("Nâng gối luân phiên có điểm tựa"))
        assertEquals(WorkoutPoseGuide.Pose.HIP_ABDUCTION,
            WorkoutPoseGuide.classify("Dang chân sang bên có điểm tựa"))
        assertEquals(WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH,
            WorkoutPoseGuide.classify("Giãn gân kheo ngồi ghế"))
        assertEquals(WorkoutPoseGuide.Pose.STRETCH, WorkoutPoseGuide.classify("Mở vai và ngực"))
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.SUPPORTED_MARCH))
        assertTrue(WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.HIP_ABDUCTION))
        assertTrue(!WorkoutPoseMotion.hasMotion(WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.HIP_ABDUCTION).contains("Đưa một chân sang bên"))
        assertTrue(WorkoutPoseGuide.label(WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH).contains("ngồi ghế"))
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

    @Test fun commonSubstitutionsHaveDedicatedAnimatedIllustrations() {
        val cases = listOf(
            "Siết bả vai đứng" to WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE,
            "Ép ngực tường bằng cẳng tay" to WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS,
            "Bước ngang nhẹ" to WorkoutPoseGuide.Pose.SIDE_STEP,
            "Nâng gối ngồi ghế" to WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE
        )
        for ((name, expected) in cases) {
            assertEquals(name, expected, WorkoutPoseGuide.classify(name))
            assertTrue("$name should have a distinct animation", WorkoutPoseMotion.hasMotion(expected))
            assertTrue(WorkoutPoseGuide.label(expected).isNotBlank())
            assertTrue(WorkoutPoseMotion.spokenWalkthrough(expected).isNotBlank())
        }
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
