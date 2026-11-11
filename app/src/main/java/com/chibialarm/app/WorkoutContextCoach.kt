package com.chibialarm.app

/**
 * Offline, exercise-specific coaching. The exercise catalog is the source of truth;
 * this class never diagnoses injuries or claims to observe the user's technique.
 */
object WorkoutContextCoach {
    data class SetProgress(
        val completed: Int,
        val required: Int,
        val maximum: Int,
        val targetMin: Int,
        val targetMax: Int,
        val unit: String
    ) {
        init {
            require(required in 1..20)
            require(maximum in required..20)
            require(completed in 0..maximum)
            require(targetMin in 1..200)
            require(targetMax in targetMin..200)
            require(unit in setOf("lần", "nhịp", "giây"))
        }
    }

    data class Progress(
        val completedMoves: Int,
        val totalMoves: Int,
        val currentMoveIndex: Int,
        val currentMoveName: String,
        val sets: SetProgress? = null,
        val resting: Boolean = false,
        val restSecondsRemaining: Int = 0,
        val exerciseSecondsRemaining: Int = 0,
        val pausedForSafety: Boolean = false,
        val nextMoveName: String? = null,
        /** Approximate remaining session time, including prescribed rests and transitions. */
        val estimatedSessionSecondsRemaining: Int? = null
    ) {
        init {
            require(totalMoves in 1..500)
            require(completedMoves in 0..totalMoves)
            require(currentMoveIndex in 0 until totalMoves)
            require(restSecondsRemaining in 0..600)
            require(exerciseSecondsRemaining in 0..600)
            require(estimatedSessionSecondsRemaining == null ||
                estimatedSessionSecondsRemaining in 0..86_400)
        }
    }

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

    /**
     * A spoken status report built only from the active local session.
     * Set counts are explicitly self-confirmed; rest time is kept separate from
     * the exercise reminder timer and no health note or free-form chat is included.
     *
     * A safety lock suppresses all forward-looking timing and next-move prompts,
     * even if a stale caller accidentally supplies those fields.
     */
    fun progressScript(progress: Progress): String {
        val current = progress.currentMoveName.trim().take(90).ifBlank { "động tác hiện tại" }
        val next = progress.nextMoveName?.trim()?.take(90)?.takeIf { it.isNotBlank() }
        val completed = progress.completedMoves.coerceIn(0, progress.totalMoves)
        return buildString {
            append("Tiến độ buổi tập: đã ghi nhận ")
                .append(completed).append(" trên ").append(progress.totalMoves)
                .append(" động tác. ")
            append("Đang ở bước ").append(progress.currentMoveIndex + 1)
                .append(": ").append(current).append(". ")

            progress.sets?.let { sets ->
                val target = if (sets.targetMin == sets.targetMax) {
                    "${sets.targetMin} ${sets.unit}"
                } else {
                    "${sets.targetMin} đến ${sets.targetMax} ${sets.unit}"
                }
                append("Bạn đã tự xác nhận ").append(sets.completed)
                    .append(" trên ").append(sets.maximum).append(" hiệp; tối thiểu ")
                    .append(sets.required).append(" hiệp, mục tiêu mỗi hiệp ")
                    .append(target).append(". ")
            }

            when {
                progress.pausedForSafety ->
                    append("Buổi tập đang khóa an toàn vì bạn đã báo đau. Hãy nghỉ; Chibi sẽ không tự tiếp tục.")
                progress.resting ->
                    append("Bạn đang nghỉ riêng, còn ").append(progress.restSecondsRemaining)
                        .append(" giây. Thời gian nghỉ không tính vào thời gian vận động.")
                progress.exerciseSecondsRemaining > 0 ->
                    append("Thời gian nhắc nhở của động tác còn ")
                        .append(progress.exerciseSecondsRemaining).append(" giây.")
            }

            // Never encourage progression or imply an ETA while the safety lock is active.
            if (!progress.pausedForSafety && next != null) {
                append(" Động tác tiếp theo: ").append(next).append(".")
            }
            if (!progress.pausedForSafety) {
                progress.estimatedSessionSecondsRemaining?.let { estimate ->
                    val minutes = estimate / 60
                    val seconds = estimate % 60
                    append(" Ước tính phần còn lại khoảng ")
                    if (minutes > 0) append(minutes).append(" phút ")
                    append(seconds).append(" giây, gồm thời gian nhắc động tác, nghỉ và chuyển bước; đây chỉ là ước tính.")
                }
            }
            append(" Số hiệp là xác nhận của bạn, không phải cảm biến tự đếm.")
        }.take(900)
    }
}
