package com.chibialarm.app

/**
 * A single spoken heads-up near the end of a rest interval.
 *
 * The caller owns and persists the one-shot flag so pause/rotation/process restore
 * cannot repeatedly announce the same countdown. Safety pauses suppress the cue.
 */
internal object WorkoutRestVoiceCue {
    const val WARNING_WINDOW_MS = 10_000L

    fun shouldAnnounce(
        remainingMs: Long,
        alreadyAnnounced: Boolean,
        pausedForSafety: Boolean
    ): Boolean = !alreadyAnnounced &&
        !pausedForSafety &&
        remainingMs in 1L..WARNING_WINDOW_MS

    fun message(remainingMs: Long): String? {
        if (remainingMs !in 1L..WARNING_WINDOW_MS) return null
        val seconds = ((remainingMs + 999L) / 1_000L).coerceIn(1L, 10L)
        return "Còn $seconds giây nghỉ. Chuẩn bị cho động tác tiếp theo."
    }

    
    /**
     * Announces exactly what is next after a rest finishes. Required sets are
     * distinguished from optional sets so TTS never pressures the user to exceed
     * the prescribed minimum. This is guidance only; it does not start a timer.
     */
    fun completionMessage(
        moveName: String?,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan?
    ): String {
        val safeName = moveName.orEmpty()
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        if (safeName.isBlank()) return "Đã hết giờ nghỉ. Tiếp tục khi bạn sẵn sàng."
        if (plan == null) {
            return "Đã hết giờ nghỉ. Khi bạn sẵn sàng, tiếp tục: $safeName."
        }

        val done = completedSets.coerceIn(0, plan.maximumSets)
        if (done >= plan.maximumSets) {
            return "Đã hết giờ nghỉ. Các hiệp của $safeName đã được ghi nhận đủ; không cần tập thêm."
        }

        val nextSet = done + 1
        val target = if (plan.targetMin == plan.targetMax) {
            "${plan.targetMin} ${plan.unit}"
        } else {
            "${plan.targetMin} đến ${plan.targetMax} ${plan.unit}"
        }
        return if (nextSet <= plan.requiredSets) {
            "Đã hết giờ nghỉ. Khi sẵn sàng, bắt đầu hiệp $nextSet/${plan.requiredSets} của $safeName; mục tiêu $target. Hãy tập theo sức mình."
        } else {
            "Đã hết giờ nghỉ. Hiệp tiếp theo của $safeName là tùy chọn ($nextSet/${plan.maximumSets}), mục tiêu $target. Chỉ thực hiện nếu bạn còn sức; có thể bỏ qua."
        }
    }
}
