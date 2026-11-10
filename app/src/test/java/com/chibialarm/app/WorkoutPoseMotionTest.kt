package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPoseMotionTest {
    @Test fun dynamicExercisesHaveTwoPhases() {
        for (pose in listOf(
            WorkoutPoseGuide.Pose.SQUAT,
            WorkoutPoseGuide.Pose.PUSH_UP,
            WorkoutPoseGuide.Pose.KNEE_PUSH_UP,
            WorkoutPoseGuide.Pose.WALL_PUSH_UP,
            WorkoutPoseGuide.Pose.INCLINE_PUSH_UP,
            WorkoutPoseGuide.Pose.LUNGE,
            WorkoutPoseGuide.Pose.BRIDGE,
            WorkoutPoseGuide.Pose.BIRD_DOG,
            WorkoutPoseGuide.Pose.DEAD_BUG,
            WorkoutPoseGuide.Pose.CAT_COW,
            WorkoutPoseGuide.Pose.MARCH,
            WorkoutPoseGuide.Pose.CALF_RAISE
        )) assertTrue("$pose should animate", WorkoutPoseMotion.hasMotion(pose))
    }

    @Test fun movementCuesDescribeBothPhases() {
        assertEquals("Đứng lên", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SQUAT, false))
        assertEquals("Hạ hông", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SQUAT, true))
        assertEquals("Đẩy lên", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.PUSH_UP, false))
        assertEquals("Hạ ngực", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.PUSH_UP, true))
        assertEquals("Đẩy lên từ điểm tựa gối", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.KNEE_PUSH_UP, false))
        assertEquals("Gập khuỷu về tường", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.WALL_PUSH_UP, true))
        assertEquals("Đẩy ra khỏi mặt tựa", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.INCLINE_PUSH_UP, false))
        assertEquals("Nâng hông", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.BRIDGE, false))
        assertEquals("Hạ hông", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.BRIDGE, true))
        assertEquals("Duỗi tay chân đối diện", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.BIRD_DOG, false))
        assertEquals("Thu tay chân", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.BIRD_DOG, true))
        assertEquals("Cuộn lưng nhẹ", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.CAT_COW, true))
        assertEquals("Nhón gót", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.CALF_RAISE, false))
    }

    @Test fun staticCuesDoNotDependOnAnimationPhase() {
        for (pose in listOf(
            WorkoutPoseGuide.Pose.PLANK,
            WorkoutPoseGuide.Pose.STRETCH,
            WorkoutPoseGuide.Pose.SIDE_PLANK,
            WorkoutPoseGuide.Pose.BALANCE,
            WorkoutPoseGuide.Pose.REST,
            WorkoutPoseGuide.Pose.STANDING
        )) {
            assertEquals(WorkoutPoseMotion.phaseCue(pose, false), WorkoutPoseMotion.phaseCue(pose, true))
        }
    }

    @Test fun screenReaderDescriptionTracksMovementWithoutClaimingFormAssessment() {
        val upright = WorkoutPoseMotion.accessibilityCue(WorkoutPoseGuide.Pose.SQUAT, false)
        val lowered = WorkoutPoseMotion.accessibilityCue(WorkoutPoseGuide.Pose.SQUAT, true)
        assertTrue(upright.contains("Đứng lên"))
        assertTrue(lowered.contains("Hạ hông"))
        assertTrue(upright.contains("không đánh giá tư thế"))
        assertTrue(lowered.contains("không đánh giá tư thế"))
        assertFalse(upright == lowered)
    }

    @Test fun spokenWalkthroughIsAvailableOfflineAndNeverClaimsFormAnalysis() {
        for (pose in WorkoutPoseGuide.Pose.values()) {
            val cue = WorkoutPoseMotion.spokenWalkthrough(pose)
            assertTrue(cue.isNotBlank())
            assertTrue(cue.length < 350)
            assertFalse(cue.contains("đã đánh giá"))
        }
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.SQUAT).contains("Hạ hông"))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.STANDING).contains("Chưa có minh họa riêng"))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.WALL_PUSH_UP).contains("tường"))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.INCLINE_PUSH_UP).contains("chắc chắn"))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.KNEE_PUSH_UP).contains("hai gối"))
    }

    @Test fun staticAndFallbackCuesNeverAnimate() {
        for (pose in listOf(
            WorkoutPoseGuide.Pose.PLANK,
            WorkoutPoseGuide.Pose.STRETCH,
            WorkoutPoseGuide.Pose.SIDE_PLANK,
            WorkoutPoseGuide.Pose.BALANCE,
            WorkoutPoseGuide.Pose.REST,
            WorkoutPoseGuide.Pose.STANDING
        )) assertFalse("$pose should remain static", WorkoutPoseMotion.hasMotion(pose))
    }
}
