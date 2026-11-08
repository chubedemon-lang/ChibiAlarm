package com.chibialarm.app

/** Offline two-phase exercise cues. Illustrations are not a form assessment. */
object WorkoutPoseMotion {
    fun hasMotion(pose: WorkoutPoseGuide.Pose): Boolean = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT,
        WorkoutPoseGuide.Pose.PUSH_UP,
        WorkoutPoseGuide.Pose.LUNGE -> true
        WorkoutPoseGuide.Pose.PLANK,
        WorkoutPoseGuide.Pose.REST,
        WorkoutPoseGuide.Pose.STANDING -> false
    }

    /** Screen-reader description for the current illustration, without claiming form assessment. */
    fun accessibilityCue(pose: WorkoutPoseGuide.Pose, lowered: Boolean): String =
        "${WorkoutPoseGuide.label(pose)}. ${phaseCue(pose, lowered)}. Hình minh họa, không đánh giá tư thế."

    /** Short, explicit phase cues to accompany the two-frame drawing. */
    fun phaseCue(pose: WorkoutPoseGuide.Pose, lowered: Boolean): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT -> if (lowered) "Hạ hông" else "Đứng lên"
        WorkoutPoseGuide.Pose.PUSH_UP -> if (lowered) "Hạ ngực" else "Đẩy lên"
        WorkoutPoseGuide.Pose.LUNGE -> if (lowered) "Hạ gối" else "Đứng lên"
        WorkoutPoseGuide.Pose.PLANK -> "Giữ thân thẳng"
        WorkoutPoseGuide.Pose.REST -> "Nghỉ và thở đều"
        WorkoutPoseGuide.Pose.STANDING -> "Xem hướng dẫn"
    }
}
