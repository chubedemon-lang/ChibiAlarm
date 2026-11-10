package com.chibialarm.app

/**
 * Original offline posture illustrations. They are movement cues, NOT a
 * form assessment or a substitute for the exercise's written instructions.
 */
object WorkoutPoseGuide {
    enum class Pose {
        SQUAT, PLANK, PUSH_UP, LUNGE,
        BRIDGE, BIRD_DOG, DEAD_BUG, CAT_COW,
        MARCH, STRETCH, SIDE_PLANK, CALF_RAISE, BALANCE,
        STANDING, REST
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
            listOf("đi bộ nhanh tại chỗ", "bước tại chỗ", "march").any { it in text } -> Pose.MARCH
            listOf("mở vai", "giãn cơ", "stretch").any { it in text } -> Pose.STRETCH
            listOf("nhón gót", "calf raise").any { it in text } -> Pose.CALF_RAISE
            listOf("thăng bằng", "balance").any { it in text } -> Pose.BALANCE
            listOf("squat", "ngồi xổm", "đứng lên – ngồi xuống", "đứng lên - ngồi xuống").any { it in text } -> Pose.SQUAT
            listOf("push-up", "push up", "chống đẩy", "hít đất").any { it in text } -> Pose.PUSH_UP
            listOf("lunge", "chùng chân").any { it in text } -> Pose.LUNGE
            listOf("plank", "tấm ván").any { it in text } -> Pose.PLANK
            listOf("nghỉ", "thở", "thư giãn").any { it in text } -> Pose.REST
            else -> Pose.STANDING
        }
    }

    fun label(pose: Pose): String = when (pose) {
        Pose.SQUAT -> "Minh họa: hạ hông và gập gối"
        Pose.PLANK -> "Minh họa: giữ thân người thẳng"
        Pose.PUSH_UP -> "Minh họa: chống tay, thân người thẳng"
        Pose.LUNGE -> "Minh họa: bước chân trước, hạ gối"
        Pose.BRIDGE -> "Minh họa: nằm ngửa và nâng hông"
        Pose.BIRD_DOG -> "Minh họa: chống tay gối, duỗi tay chân đối diện"
        Pose.DEAD_BUG -> "Minh họa: nằm ngửa, co và duỗi tay chân đối diện"
        Pose.CAT_COW -> "Minh họa: chuyển động cột sống nhẹ nhàng"
        Pose.MARCH -> "Minh họa: bước tại chỗ và nâng gối luân phiên"
        Pose.STRETCH -> "Minh họa: giãn cơ nhẹ, không ép biên độ"
        Pose.SIDE_PLANK -> "Minh họa: plank nghiêng có điểm tựa"
        Pose.CALF_RAISE -> "Minh họa: nhón và hạ gót chân"
        Pose.BALANCE -> "Minh họa: giữ thăng bằng cạnh điểm tựa"
        Pose.REST -> "Minh họa: nghỉ và thở đều"
        Pose.STANDING -> "Minh họa chung: đứng thẳng"
    }
}
