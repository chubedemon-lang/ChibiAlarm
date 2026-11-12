package com.chibialarm.app

/** Offline two-phase exercise cues. Illustrations are not a form assessment. */
object WorkoutPoseMotion {
    fun hasMotion(pose: WorkoutPoseGuide.Pose): Boolean = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT,
        WorkoutPoseGuide.Pose.HIP_HINGE,
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
        WorkoutPoseGuide.Pose.SUPPORTED_MARCH,
        WorkoutPoseGuide.Pose.HIP_ABDUCTION,
        WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE,
        WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS,
        WorkoutPoseGuide.Pose.SIDE_STEP,
        WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE,
        WorkoutPoseGuide.Pose.CALF_RAISE -> true
        WorkoutPoseGuide.Pose.PLANK,
        WorkoutPoseGuide.Pose.STRETCH,
        WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH,
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
        WorkoutPoseGuide.Pose.HIP_HINGE -> "Đứng chân rộng bằng hông, gối hơi chùng. Đẩy hông ra sau, giữ lưng trung lập và nghiêng thân nhẹ; siết mông để đứng thẳng. Không cần tạ."
        WorkoutPoseGuide.Pose.PUSH_UP -> "Minh họa chống đẩy sàn. Hạ ngực có kiểm soát, sau đó đẩy lên. Có thể chọn biến thể dễ hơn nếu cần."
        WorkoutPoseGuide.Pose.KNEE_PUSH_UP -> "Minh họa chống đẩy gối. Đặt hai gối trên sàn, giữ thân từ vai tới gối ổn định, hạ ngực vừa tầm rồi đẩy lên."
        WorkoutPoseGuide.Pose.WALL_PUSH_UP -> "Minh họa chống đẩy tường. Đứng cách tường một khoảng thoải mái, đặt tay ngang ngực, gập khuỷu đưa thân gần tường rồi đẩy ra."
        WorkoutPoseGuide.Pose.INCLINE_PUSH_UP -> "Minh họa chống đẩy trên bề mặt cao. Chỉ dùng mặt tựa chắc chắn, không trượt hoặc lật; giữ thân thẳng, hạ ngực rồi đẩy ra. Dừng nếu mặt tựa không vững."
        WorkoutPoseGuide.Pose.LUNGE -> "Minh họa chùng chân. Bước chân trước, hạ gối từ từ rồi đứng lên. Có thể vịn vào điểm tựa chắc chắn."
        WorkoutPoseGuide.Pose.PLANK -> "Minh họa plank. Giữ thân thẳng, thở đều và dừng nếu đau hoặc khó chịu."
        WorkoutPoseGuide.Pose.BRIDGE -> "Minh họa nâng hông. Nằm ngửa, co gối, siết mông nâng hông vừa tầm rồi hạ chậm. Không ưỡn lưng quá mức."
        WorkoutPoseGuide.Pose.BIRD_DOG -> "Minh họa bird-dog. Chống tay và gối, duỗi tay cùng chân đối diện trong tầm kiểm soát rồi đổi bên."
        WorkoutPoseGuide.Pose.DEAD_BUG -> "Minh họa dead bug. Nằm ngửa, giữ thân ổn định, hạ tay và chân đối diện chậm rồi thu về."
        WorkoutPoseGuide.Pose.CAT_COW -> "Minh họa cat-cow. Chống tay và gối, chuyển lưng nhẹ nhàng theo nhịp thở, không ép biên độ."
        WorkoutPoseGuide.Pose.MARCH -> "Minh họa bước tại chỗ. Nâng từng gối luân phiên, bước nhẹ và giữ nhịp thở đều."
        WorkoutPoseGuide.Pose.SUPPORTED_MARCH -> "Đứng cạnh ghế vững và vịn nhẹ. Nâng từng gối thấp vừa sức, đổi bên và giữ thân thẳng."
        WorkoutPoseGuide.Pose.HIP_ABDUCTION -> "Đứng cạnh ghế vững, giữ nhẹ để thăng bằng. Đưa một chân sang bên trong biên độ nhỏ, hạ chậm rồi đổi bên."
        WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE -> "Ngồi hoặc đứng thẳng, thả tay. Kéo nhẹ hai bả vai về sau và xuống, giữ ngắn rồi thả lỏng; không nhún vai hoặc ưỡn lưng."
        WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS -> "Đứng đối diện tường, đặt cẳng tay lên tường với khuỷu gập thoải mái. Ấn nhẹ cẳng tay vào tường, giữ cổ tay thả lỏng và thở đều rồi thả. Dừng nếu đau."
        WorkoutPoseGuide.Pose.SIDE_STEP -> "Đứng vững, bước ngang ngắn sang một bên rồi đưa chân kia về gần; đổi hướng. Giữ gối mềm, sàn khô và vịn điểm tựa nếu cần."
        WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE -> "Ngồi trên ghế chắc chắn, lưng thẳng. Nâng một gối thấp vừa sức rồi hạ chậm, đổi bên. Dừng nếu đau hoặc chóng mặt."
        WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH -> "Ngồi ghế vững, duỗi một chân với gót chạm sàn. Nghiêng nhẹ từ hông đến khi căng nhẹ phía sau đùi; đổi bên, không kéo đến đau."
        WorkoutPoseGuide.Pose.STRETCH -> "Minh họa giãn cơ. Thực hiện chậm, không giật nảy và không ép tới mức đau."
        WorkoutPoseGuide.Pose.SIDE_PLANK -> "Minh họa plank nghiêng. Chống khuỷu chắc chắn, nâng hông trong mức thoải mái; có thể co gối để dễ hơn."
        WorkoutPoseGuide.Pose.CALF_RAISE -> "Minh họa nhón gót. Vịn điểm tựa chắc chắn nếu cần, nâng gót chậm rồi hạ có kiểm soát."
        WorkoutPoseGuide.Pose.BALANCE -> "Minh họa thăng bằng. Đứng cạnh tường hoặc ghế chắc chắn và giữ điểm tựa trong tầm tay."
        WorkoutPoseGuide.Pose.REST -> "Đang nghỉ. Hít thở bình thường, nghỉ thêm nếu cần."
        WorkoutPoseGuide.Pose.STANDING -> "Chưa có minh họa riêng cho động tác này. Hãy đọc hướng dẫn từng bước bên dưới trước khi tập."
    }

    /** Spoken context for the selected walkthrough step; bounded for short on-device TTS. */
    fun spokenStepCue(
        pose: WorkoutPoseGuide.Pose,
        step: String,
        position: Int,
        total: Int
    ): String {
        val safeTotal = total.coerceIn(1, 100)
        val safePosition = position.coerceIn(1, safeTotal)
        val safeStep = step.trim().replace(Regex("\\s+"), " ")
            .take(180)
            .ifBlank { "Thực hiện chậm trong biên độ thoải mái." }
        return "${WorkoutPoseGuide.label(pose)}. ${phaseCue(pose, false)}. " +
            "Bước $safePosition trên $safeTotal: $safeStep"
    }

    /** Short, explicit phase cues to accompany the two-frame drawing. */
    fun phaseCue(pose: WorkoutPoseGuide.Pose, lowered: Boolean): String = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT -> if (lowered) "Hạ hông" else "Đứng lên"
        WorkoutPoseGuide.Pose.HIP_HINGE -> if (lowered) "Đẩy hông ra sau" else "Đứng thẳng"
        WorkoutPoseGuide.Pose.PUSH_UP -> if (lowered) "Hạ ngực" else "Đẩy lên"
        WorkoutPoseGuide.Pose.KNEE_PUSH_UP -> if (lowered) "Hạ ngực" else "Đẩy lên từ điểm tựa gối"
        WorkoutPoseGuide.Pose.WALL_PUSH_UP -> if (lowered) "Gập khuỷu về tường" else "Đẩy ra khỏi tường"
        WorkoutPoseGuide.Pose.INCLINE_PUSH_UP -> if (lowered) "Hạ ngực về mặt tựa" else "Đẩy ra khỏi mặt tựa"
        WorkoutPoseGuide.Pose.LUNGE -> if (lowered) "Hạ gối" else "Đứng lên"
        WorkoutPoseGuide.Pose.BRIDGE -> if (lowered) "Hạ hông" else "Nâng hông"
        WorkoutPoseGuide.Pose.BIRD_DOG -> if (lowered) "Thu tay chân" else "Duỗi tay chân đối diện"
        WorkoutPoseGuide.Pose.DEAD_BUG -> if (lowered) "Co về" else "Duỗi tay chân"
        WorkoutPoseGuide.Pose.CAT_COW -> if (lowered) "Cuộn lưng nhẹ" else "Thả lưng nhẹ"
        WorkoutPoseGuide.Pose.MARCH -> if (lowered) "Đổi chân" else "Nâng gối"
        WorkoutPoseGuide.Pose.SUPPORTED_MARCH -> if (lowered) "Đổi chân có điểm tựa" else "Nâng gối thấp"
        WorkoutPoseGuide.Pose.HIP_ABDUCTION -> if (lowered) "Hạ chân" else "Đưa chân sang bên"
        WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE -> if (lowered) "Thả lỏng bả vai" else "Kéo nhẹ bả vai"
        WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS -> if (lowered) "Thả cẳng tay" else "Ấn nhẹ cẳng tay"
        WorkoutPoseGuide.Pose.SIDE_STEP -> if (lowered) "Đưa chân về" else "Bước ngang"
        WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE -> if (lowered) "Hạ chân" else "Nâng gối"
        WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH -> "Giãn nhẹ phía sau đùi"
        WorkoutPoseGuide.Pose.CALF_RAISE -> if (lowered) "Hạ gót" else "Nhón gót"
        WorkoutPoseGuide.Pose.PLANK -> "Giữ thân thẳng"
        WorkoutPoseGuide.Pose.STRETCH -> "Giãn nhẹ"
        WorkoutPoseGuide.Pose.SIDE_PLANK -> "Giữ hông nâng"
        WorkoutPoseGuide.Pose.BALANCE -> "Giữ thăng bằng"
        WorkoutPoseGuide.Pose.REST -> "Nghỉ và thở đều"
        WorkoutPoseGuide.Pose.STANDING -> "Xem hướng dẫn"
    }
}
