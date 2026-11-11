package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.TrainingMove
import com.chibialarm.app.WorkoutConstraintFilter
import com.chibialarm.app.WorkoutConstraintProfile
import com.chibialarm.app.WorkoutDurationPlanner
import com.chibialarm.app.WorkoutMoveSubstitution
import com.chibialarm.app.WorkoutSetTracker
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Builds a preview for several mid-session preferences without changing completed work.
 * All replacement instructions come from the curated local catalog.
 */
object WorkoutMidSessionPlanner {
    sealed class Result {
        data class Proposed(val preview: Preview) : Result()
        data class Blocked(val reason: String) : Result()
        object Unsupported : Result()
    }

    data class Preview(
        val before: List<TrainingMove>,
        val after: List<TrainingMove>,
        val currentIndex: Int,
        val currentMoveChanged: Boolean,
        val replacements: List<String>,
        val removedMoves: List<String>,
        val estimatedRemainingSeconds: Int,
        val requestedMinutes: Int?,
        val targetExceeded: Boolean,
        val restSeconds: Int,
        val extensionMoveNames: Set<String>
    ) {
        val changed: Boolean get() = before != after
    }

    fun preview(
        currentMoves: List<TrainingMove>,
        currentIndex: Int,
        completedCurrentSets: Int,
        currentStepLocked: Boolean,
        constraints: WorkoutConstraintProfile,
        request: WorkoutMidSessionRequest.Request,
        remainingCurrentSeconds: Int,
        extensionMoveNames: Set<String>,
        level: Int
    ): Result {
        if (currentMoves.isEmpty() || currentMoves.size > 500 ||
            currentIndex !in currentMoves.indices || completedCurrentSets !in 0..20 ||
            remainingCurrentSeconds !in 0..3_600 || level !in 0..2
        ) return Result.Unsupported

        val effectiveProfile = WorkoutConstraintProfile(
            avoidWristLoad = constraints.avoidWristLoad || request.avoidWristLoad,
            avoidFloorPositions = constraints.avoidFloorPositions || request.avoidFloorPositions
        )
        val prefix = currentMoves.take(currentIndex)
        val usedNames = prefix.mapTo(mutableSetOf()) { it.name }
        val reservedFutureNames = currentMoves.drop(currentIndex + 1).map { it.name }.toSet()
        val remaining = mutableListOf<TrainingMove>()
        val replacements = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val extensions = extensionMoveNames.toMutableSet()
        var currentChanged = false

        currentMoves.drop(currentIndex).forEachIndexed { offset, move ->
            val isCurrent = offset == 0
            val mustAvoid = !WorkoutConstraintFilter.isAllowed(move, effectiveProfile)
            val mustRemoveEquipment = request.avoidExternalLoad && requiresExternalLoad(move)
            val mustChange = mustAvoid || mustRemoveEquipment
            val canReplace = !isCurrent || (!currentStepLocked && completedCurrentSets == 0)

            if (isCurrent && mustChange && !canReplace) {
                val reason = if (mustRemoveEquipment && !mustAvoid) {
                    "Động tác hiện tại cần tạ hoặc dây kháng lực nhưng đã có hiệp được ghi nhận. " +
                        "Chibi không thể đổi hồi tố; hãy kết thúc bước an toàn trước khi đổi phần còn lại."
                } else {
                    "Động tác hiện tại không phù hợp với hạn chế vận động mới và đã có hiệp được ghi nhận. " +
                        "Chibi không thể đổi hồi tố; hãy dừng buổi tập và tạo phiên an toàn mới."
                }
                return Result.Blocked(reason)
            }

            val mayChooseEasier = request.preferEasier && canReplace
            val candidates = if (canReplace && (mustChange || mayChooseEasier)) {
                WorkoutMoveSubstitution.alternativesFor(move, effectiveProfile)
            } else {
                emptyList()
            }
            val replacement = candidates.firstOrNull {
                it.name !in usedNames && it.name !in reservedFutureNames && it.name != move.name &&
                    (!request.avoidExternalLoad || !requiresExternalLoad(it))
            }

            when {
                replacement != null -> {
                    remaining += replacement
                    usedNames += replacement.name
                    replacements += "${move.name} → ${replacement.name}"
                    if (isCurrent) currentChanged = true
                    if (move.name in extensions) {
                        extensions.remove(move.name)
                        extensions += replacement.name
                    }
                }
                mustChange && isCurrent -> {
                    val reason = if (mustRemoveEquipment) {
                        "Chưa có biến thể không dùng tạ/dây được tuyển chọn cho động tác hiện tại. Chibi không tự bịa bài thay thế."
                    } else {
                        "Chưa có biến thể được tuyển chọn phù hợp với hạn chế vận động cho động tác hiện tại. Chibi không tự bịa bài thay thế."
                    }
                    return Result.Blocked(reason)
                }
                mustChange -> {
                    removed += move.name
                    extensions.remove(move.name)
                }
                else -> {
                    remaining += move
                    usedNames += move.name
                }
            }
        }

        if (remaining.none(::isMainExercise)) {
            return Result.Blocked(
                "Sau khi áp dụng hạn chế, không còn động tác chính phù hợp trong phần buổi tập còn lại."
            )
        }

        val extraRest = request.restSeconds ?: 0
        fun estimate(): Int = estimateRemaining(
            remaining = remaining,
            remainingCurrentSeconds = remainingCurrentSeconds,
            currentMoveChanged = currentChanged,
            completedCurrentSets = completedCurrentSets,
            level = level,
            extraRestSeconds = extraRest
        )

        // Only remove unstarted, explicitly optional extension moves to meet a shorter target.
        // Required base exercises and set counts are never silently cut.
        val targetSeconds = request.remainingMinutes?.times(60)
        if (targetSeconds != null) {
            while (estimate() > targetSeconds) {
                val optionalIndex = remaining.indices.lastOrNull { offset ->
                    offset > 0 && remaining[offset].name in extensions
                } ?: break
                val removedMove = remaining.removeAt(optionalIndex)
                removed += removedMove.name
                extensions.remove(removedMove.name)
            }
        }

        if (remaining.none(::isMainExercise)) {
            return Result.Blocked("Không thể rút ngắn mà vẫn giữ lại động tác chính chưa hoàn thành.")
        }

        val after = prefix + remaining
        val estimated = estimate()
        val targetExceeded = targetSeconds != null && estimated > targetSeconds

        return Result.Proposed(
            Preview(
                before = currentMoves.toList(),
                after = after,
                currentIndex = currentIndex,
                currentMoveChanged = currentChanged,
                replacements = replacements,
                removedMoves = removed,
                estimatedRemainingSeconds = estimated,
                requestedMinutes = request.remainingMinutes,
                targetExceeded = targetExceeded,
                restSeconds = extraRest,
                extensionMoveNames = extensions
            )
        )
    }

