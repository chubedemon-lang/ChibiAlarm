package com.chibialarm.app

/**
 * Original offline posture illustrations. They are movement cues, NOT a
 * form assessment or a substitute for the exercise's written instructions.
 */
object WorkoutPoseGuide {
    enum class Pose {
        SQUAT, HIP_HINGE, RESISTANCE_ROW, RESISTANCE_HINGE, RESISTANCE_PRESS, PLANK, PUSH_UP, KNEE_PUSH_UP, WALL_PUSH_UP, INCLINE_PUSH_UP, LUNGE,
        BRIDGE, BIRD_DOG, DEAD_BUG, CAT_COW,
        MARCH, MOUNTAIN_CLIMBER, SUPPORTED_MARCH, HIP_ABDUCTION, SEATED_HAMSTRING_STRETCH,
        SCAPULAR_SQUEEZE, FOREARM_WALL_PRESS, SIDE_STEP, SEATED_KNEE_RAISE,
        STRETCH, SIDE_PLANK, CALF_RAISE, BALANCE, STANDING, REST
    }

    fun classify(name: String): Pose {
        val text = name.lowercase(java.util.Locale.ROOT)
        return when {
            // More specific names must be checked before their generic parent.
            listOf("side plank", "plank nghiêng").any { it in text } -> Pose.SIDE_PLANK
            listOf("bird-dog", "bird dog").any { it in text } -> Pose.BIRD_DOG
            listOf("dead bug").any { it in text } -> Pose.DEAD_BUG
            listOf("cat-cow", "cat cow", "mèo-bò").any { it in text } -> Pose.CAT_COW
            listOf("nâng hông", "glute bridge", "bridge").any { it in text } -> Pose.BRIDGE
            listOf("kéo tạ đơn hoặc dây kháng lực", "bent-over row", "dumbbell row", "resistance band row").any { it in text } -> Pose.RESISTANCE_ROW
            listOf("gập hông với tạ đơn hoặc dây", "romanian deadlift", "resistance hinge").any { it in text } -> Pose.RESISTANCE_HINGE
            listOf("đẩy vai với tạ đơn hoặc dây", "overhead press", "shoulder press").any { it in text } -> Pose.RESISTANCE_PRESS
            listOf("gập hông không dụng cụ", "hip hinge").any { it in text } -> Pose.HIP_HINGE
            listOf("nâng gối luân phiên có điểm tựa", "supported march").any { it in text } -> Pose.SUPPORTED_MARCH
            listOf("dang chân sang bên có điểm tựa", "hip abduction").any { it in text } -> Pose.HIP_ABDUCTION
            listOf("giãn gân kheo ngồi ghế", "seated hamstring").any { it in text } -> Pose.SEATED_HAMSTRING_STRETCH
            listOf("siết bả vai đứng", "scapular squeeze").any { it in text } -> Pose.SCAPULAR_SQUEEZE
            listOf("ép ngực tường bằng cẳng tay", "forearm wall press").any { it in text } -> Pose.FOREARM_WALL_PRESS
            listOf("bước ngang nhẹ", "side step", "lateral step").any { it in text } -> Pose.SIDE_STEP
            listOf("nâng gối ngồi ghế", "seated knee raise").any { it in text } -> Pose.SEATED_KNEE_RAISE
            listOf("mountain climber", "leo núi").any { it in text } -> Pose.MOUNTAIN_CLIMBER
            listOf("đi bộ nhanh tại chỗ", "bước tại chỗ", "march").any { it in text } -> Pose.MARCH
            listOf("mở vai", "giãn cơ", "stretch").any { it in text } -> Pose.STRETCH
            listOf("nhón gót", "calf raise").any { it in text } -> Pose.CALF_RAISE
            listOf("thăng bằng", "balance").any { it in text } -> Pose.BALANCE
            listOf("squat", "ngồi xổm", "đứng lên – ngồi xuống", "đứng lên - ngồi xuống").any { it in text } -> Pose.SQUAT
            // Distinguish easier variants before the generic floor push-up matcher.
            listOf("chống đẩy gối", "knee push-up", "knee push up").any { it in text } -> Pose.KNEE_PUSH_UP
            listOf("chống đẩy tường", "wall push-up", "wall push up").any { it in text } -> Pose.WALL_PUSH_UP
            listOf("chống đẩy trên bề mặt cao", "incline push-up", "incline push up").any { it in text } -> Pose.INCLINE_PUSH_UP
            listOf("push-up", "push up", "chống đẩy", "hít đất").any { it in text } -> Pose.PUSH_UP
            listOf("lunge", "chùng chân").any { it in text } -> Pose.LUNGE
            listOf("plank", "tấm ván").any { it in text } -> Pose.PLANK
            listOf("nghỉ", "thở", "thư giãn").any { it in text } -> Pose.REST
            else -> Pose.STANDING
        }
    }

