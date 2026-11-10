package com.chibialarm.app

/** Offline two-phase exercise cues. Illustrations are not a form assessment. */
object WorkoutPoseMotion {
    fun hasMotion(pose: WorkoutPoseGuide.Pose): Boolean = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT,
        WorkoutPoseGuide.Pose.PUSH_UP,
        WorkoutPoseGuide.Pose.LUNGE,
        WorkoutPoseGuide.Pose.BRIDGE,
        WorkoutPoseGuide.Pose.BIRD_DOG,
        WorkoutPoseGuide.Pose.DEAD_BUG,
        WorkoutPoseGuide.Pose.CAT_COW,
        WorkoutPoseGuide.Pose.MARCH,
        WorkoutPoseGuide.Pose.CALF_RAISE -> true
        WorkoutPoseGuide.Pose.PLANK,
        WorkoutPoseGuide.Pose.STRETCH,
        WorkoutPoseGuide.Pose.SIDE_PLANK,
        WorkoutPoseGuide.Pose.BALANCE,
        WorkoutPoseGuide.Pose.REST,
        WorkoutPoseGuide.Pose.STANDING -> false
    }

    /** Screen-reader description for the current illustration, without claiming form assessment. */
    fun accessibilityCue(pose: WorkoutPoseGuide.Pose, lowered: Boolean): String =
        "${WorkoutPoseGuide.label(pose)}. ${phaseCue(pose, lowered)}. Hình minh họa, không đánh giá tư thế."

    /** One user-requested spoken walkthrough, never automatically repeated with animation. */
    fun spokenWalkthrough(pose: WorkoutPoseGuide.Pose): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT -> "Minh họa squat. Hạ hông từ từ, sau đó đứng lên. Chỉ xuống sâu trong mức thoải mái."
        WorkoutPoseGuide.Pose.PUSH_UP -> "Minh họa chống đẩy. Hạ ngực có kiểm soát, sau đó đẩy lên. Có thể chống gối để giảm độ khó."
        WorkoutPoseGuide.Pose.LUNGE -> "Minh họa chùng chân. Bước chân trước, hạ gối từ từ rồi đứng lên. Có thể vịn vào điểm tựa chắc chắn."
        WorkoutPoseGuide.Pose.PLANK -> "Minh họa plank. Giữ thân thẳng, thở đều và dừng nếu đau hoặc khó chịu."
        WorkoutPoseGuide.Pose.BRIDGE -> "Minh họa nâng hông. Nằm ngửa, co gối, siết mông nâng hông vừa tầm rồi hạ chậm. Không ưỡn lưng quá mức."
        WorkoutPoseGuide.Pose.BIRD_DOG -> "Minh họa bird-dog. Chống tay và gối, duỗi tay cùng chân đối diện trong tầm kiểm soát rồi đổi bên."
        WorkoutPoseGuide.Pose.DEAD_BUG -> "Minh họa dead bug. Nằm ngửa, giữ thân ổn định, hạ tay và chân đối diện chậm rồi thu về."
        WorkoutPoseGuide.Pose.CAT_COW -> "Minh họa cat-cow. Chống tay và gối, chuyển lưng nhẹ nhàng theo nhịp thở, không ép biên độ."
        WorkoutPoseGuide.Pose.MARCH -> "Minh họa bước tại chỗ. Nâng từng gối luân phiên, bước nhẹ và giữ nhịp thở đều."
        WorkoutPoseGuide.Pose.STRETCH -> "Minh họa giãn cơ. Thực hiện chậm, không giật nảy và không ép tới mức đau."
        WorkoutPoseGuide.Pose.SIDE_PLANK -> "Minh họa plank nghiêng. Chống khuỷu chắc chắn, nâng hông trong mức thoải mái; có thể co gối để dễ hơn."
        WorkoutPoseGuide.Pose.CALF_RAISE -> "Minh họa nhón gót. Vịn điểm tựa chắc chắn nếu cần, nâng gót chậm rồi hạ có kiểm soát."
        WorkoutPoseGuide.Pose.BALANCE -> "Minh họa thăng bằng. Đứng cạnh tường hoặc ghế chắc chắn và giữ điểm tựa trong tầm tay."
        WorkoutPoseGuide.Pose.REST -> "Đang nghỉ. Hít thở bình thường, nghỉ thêm nếu cần."
        WorkoutPoseGuide.Pose.STANDING -> "Chưa có minh họa riêng cho động tác này. Hãy đọc hướng dẫn từng bước bên dưới trước khi tập."
    }

    /** Short, explicit phase cues to accompany the two-frame drawing. */
    fun phaseCue(pose: WorkoutPoseGuide.Pose, lowered: Boolean): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT -> if (lowered) "Hạ hông" else "Đứng lên"
        WorkoutPoseGuide.Pose.PUSH_UP -> if (lowered) "Hạ ngực" else "Đẩy lên"
        WorkoutPoseGuide.Pose.LUNGE -> if (lowered) "Hạ gối" else "Đứng lên"
        WorkoutPoseGuide.Pose.BRIDGE -> if (lowered) "Hạ hông" else "Nâng hông"
        WorkoutPoseGuide.Pose.BIRD_DOG -> if (lowered) "Thu tay chân" else "Duỗi tay chân đối diện"
        WorkoutPoseGuide.Pose.DEAD_BUG -> if (lowered) "Co về" else "Duỗi tay chân"
        WorkoutPoseGuide.Pose.CAT_COW -> if (lowered) "Cuộn lưng nhẹ" else "Thả lưng nhẹ"
        WorkoutPoseGuide.Pose.MARCH -> if (lowered) "Đổi chân" else "Nâng gối"
        WorkoutPoseGuide.Pose.CALF_RAISE -> if (lowered) "Hạ gót" else "Nhón gót"
        WorkoutPoseGuide.Pose.PLANK -> "Giữ thân thẳng"
        WorkoutPoseGuide.Pose.STRETCH -> "Giãn nhẹ"
        WorkoutPoseGuide.Pose.SIDE_PLANK -> "Giữ hông nâng"
        WorkoutPoseGuide.Pose.BALANCE -> "Giữ thăng bằng"
        WorkoutPoseGuide.Pose.REST -> "Nghỉ và thở đều"
        WorkoutPoseGuide.Pose.STANDING -> "Xem hướng dẫn"
    }
}
