package com.chibialarm.app

/**
 * A single spoken heads-up near the end of a rest interval.
 *
 * The caller owns and persists the one-shot flag so pause/rotation/process restore
 * cannot repeatedly announce the same countdown. Safety pauses suppress the cue.
 */
internal object WorkoutRestVoiceCue {
    const val WARNING_WINDOW_MS = 10_000L

    /** The caller must report false while TTS is already speaking, to avoid flushing it. */
    fun shouldAnnounce(
        remainingMs: Long,
        alreadyAnnounced: Boolean,
        pausedForSafety: Boolean,
        microphoneActive: Boolean = false,
        speechEngineReady: Boolean = true,
        voiceCueEnabled: Boolean = true
    ): Boolean = voiceCueEnabled &&
        !alreadyAnnounced &&
        !pausedForSafety &&
        !microphoneActive &&
        speechEngineReady &&
        remainingMs in 1L..WARNING_WINDOW_MS

    fun message(remainingMs: Long, upcomingPreview: String? = null): String? {
        if (remainingMs !in 1L..WARNING_WINDOW_MS) return null
        val seconds = ((remainingMs + 999L) / 1_000L).coerceIn(1L, 10L)
        val upcoming = safeLabel(upcomingPreview, 180)
        return if (upcoming.isBlank()) {
            "Còn $seconds giây nghỉ. Chuẩn bị cho động tác tiếp theo."
        } else {
            "Còn $seconds giây nghỉ. $upcoming"
        }
    }

    /** A catalog-grounded preview of what follows rest; it never changes workout state. */
    fun previewMessage(
        moveName: String?,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan?,
        nextMoveName: String? = null,
        nextMoveDose: String? = null,
        currentRemainingLabel: String = ""
    ): String {
        val current = safeLabel(moveName, 80)
        if (current.isBlank()) return "Sau nghỉ: tiếp tục khi bạn sẵn sàng."
        if (plan != null) {
            val done = completedSets.coerceIn(0, plan.maximumSets)
            if (done < plan.maximumSets) {
                val nextSet = done + 1
                val target = if (plan.targetMin == plan.targetMax) "${plan.targetMin} ${plan.unit}"
                    else "${plan.targetMin} đến ${plan.targetMax} ${plan.unit}"
                val required = nextSet <= plan.requiredSets
                val kind = if (required) "hiệp bắt buộc" else "hiệp tùy chọn"
                val denominator = if (required) plan.requiredSets else plan.maximumSets
                return "Sau nghỉ: $kind $nextSet/$denominator của $current; mục tiêu $target."
            }
            val next = safeLabel(nextMoveName, 80)
            if (next.isNotBlank()) {
                val dose = safeLabel(nextMoveDose, 80)
                return if (dose.isBlank()) "Tiếp theo: $next." else "Tiếp theo: $next; mục tiêu $dose."
            }
            return "Sau nghỉ: đã đủ hiệp của $current; đây là bước cuối."
        }
        val next = safeLabel(nextMoveName, 80)
        if (next.isNotBlank()) {
            val dose = safeLabel(nextMoveDose, 80)
            return if (dose.isBlank()) "Tiếp theo: $next."
            else "Tiếp theo: $next; mục tiêu $dose."
        }
        val remaining = safeLabel(currentRemainingLabel, 32)
        return if (remaining.isBlank()) "Sau nghỉ: tiếp tục $current."
        else "Sau nghỉ: tiếp tục $current; thời gian gợi ý còn $remaining."
    }

    private fun safeLabel(value: String?, limit: Int): String = value.orEmpty()
        .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(limit)

    
    /**
     * Announces exactly what is next after a rest finishes. Required sets are
     * distinguished from optional sets so TTS never pressures the user to exceed
     * the prescribed minimum. This is guidance only; it does not start a timer.
     */
    fun completionMessage(
        moveName: String?,
        completedSets: Int,
        plan: WorkoutSetTracker.Plan?,
        nextMoveName: String? = null,
        nextMoveDose: String? = null
    ): String {
        val safeName = safeLabel(moveName, 80)
        if (safeName.isBlank()) return "Đã hết giờ nghỉ. Tiếp tục khi bạn sẵn sàng."
        if (plan == null) {
            val next = safeLabel(nextMoveName, 80)
            if (next.isNotBlank()) {
                val dose = safeLabel(nextMoveDose, 80)
                val nextDescription = if (dose.isBlank()) {
                    "Tiếp theo: $next."
                } else {
                    "Tiếp theo: $next; mục tiêu $dose."
                }
                return "Đã hết giờ nghỉ. $nextDescription Khi sẵn sàng, bạn có thể chuyển sang động tác tiếp theo."
            }
            return "Đã hết giờ nghỉ. Khi bạn sẵn sàng, tiếp tục: $safeName."
        }

        val done = completedSets.coerceIn(0, plan.maximumSets)
        if (done >= plan.maximumSets) {
            val next = safeLabel(nextMoveName, 80)
            if (next.isNotBlank()) {
                val dose = safeLabel(nextMoveDose, 80)
                val nextDescription = if (dose.isBlank()) {
                    "Tiếp theo: $next."
                } else {
                    "Tiếp theo: $next; mục tiêu $dose."
                }
                return "Đã hết giờ nghỉ. Các hiệp của $safeName đã được ghi nhận đủ. $nextDescription Khi sẵn sàng, bạn có thể chuyển sang động tác tiếp theo."
            }
            return "Đã hết giờ nghỉ. Các hiệp của $safeName đã được ghi nhận đủ; không cần tập thêm."
        }

        val nextSet = done + 1
        val target = if (plan.targetMin == plan.targetMax) {
            "${plan.targetMin} ${plan.unit}"
        } else {
            "${plan.targetMin} đến ${plan.targetMax} ${plan.unit}"
        }
        return if (nextSet <= plan.requiredSets) {
            "Đã hết giờ nghỉ. Khi sẵn sàng, bắt đầu hiệp bắt buộc $nextSet/${plan.requiredSets} của $safeName; mục tiêu $target. Hãy tập theo sức mình."
        } else {
            "Đã hết giờ nghỉ. Hiệp tiếp theo của $safeName là tùy chọn ($nextSet/${plan.maximumSets}), mục tiêu $target. Chỉ thực hiện nếu bạn còn sức; có thể bỏ qua."
        }
    }
}