    fun label(pose: Pose): String = when (pose) {
        Pose.SQUAT -> "Minh họa: hạ hông và gập gối"
        Pose.HIP_HINGE -> "Minh họa: đẩy hông ra sau, giữ lưng trung lập"
        Pose.RESISTANCE_ROW -> "Minh họa: kéo khuỷu về sau, giữ thân ổn định"
        Pose.RESISTANCE_HINGE -> "Minh họa: gập hông với tải nhẹ, giữ lưng trung lập"
        Pose.RESISTANCE_PRESS -> "Minh họa: đẩy tải nhẹ qua đầu, không ưỡn lưng"
        Pose.PLANK -> "Minh họa: giữ thân người thẳng"
        Pose.PUSH_UP -> "Minh họa: chống đẩy sàn, thân người thẳng"
        Pose.KNEE_PUSH_UP -> "Minh họa: chống đẩy với hai gối làm điểm tựa"
        Pose.WALL_PUSH_UP -> "Minh họa: đứng chống tay vào tường và gập khuỷu"
        Pose.INCLINE_PUSH_UP -> "Minh họa: chống tay trên bề mặt cao vững chắc"
        Pose.LUNGE -> "Minh họa: bước chân trước, hạ gối"
        Pose.BRIDGE -> "Minh họa: nằm ngửa và nâng hông"
        Pose.BIRD_DOG -> "Minh họa: chống tay gối, duỗi tay chân đối diện"
        Pose.DEAD_BUG -> "Minh họa: nằm ngửa, co và duỗi tay chân đối diện"
        Pose.CAT_COW -> "Minh họa: chuyển động cột sống nhẹ nhàng"
        Pose.MARCH -> "Minh họa: bước tại chỗ và nâng gối luân phiên"
        Pose.MOUNTAIN_CLIMBER -> "Minh họa: chống tay vững và luân phiên đưa gối về phía ngực"
        Pose.SUPPORTED_MARCH -> "Minh họa: đứng cạnh ghế vững và nâng gối thấp luân phiên"
        Pose.HIP_ABDUCTION -> "Minh họa: đứng cạnh điểm tựa và đưa chân sang bên"
        Pose.SEATED_HAMSTRING_STRETCH -> "Minh họa: ngồi ghế, duỗi một chân và giãn nhẹ phía sau đùi"
        Pose.SCAPULAR_SQUEEZE -> "Minh họa: kéo nhẹ hai bả vai về sau và xuống"
        Pose.FOREARM_WALL_PRESS -> "Minh họa: đặt cẳng tay lên tường, giữ cổ tay thả lỏng"
        Pose.SIDE_STEP -> "Minh họa: bước ngang ngắn, giữ gối mềm"
        Pose.SEATED_KNEE_RAISE -> "Minh họa: ngồi ghế vững và nâng từng gối"
        Pose.STRETCH -> "Minh họa: giãn cơ nhẹ, không ép biên độ"
        Pose.SIDE_PLANK -> "Minh họa: plank nghiêng có điểm tựa"
        Pose.CALF_RAISE -> "Minh họa: nhón và hạ gót chân"
        Pose.BALANCE -> "Minh họa: giữ thăng bằng cạnh điểm tựa"
        Pose.REST -> "Minh họa: nghỉ và thở đều"
        Pose.STANDING -> "Minh họa chung: đứng thẳng"
    }
}
