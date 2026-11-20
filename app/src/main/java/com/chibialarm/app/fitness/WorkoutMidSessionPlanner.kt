package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.TrainingEquipment
import com.chibialarm.app.TrainingEquipmentAvailability
import com.chibialarm.app.TrainingMove
import com.chibialarm.app.WorkoutExtensionPreferences
import com.chibialarm.app.WorkoutExtensionSelector
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
        val extensionMoveNames: Set<String>,
        val addedMoves: List<String> = emptyList(),
        val availableEquipment: Set<TrainingEquipment> = emptySet(),
        // Self-confirmed progress for the active set-based move; never inferred from elapsed time.
        val completedCurrentSets: Int = 0,
        // Non-null only when the active move's optional set ceiling was reduced.
        val currentSetLimit: Int? = null,
        // Non-null only when the active move's upper rep target was reduced.
        val currentRepLimit: Int? = null
    ) {
        val changed: Boolean get() = before != after
    }

    /**
     * Resolve explicit mid-session permissions without confusing an omitted field with consent.
     * A true restriction wins within one parsed utterance; otherwise an explicit allow can
     * relax only that preference for the uncompleted portion of the current session.
     */
    fun effectiveConstraints(
        current: WorkoutConstraintProfile,
        request: WorkoutMidSessionRequest.Request
    ): WorkoutConstraintProfile = WorkoutConstraintProfile(
        avoidWristLoad = when {
            request.avoidWristLoad -> true
            request.allowWristLoad -> false
            else -> current.avoidWristLoad
        },
        avoidFloorPositions = when {
            request.avoidFloorPositions -> true
            request.allowFloorPositions -> false
            else -> current.avoidFloorPositions
        },
        avoidOverheadReach = when {
            request.avoidOverheadReach -> true
            request.allowOverheadReach -> false
            else -> current.avoidOverheadReach
        },
        avoidJumping = when {
            request.avoidJumping -> true
            request.allowJumping -> false
            else -> current.avoidJumping
        },
        avoidKneeling = when {
            request.avoidKneeling -> true
            request.allowKneeling -> false
            else -> current.avoidKneeling
        }
    )

    fun preview(
        currentMoves: List<TrainingMove>,
        currentIndex: Int,
        completedCurrentSets: Int,
        currentStepLocked: Boolean,
        constraints: WorkoutConstraintProfile,
        request: WorkoutMidSessionRequest.Request,
        remainingCurrentSeconds: Int,
        extensionMoveNames: Set<String>,
        level: Int,
        extensionPreferences: WorkoutExtensionPreferences = WorkoutExtensionPreferences()
    ): Result {
        if (currentMoves.isEmpty() || currentMoves.size > 500 ||
            currentIndex !in currentMoves.indices || completedCurrentSets !in 0..20 ||
            remainingCurrentSeconds !in 0..3_600 || level !in 0..2
        ) return Result.Unsupported
        val currentSetPlan = WorkoutSetTracker.parse(
            currentMoves[currentIndex].dose,
            currentMoves[currentIndex].restSeconds
        )
        if (currentSetPlan != null && completedCurrentSets > currentSetPlan.maximumSets) {
            return Result.Unsupported
        }

        val effectiveProfile = effectiveConstraints(constraints, request)
        val unavailableTools = request.unavailableEquipment +
            if (request.avoidExternalLoad) {
                setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND)
            } else emptySet()
        // An explicit mid-session inventory is a hard boundary for optional additions too.
        // Do not let previously selected profile equipment leak back into the selector after
        // the user says they only have (for example) a mat.
        val effectiveAvailableEquipment = (
            TrainingEquipmentAvailability.fromPreference(extensionPreferences.equipment) +
                extensionPreferences.additionalEquipment + request.availableEquipment
            ) - unavailableTools - request.unavailableEquipment
        val effectiveExtensionPreferences = extensionPreferences.copy(
            equipment = 0,
            impact = when {
                request.avoidJumping -> 1
                request.allowJumping -> 0
                else -> extensionPreferences.impact
            },
            constraints = effectiveProfile,
            energy = if (request.lowEnergy || request.preferEasier) 0 else extensionPreferences.energy,
            additionalEquipment = effectiveAvailableEquipment
        )
        val prefix = currentMoves.take(currentIndex)
        val usedNames = prefix.mapTo(mutableSetOf()) { it.name }
        val reservedFutureNames = currentMoves.drop(currentIndex + 1).map { it.name }.toSet()
        val explicitReplacement = request.replaceCurrentWith?.let { requestedName ->
            if (currentStepLocked || completedCurrentSets > 0) {
                return Result.Blocked(
                    "Hiệp hiện tại đã được ghi nhận. Chibi không thể thay động tác hồi tố; hãy giữ nguyên hiệp đã làm."
                )
            }
            val requestedMove = TrainingCatalog.allMoves.singleOrNull { it.name == requestedName }
                ?: return Result.Unsupported
            val approved = WorkoutMoveSubstitution.alternativesFor(
                currentMoves[currentIndex],
                effectiveProfile,
                effectiveAvailableEquipment
            ).firstOrNull { it.name == requestedMove.name }
                ?: return Result.Blocked(
                    "Bài bạn chọn chưa phải biến thể thay thế được tuyển chọn cho động tác hiện tại. Giáo án được giữ nguyên."
                )
            if (!isMainExercise(approved) ||
                !TrainingEquipmentAvailability.allows(approved, effectiveAvailableEquipment) ||
                !equipmentAvailable(approved, request)
            ) {
                return Result.Blocked(
                    "Bài thay thế không phù hợp với dụng cụ hoặc hạn chế hiện tại. Giáo án được giữ nguyên."
                )
            }
            if (approved.name in usedNames || approved.name in reservedFutureNames) {
                return Result.Blocked(
                    "Bài thay thế đã có ở phần khác của giáo án. Chibi không thêm trùng động tác."
                )
            }
            approved
        }
        val remaining = mutableListOf<TrainingMove>()
        val replacements = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val extensions = extensionMoveNames.toMutableSet()
        var currentChanged = false

        currentMoves.drop(currentIndex).forEachIndexed { offset, move ->
            val isCurrent = offset == 0
            val mustAvoid = !WorkoutConstraintFilter.isAllowed(move, effectiveProfile)
            val mustRemoveEquipment = !equipmentAvailable(move, request)
            val mustChange = mustAvoid || mustRemoveEquipment
            val canReplace = !isCurrent || (!currentStepLocked && completedCurrentSets == 0)

            if (isCurrent && mustChange && !canReplace) {
                val reason = if (mustRemoveEquipment && !mustAvoid) {
                    "Động tác hiện tại cần ${unavailableEquipmentLabel(move, request)} nhưng đã có hiệp được ghi nhận. " +
                        "Chibi không thể đổi hồi tố; hãy kết thúc bước an toàn trước khi đổi phần còn lại."
                } else {
                    "Động tác hiện tại không phù hợp với hạn chế vận động mới và đã có hiệp được ghi nhận. " +
                        "Chibi không thể đổi hồi tố; hãy dừng buổi tập và tạo phiên an toàn mới."
                }
                return Result.Blocked(reason)
            }

            val mayChooseEasier = request.preferEasier && canReplace
            val candidates = if (canReplace && (mustChange || mayChooseEasier)) {
                WorkoutMoveSubstitution.alternativesFor(move, effectiveProfile, effectiveAvailableEquipment)
            } else {
                emptyList()
            }
            val replacement = if (isCurrent && explicitReplacement != null) {
                explicitReplacement
            } else {
                candidates.firstOrNull {
                    it.name !in usedNames && it.name !in reservedFutureNames && it.name != move.name &&
                        TrainingEquipmentAvailability.allows(it, effectiveAvailableEquipment) &&
                        equipmentAvailable(it, request)
                }
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

        if (request.removeOptionalExercises) {
            // The current step is index 0 in `remaining`; never remove it from under
            // the user's feet. Only future, unstarted extension moves are removable.
            val optionalFutureIndexes = remaining.indices.filter { index ->
                index > 0 && remaining[index].name in extensions
            }
            if (optionalFutureIndexes.isEmpty()) {
                return Result.Blocked(
                    "Không có bài bổ sung tùy chọn nào ở phía trước để bỏ. Động tác đang tập và các hiệp đã ghi nhận được giữ nguyên."
                )
            }
            optionalFutureIndexes.asReversed().forEach { index ->
                val removedMove = remaining.removeAt(index)
                removed += removedMove.name
                extensions.remove(removedMove.name)
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

        val addedMoves = mutableListOf<String>()
        if (request.addOptionalExercise) {
            // A bounded three-minute allowance is used only when the user explicitly asks
            // to add volume. If they state a remaining-time target, only unused time is available.
            val additionBudget = targetSeconds?.let {
                (it - estimate()).coerceAtLeast(0)
            } ?: 180
            val additions = WorkoutExtensionSelector.selectAdditional(
                baseMoves = prefix + remaining,
                level = level,
                availableSeconds = additionBudget,
                preferences = effectiveExtensionPreferences,
                preferredEquipment = request.availableEquipment,
                maxMoves = 1
            )
            if (additions.isEmpty()) {
                val reason = if (request.lowEnergy || request.preferEasier) {
                    "Bạn vừa yêu cầu giảm tải. Chibi không tăng khối lượng tập trong cùng yêu cầu; hãy giữ phần hiện tại hoặc nghỉ thêm."
                } else {
                    "Chưa có bài bổ sung đã tuyển chọn phù hợp với dụng cụ, hạn chế và thời gian còn lại. Giáo án hiện tại được giữ nguyên."
                }
                return Result.Blocked(reason)
            }
            val cooldownIndex = remaining.indexOfLast {
                it.name == "Thả lỏng" || it.name == "Giãn cơ và thở chậm"
            }
            val insertAt = if (cooldownIndex >= 0) cooldownIndex else remaining.size
            remaining.addAll(insertAt, additions)
            additions.forEach {
                addedMoves += it.name
                extensions += it.name
            }
        }

        if (remaining.none(::isMainExercise)) {
            return Result.Blocked("Không thể rút ngắn mà vẫn giữ lại động tác chính chưa hoàn thành.")
        }

        var appliedCurrentSetLimit: Int? = null
        request.currentSetLimit?.let { requestedLimit ->
            val activeMove = remaining.firstOrNull()
                ?: return Result.Blocked("Không còn động tác hiện tại để giới hạn số hiệp.")
            val plan = WorkoutSetTracker.parse(activeMove.dose, activeMove.restSeconds)
                ?: return Result.Blocked("Động tác hiện tại không có giáo án hiệp/lần để giới hạn số hiệp.")
            val confirmedSets = if (currentChanged) 0 else completedCurrentSets
            when {
                requestedLimit < confirmedSets -> return Result.Blocked(
                    "Bạn đã xác nhận $confirmedSets hiệp. Chibi không thể xóa hoặc sửa các hiệp đã ghi nhận."
                )
                requestedLimit < plan.requiredSets -> return Result.Blocked(
                    "Giáo án yêu cầu tối thiểu ${plan.requiredSets} hiệp. Chibi chỉ có thể bỏ hiệp tùy chọn, không hạ mức bắt buộc hoặc phần XP tương ứng."
                )
                requestedLimit > plan.maximumSets -> return Result.Blocked(
                    "Mục tiêu mới vượt quá giới hạn ${plan.maximumSets} hiệp hiện tại. Chibi không tăng khối lượng trong thao tác giảm tải."
                )
                requestedLimit == plan.maximumSets -> return Result.Blocked(
                    "Động tác đã có giới hạn tối đa $requestedLimit hiệp; không có hiệp tùy chọn nào để bỏ."
                )
            }
            val repTarget = if (plan.targetMin == plan.targetMax) {
                "${plan.targetMin}"
            } else {
                "${plan.targetMin}–${plan.targetMax}"
            }
            remaining[0] = activeMove.copy(
                dose = "$requestedLimit hiệp × $repTarget ${plan.unit}"
            )
            appliedCurrentSetLimit = requestedLimit
        }

        var appliedCurrentRepLimit: Int? = null
        request.currentRepLimit?.let { requestedLimit ->
            val activeMove = remaining.firstOrNull()
                ?: return Result.Blocked("Không còn động tác hiện tại để giới hạn số lần.")
            val plan = WorkoutSetTracker.parse(activeMove.dose, activeMove.restSeconds)
                ?: return Result.Blocked("Động tác hiện tại không có giáo án hiệp/lần để giới hạn số lần.")

            val confirmedSets = if (currentChanged) 0 else completedCurrentSets
            if (confirmedSets >= plan.maximumSets) {
                return Result.Blocked("Bạn đã xác nhận đủ ${plan.maximumSets} hiệp; không còn hiệp chưa thực hiện để đổi số lần.")
            }
            when {
                plan.unit == "giây" -> return Result.Blocked(
                    "Động tác này được tính theo thời gian, không thể đổi thành số lần."
                )
                requestedLimit < plan.targetMin -> return Result.Blocked(
                    "Mức tối thiểu hiện tại là ${plan.targetMin} ${plan.unit}. Chibi không hạ chuẩn bắt buộc hoặc phần thưởng tương ứng."
                )
                requestedLimit > plan.targetMax -> return Result.Blocked(
                    "Mục tiêu mới vượt quá giới hạn ${plan.targetMax} ${plan.unit}. Chibi chỉ chấp nhận giảm tải."
                )
                requestedLimit == plan.targetMax -> return Result.Blocked(
                    "Mục tiêu tối đa đã là $requestedLimit ${plan.unit}; không có phần lặp tùy chọn nào để giảm."
                )
            }
            val setRange = if (plan.requiredSets == plan.maximumSets) {
                "${plan.requiredSets}"
            } else {
                "${plan.requiredSets}–${plan.maximumSets}"
            }
            val repRange = if (plan.targetMin == requestedLimit) {
                "$requestedLimit"
            } else {
                "${plan.targetMin}–$requestedLimit"
            }
            remaining[0] = activeMove.copy(
                dose = "$setRange hiệp × $repRange ${plan.unit}"
            )
            appliedCurrentRepLimit = requestedLimit
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
                extensionMoveNames = extensions,
                addedMoves = addedMoves,
                availableEquipment = effectiveAvailableEquipment,
                completedCurrentSets = completedCurrentSets,
                currentSetLimit = appliedCurrentSetLimit,
                currentRepLimit = appliedCurrentRepLimit
            )
        )
    }

    private fun requiresExternalLoad(move: TrainingMove): Boolean = move.requiresExternalLoad

    private fun equipmentAvailable(
        move: TrainingMove,
        request: WorkoutMidSessionRequest.Request
    ): Boolean {
        if (request.avoidExternalLoad && requiresExternalLoad(move)) return false
        return move.equipmentOptions.isEmpty() ||
            move.equipmentOptions.any { it !in request.unavailableEquipment }
    }

    private fun unavailableEquipmentLabel(
        move: TrainingMove,
        request: WorkoutMidSessionRequest.Request
    ): String {
        val labels = move.equipmentOptions
            .filter { it in request.unavailableEquipment }
            .map { equipment ->
                when (equipment) {
                    com.chibialarm.app.TrainingEquipment.DUMBBELL -> "tạ đơn"
                    com.chibialarm.app.TrainingEquipment.RESISTANCE_BAND -> "dây kháng lực"
                    com.chibialarm.app.TrainingEquipment.STABLE_CHAIR -> "ghế vững"
                    com.chibialarm.app.TrainingEquipment.MAT -> "thảm tập"
                }
            }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(" hoặc ")
            ?: "dụng cụ cần thiết"
    }

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
