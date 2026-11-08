package com.chibialarm.app

/**
 * Small, original offline posture thumbnails. They are orientation cues, NOT a
 * form assessment or a substitute for the exercise's written instructions.
 */
object WorkoutPoseGuide {
    enum class Pose { SQUAT, PLANK, PUSH_UP, LUNGE, STANDING, REST }

    fun classify(name: String): Pose {
        val text = name.lowercase(java.util.Locale.ROOT)
        return when {
            listOf("squat", "ngồi xổm", "đứng lên ngồi xuống").any { it in text } -> Pose.SQUAT
            listOf("plank", "tấm ván").any { it in text } -> Pose.PLANK
            listOf("push-up", "push up", "chống đẩy", "hít đất").any { it in text } -> Pose.PUSH_UP
            listOf("lunge", "chùng chân").any { it in text } -> Pose.LUNGE
            listOf("nghỉ", "thở", "thư giãn").any { it in text } -> Pose.REST
            else -> Pose.STANDING
        }
    }

    fun label(pose: Pose): String = when (pose) {
        Pose.SQUAT -> "Minh họa: hạ hông và gập gối"
        Pose.PLANK -> "Minh họa: giữ thân người thẳng"
        Pose.PUSH_UP -> "Minh họa: chống tay, thân người thẳng"
        Pose.LUNGE -> "Minh họa: bước chân trước, hạ gối"
        Pose.REST -> "Minh họa: nghỉ và thở đều"
        Pose.STANDING -> "Minh họa chung: đứng thẳng"
    }
}