    private fun requiresExternalLoad(move: TrainingMove): Boolean = move.requiresExternalLoad

    private fun isMainExercise(move: TrainingMove): Boolean =
        move.name !in setOf("Khởi động tại chỗ", "Thả lỏng", "Giãn cơ và thở chậm")

    private fun estimateRemaining(
        remaining: List<TrainingMove>,
        remainingCurrentSeconds: Int,
        currentMoveChanged: Boolean,
        completedCurrentSets: Int,
        level: Int,
        extraRestSeconds: Int
    ): Int {
        val active = remaining.mapIndexed { index, move ->
            if (index == 0) {
                // GuidedWorkoutActivity caps the replacement timer at min(oldRemaining, newMove.seconds).
                // Mirror that exact rule in the preview so the user is not shown a stale duration.
                val currentSeconds = if (currentMoveChanged) {
                    minOf(remainingCurrentSeconds, move.seconds.coerceAtLeast(0))
                } else remainingCurrentSeconds
                currentSeconds.toLong()
            } else WorkoutDurationPlanner.activeSeconds(move).toLong()
        }.sum()

        val rests = remaining.mapIndexed { index, move ->
            val plan = WorkoutSetTracker.parse(move.dose, move.restSeconds)
            if (plan == null) 0L
            else {
                val completed = if (index == 0) completedCurrentSets else 0
                val setsLeft = (plan.requiredSets - completed).coerceAtLeast(0)
                ((setsLeft - 1).coerceAtLeast(0) * plan.defaultRestSeconds).toLong()
            }
        }.sum()

        val transitions = (remaining.size - 1).coerceAtLeast(0).toLong() *
            WorkoutDurationPlanner.transitionAllowance(level)
        return (active + rests + transitions + extraRestSeconds)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Store only canonical exercise names, never free-form user speech or health notes. */
    fun encodePlan(moves: List<TrainingMove>): String {
        if (moves.isEmpty() || moves.size > 500) return ""
        return moves.joinToString(";") { URLEncoder.encode(it.name, "UTF-8") }.take(8_192)
    }

    /** Rebuild a checkpoint plan only from known catalog entries; reject malformed data. */
    fun decodePlan(raw: String?): List<TrainingMove>? {
        if (raw.isNullOrBlank() || raw.length > 8_192) return null
        val names = raw.split(';')
        if (names.isEmpty() || names.size > 500 || names.any { it.isBlank() }) return null
        val catalog = TrainingCatalog.allMoves.associateBy { it.name }
        val decoded = names.map { encoded ->
            val name = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
                ?: return null
            catalog[name] ?: return null
        }
        return decoded
    }
}
