package com.chibialarm.app

/**
 * Offline, exercise-specific spoken coaching. Uses the catalog as the source of
 * truth instead of inferring technique from a generic pose thumbnail.
 * Never diagnoses injuries or claims to have observed the user.
 */
object WorkoutContextCoach {
    fun script(move: TrainingMove, preferEasier: Boolean = false): String {
        val name = move.name.trim().take(90).ifBlank { "Động tác hiện tại" }
        val dose = move.dose.trim().take(120)
        val steps = move.steps.trim().take(550)
        val easier = move.easier.trim().take(240)
        val avoid = move.avoid.trim().take(240)
        return buildString {
            append(name).append(". ")
            if (dose.isNotBlank()) append("Mục tiêu tham khảo: ").append(dose).append(". ")
            if (preferEasier && easier.isNotBlank()) {
                append("Cách tập nhẹ hơn: ").append(easier).append(". ")
            } else if (steps.isNotBlank()) {
                append("Cách thực hiện: ").append(steps).append(". ")
            }
            if (avoid.isNotBlank()) append("Lưu ý: ").append(avoid).append(". ")
            append("Hãy dừng nếu đau, chóng mặt hoặc khó chịu. Đây là hướng dẫn, không phải đánh giá tư thế.")
        }
    }
}
