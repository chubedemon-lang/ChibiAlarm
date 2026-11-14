package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPoseMotionTest {
    @Test fun dynamicExercisesHaveTwoPhases() {
        for (pose in listOf(
            WorkoutPoseGuide.Pose.SQUAT,
            WorkoutPoseGuide.Pose.HIP_HINGE,
            WorkoutPoseGuide.Pose.RESISTANCE_ROW,
            WorkoutPoseGuide.Pose.RESISTANCE_HINGE,
            WorkoutPoseGuide.Pose.RESISTANCE_PRESS,
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
            WorkoutPoseGuide.Pose.MOUNTAIN_CLIMBER,
            WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE,
            WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS,
            WorkoutPoseGuide.Pose.SIDE_STEP,
            WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE,
            WorkoutPoseGuide.Pose.CALF_RAISE
        )) assertTrue("$pose should animate", WorkoutPoseMotion.hasMotion(pose))
    }

    @Test fun movementCuesDescribeBothPhases() {
        assertEquals("Đứng lên", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SQUAT, false))
        assertEquals("Đứng thẳng", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.HIP_HINGE, false))
        assertEquals("Kéo khuỷu về sau", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.RESISTANCE_ROW, false))
        assertEquals("Hạ tay có kiểm soát", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.RESISTANCE_ROW, true))
        assertEquals("Đẩy tải lên", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.RESISTANCE_PRESS, false))
        assertEquals("Hạ tải ngang vai", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.RESISTANCE_PRESS, true))
        assertEquals("Đẩy hông ra sau", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.HIP_HINGE, true))
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
        assertEquals("Kéo nhẹ bả vai", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE, false))
        assertEquals("Ấn nhẹ cẳng tay", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS, false))
        assertEquals("Bước ngang", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SIDE_STEP, false))
        assertEquals("Nâng gối", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE, false))
        assertEquals("Đưa gối về phía ngực", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.MOUNTAIN_CLIMBER, false))
        assertEquals("Đổi chân, duỗi chân còn lại ra sau", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.MOUNTAIN_CLIMBER, true))
        assertEquals("Nhón gót", WorkoutPoseMotion.phaseCue(WorkoutPoseGuide.Pose.CALF_RAISE, false))
    }

    @Test fun spokenStepCueIncludesExerciseContextAndTheSelectedStep() {
        val cue = WorkoutPoseMotion.spokenStepCue(
            WorkoutPoseGuide.Pose.SQUAT,
            "Đặt chân rộng bằng vai, giữ bàn chân vững.",
            2,
            3,
            true
        )
        assertTrue(cue.contains("Hạ hông"))
        assertTrue(cue.contains("Bước 2 trên 3"))
        assertTrue(cue.contains("Đặt chân rộng bằng vai"))
        assertTrue(cue.length <= 340)
    }

    @Test fun spokenStepCueIncludesPhaseMatchedBreathingGuidance() {
        val lowering = WorkoutPoseMotion.spokenStepCue(
            WorkoutPoseGuide.Pose.SQUAT, "Giữ bàn chân vững.", 1, 2, true
        )
        val effort = WorkoutPoseMotion.spokenStepCue(
            WorkoutPoseGuide.Pose.SQUAT, "Đứng lên có kiểm soát.", 2, 2, false
        )
        val staticHold = WorkoutPoseMotion.spokenStepCue(
            WorkoutPoseGuide.Pose.PLANK, "Giữ thân ổn định.", 1, 1, false
        )
        assertTrue(lowering.contains("Hít vào khi hạ hoặc trở về"))
        assertTrue(effort.contains("Thở ra khi dùng lực"))
        assertTrue(staticHold.contains("Thở đều, không nín thở"))
    }

    @Test fun spokenStepCueTracksTheCurrentIllustrationPhase() {
        val lowered = WorkoutPoseMotion.spokenStepCue(
            WorkoutPoseGuide.Pose.SQUAT, "Giữ bàn chân vững.", 1, 2, true
        )
        val upright = WorkoutPoseMotion.spokenStepCue(
            WorkoutPoseGuide.Pose.SQUAT, "Giữ bàn chân vững.", 1, 2, false
        )
        assertTrue(lowered.contains("Hạ hông"))
        assertTrue(upright.contains("Đứng lên"))
        assertFalse(lowered == upright)
    }

    @Test fun walkthroughIllustrationPhaseFollowsTheSelectedInstruction() {
        assertEquals(true, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.SQUAT, "Hạ xuống theo khả năng"
        ))
        assertEquals(false, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.SQUAT, "Đứng lên có kiểm soát"
        ))
        assertEquals(true, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.WALL_PUSH_UP, "Gập khuỷu đưa ngực gần tường"
        ))
        assertEquals(false, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.WALL_PUSH_UP, "Đẩy ra khỏi tường"
        ))
        assertEquals(true, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.BRIDGE, "Hạ hông chậm"
        ))
        assertEquals(false, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.BRIDGE, "Siết mông nâng hông"
        ))
        assertEquals(null, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.SQUAT, "Chân rộng bằng vai"
        ))
        assertEquals(null, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.SQUAT, "Hạ xuống rồi đứng lên"
        ))
        assertEquals(null, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.LUNGE, "Bước lùi, hạ thân rồi trở về"
        ))
        assertEquals(false, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.MOUNTAIN_CLIMBER, "Đưa từng gối về phía ngực"
        ))
        assertEquals(true, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.MOUNTAIN_CLIMBER, "Duỗi chân ra sau có kiểm soát"
        ))
        assertEquals(null, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.MOUNTAIN_CLIMBER, "Giữ hông ổn định"
        ))
        assertEquals(null, WorkoutPoseMotion.guidePhaseForStep(
            WorkoutPoseGuide.Pose.PLANK, "Giữ thân ổn định"
        ))
    }

    @Test fun neutralSetupStepDoesNotInventARepPhaseOrBreathingPattern() {
        val cue = WorkoutPoseMotion.spokenInstructionStepCue(
            WorkoutPoseGuide.Pose.SQUAT, "Chân rộng bằng vai.", 1, 5
        )
        assertTrue(cue.contains("Tư thế minh họa cho bước này"))
        assertTrue(cue.contains("Minh họa: hạ hông và gập gối"))
        assertTrue(cue.contains("Thở đều, không nín thở"))
        assertTrue(cue.contains("Bước 1 trên 5"))
        assertFalse(cue.contains("Đứng lên."))
        assertTrue(cue.length <= 340)
    }

    @Test fun selectedMovementStepGetsMatchingSpokenPhase() {
        val lowering = WorkoutPoseMotion.spokenInstructionStepCue(
            WorkoutPoseGuide.Pose.SQUAT, "Hạ xuống theo khả năng.", 3, 5
        )
        val standing = WorkoutPoseMotion.spokenInstructionStepCue(
            WorkoutPoseGuide.Pose.SQUAT, "Đứng lên.", 4, 5
        )
        assertTrue(lowering.contains("Hạ hông"))
        assertTrue(lowering.contains("Hít vào khi hạ hoặc trở về"))
        assertTrue(standing.contains("Đứng lên"))
        assertTrue(standing.contains("Thở ra khi dùng lực"))
    }

    @Test fun spokenStepCueBoundsInvalidProgressAndLongInstructions() {
        val cue = WorkoutPoseMotion.spokenStepCue(
            WorkoutPoseGuide.Pose.STANDING,
            "Hướng dẫn ".repeat(80),
            0,
            0
        )
        assertTrue(cue.contains("Bước 1 trên 1"))
        assertTrue(cue.length <= 340)
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
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.RESISTANCE_ROW).contains("lưng trung lập"))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.RESISTANCE_HINGE).contains("tải nhẹ"))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(WorkoutPoseGuide.Pose.RESISTANCE_PRESS).contains("không nín thở"))
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
