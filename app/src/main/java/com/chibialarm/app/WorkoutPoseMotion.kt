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

    /** One user-requested spoken walkthrough, never automatically repeated with animation. */
    fun spokenWalkthrough(pose: WorkoutPoseGuide.Pose): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT -> "Minh họa squat. Hạ hông từ từ, sau đó đứng lên. Giữ nhịp thở đều và chỉ xuống sâu trong mức thoải mái."
        WorkoutPoseGuide.Pose.PUSH_UP -> "Minh họa chống đẩy. Hạ ngực có kiểm soát, sau đó đẩy lên. Có thể chống gối để giảm độ khó."
        WorkoutPoseGuide.Pose.LUNGE -> "Minh họa chùng chân. Bước chân trước, hạ gối từ từ rồi đứng lên. Có thể vịn vào điểm tựa chắc chắn."
        WorkoutPoseGuide.Pose.PLANK -> "Minh họa plank. Giữ thân thẳng, thở đều và dừng nếu đau hoặc khó chịu."
        WorkoutPoseGuide.Pose.REST -> "Đang nghỉ. Hít thở bình thường, nghỉ thêm nếu cần."
        WorkoutPoseGuide.Pose.STANDING -> "Chưa có minh họa riêng cho động tác này. Hãy đọc hướng dẫn từng bước bên dưới trước khi tập."
    }

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
