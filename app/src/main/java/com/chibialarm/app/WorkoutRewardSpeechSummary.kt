package com.chibialarm.app

/**
 * Short, opt-in spoken recap of the committed RPG reward.
 *
 * It intentionally excludes exercise names, effort check-ins, health notes and chat.
 * The caller must wait for an explicit user tap before sending this text to TTS.
 */
internal object WorkoutRewardSpeechSummary {
    fun build(display: WorkoutRewardDisplay): String {
        val award = if (display.awardedXp > 0) {
            "Bạn nhận được ${display.awardedXp.coerceIn(0, 30)} điểm EXP trong buổi này."
        } else {
            "Phần thưởng của buổi này đã được ghi nhận trước đó."
        }
        val progress = if (display.maxLevelReached) {
            "Chibi đang ở cấp ${display.level.coerceIn(1, 1000)} và đã đạt cấp tối đa."
        } else {
            val remaining = (display.xpRequired - display.xpInLevel).coerceAtLeast(0L)
            "Chibi đang ở cấp ${display.level.coerceIn(1, 1000)}. Còn $remaining EXP để lên cấp tiếp theo."
        }
        return "Chúc mừng! Buổi tập đã được lưu. $award $progress"
    }
}
