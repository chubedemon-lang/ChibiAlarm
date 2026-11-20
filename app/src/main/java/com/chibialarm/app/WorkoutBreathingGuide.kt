package com.chibialarm.app

/**
 * Conservative, offline breathing cues for catalogued exercises.
 * These are general coaching prompts, not medical advice or a live form assessment.
 */
internal object WorkoutBreathingGuide {
    fun cue(moveName: String): String {
        val safeName = moveName
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
            .ifBlank { "Động tác hiện tại" }

        val catalogued = TrainingCatalog.allMoves.any { it.name == moveName.trim() }
        val advice = if (!catalogued) {
            "Chibi chưa có gợi ý nhịp thở riêng cho động tác này. Hãy thở liên tục, tự nhiên; " +
                "không nín thở hoặc cố ép nhịp."
        } else {
            when (WorkoutPoseGuide.classify(moveName)) {
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
                WorkoutPoseGuide.Pose.CALF_RAISE,
                WorkoutPoseGuide.Pose.HIP_ABDUCTION,
                WorkoutPoseGuide.Pose.SCAPULAR_SQUEEZE,
                WorkoutPoseGuide.Pose.FOREARM_WALL_PRESS,
                WorkoutPoseGuide.Pose.SEATED_KNEE_RAISE ->
                    "Thở ra nhẹ ở pha dùng lực (ví dụ đứng lên, đẩy hoặc kéo); hít vào khi trở về hoặc hạ xuống. " +
                        "Không nín thở; nếu nhịp này không tự nhiên, cứ thở đều."
                WorkoutPoseGuide.Pose.PLANK,
                WorkoutPoseGuide.Pose.SIDE_PLANK,
                WorkoutPoseGuide.Pose.BALANCE ->
                    "Giữ nhịp hít vào và thở ra đều, liên tục trong suốt thời gian giữ tư thế. " +
                        "Không nín thở hoặc gồng đến mức không thể thở thoải mái."
                WorkoutPoseGuide.Pose.WARM_UP,
                WorkoutPoseGuide.Pose.MARCH,
                WorkoutPoseGuide.Pose.JUMPING_JACKS,
                WorkoutPoseGuide.Pose.MOUNTAIN_CLIMBER,
                WorkoutPoseGuide.Pose.SUPPORTED_MARCH,
                WorkoutPoseGuide.Pose.SIDE_STEP,
                WorkoutPoseGuide.Pose.COOL_DOWN ->
                    "Thở tự nhiên theo nhịp vận động và giữ tốc độ vẫn nói chuyện được. " +
                        "Nếu không thể nói thoải mái, hãy giảm tốc độ hoặc nghỉ; không cần ép chỉ thở bằng mũi."
                WorkoutPoseGuide.Pose.CAT_COW,
                WorkoutPoseGuide.Pose.SEATED_HAMSTRING_STRETCH,
                WorkoutPoseGuide.Pose.STRETCH ->
                    "Thở chậm và tự nhiên; có thể thở ra nhẹ khi đi vào biên độ giãn. " +
                        "Không nảy người, không ép đến đau và không nín thở."
                WorkoutPoseGuide.Pose.REST ->
                    "Thả lỏng và thở bình thường, không cố điều khiển nhịp thở."
                else ->
                    "Thở liên tục và tự nhiên; thường có thể thở ra nhẹ khi dùng lực, hít vào khi trở về. " +
                        "Không nín thở hoặc cố ép nhịp."
            }
        }
        return "$safeName. $advice Nếu khó thở bất thường, đau ngực, chóng mặt hoặc thấy không ổn, hãy dừng tập và nghỉ."
    }
}
