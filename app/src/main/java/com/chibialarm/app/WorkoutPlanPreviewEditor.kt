package com.chibialarm.app

/**
 * Immutable draft for choosing a curated exercise replacement before a new workout starts.
 * It never edits the active session or grants progress. The guided activity revalidates the
 * encoded substitutions against the same catalog, constraints and equipment before use.
 */
internal object WorkoutPlanPreviewEditor {
    const val MAX_MOVES = 100

    data class Draft(
        val originalMoves: List<TrainingMove>,
        val moves: List<TrainingMove>,
        val substitutions: Map<Int, TrainingMove>,
        val targetMinutes: Int,
        val level: Int,
        val constraints: WorkoutConstraintProfile,
        val equipmentSelection: Int,
        val additionalEquipment: Set<TrainingEquipment>,
        val estimatedSeconds: Int
    ) {
        val estimatedMinutes: Int
            get() = ((estimatedSeconds.toLong() + 59L) / 60L)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val exceedsTarget: Boolean
            get() = estimatedSeconds.toLong() > targetMinutes.toLong() * 60L
    }

    fun start(
        moves: List<TrainingMove>,
        targetMinutes: Int,
        level: Int,
        constraints: WorkoutConstraintProfile,
        equipmentSelection: Int,
        additionalEquipment: Set<TrainingEquipment> = emptySet()
    ): Draft? {
        if (moves.isEmpty() || moves.size > MAX_MOVES ||
            targetMinutes !in 5..120 || level !in 0..2 ||
            equipmentSelection !in 0..5 ||
            additionalEquipment.size > TrainingEquipment.values().size
        ) return null

        val available = availableEquipment(equipmentSelection, additionalEquipment)
        if (moves.any { !WorkoutConstraintFilter.isAllowed(it, constraints) ||
                !TrainingEquipmentAvailability.allows(it, available) }
        ) return null

        val snapshot = moves.toList()
        return Draft(
            originalMoves = snapshot,
            moves = snapshot,
            substitutions = emptyMap(),
            targetMinutes = targetMinutes,
            level = level,
            constraints = constraints,
            equipmentSelection = equipmentSelection,
            additionalEquipment = additionalEquipment.toSet(),
            estimatedSeconds = estimate(snapshot, level)
        )
    }

    /** Options are always derived from the original step, not from caller-supplied text. */
    fun alternatives(draft: Draft, index: Int): List<TrainingMove> {
        if (!isValid(draft) || index !in draft.originalMoves.indices) return emptyList()
        val available = availableEquipment(draft.equipmentSelection, draft.additionalEquipment)
        return WorkoutMoveSubstitution.alternativesFor(
            draft.originalMoves[index], draft.constraints, available
        ).filter { TrainingEquipmentAvailability.allows(it, available) }
            .distinctBy { it.name }
    }

    /** Returns a new draft; the source plan and completed journal data are never touched. */
    fun replace(draft: Draft, index: Int, requested: TrainingMove): Draft? {
        if (!isValid(draft) || index !in draft.originalMoves.indices) return null
        val canonical = alternatives(draft, index).firstOrNull { it.name == requested.name }
            ?: return null
        val nextMoves = draft.moves.toMutableList().also { it[index] = canonical }.toList()
        val nextSubstitutions = draft.substitutions.toMutableMap().also {
            it[index] = canonical
        }.toSortedMap()
        return draft.copy(
            moves = nextMoves,
            substitutions = nextSubstitutions,
            estimatedSeconds = estimate(nextMoves, draft.level)
        )
    }

    /** Only canonical, still-approved name/index pairs leave the preview. */
    fun encodeSubstitutions(draft: Draft): String =
        if (isValid(draft)) {
            WorkoutMoveSubstitution.encode(
                draft.substitutions,
                draft.originalMoves,
                draft.constraints,
                availableEquipment(draft.equipmentSelection, draft.additionalEquipment)
            )
        } else ""

    fun isValid(draft: Draft): Boolean {
        if (draft.originalMoves.isEmpty() || draft.originalMoves.size > MAX_MOVES ||
            draft.moves.size != draft.originalMoves.size ||
            draft.targetMinutes !in 5..120 || draft.level !in 0..2 ||
            draft.equipmentSelection !in 0..5 ||
            draft.additionalEquipment.size > TrainingEquipment.values().size
        ) return false
        val available = availableEquipment(draft.equipmentSelection, draft.additionalEquipment)
        if (draft.originalMoves.any { !WorkoutConstraintFilter.isAllowed(it, draft.constraints) ||
                !TrainingEquipmentAvailability.allows(it, available) } ||
            draft.moves.any { !WorkoutConstraintFilter.isAllowed(it, draft.constraints) ||
                !TrainingEquipmentAvailability.allows(it, available) }
        ) return false
        if (draft.substitutions.keys.any { it !in draft.originalMoves.indices }) return false
        for ((index, replacement) in draft.substitutions) {
            val approved = WorkoutMoveSubstitution.alternativesFor(
                draft.originalMoves[index], draft.constraints, available
            ).firstOrNull { it.name == replacement.name } ?: return false
            if (!TrainingEquipmentAvailability.allows(approved, available) ||
                draft.moves[index] != approved
            ) return false
        }
        for (index in draft.originalMoves.indices) {
            if (index !in draft.substitutions && draft.moves[index] != draft.originalMoves[index]) {
                return false
            }
        }
        return draft.estimatedSeconds == estimate(draft.moves, draft.level)
    }

    fun durationLabel(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        return if (seconds == 0) "$minutes phút" else "$minutes phút $seconds giây"
    }

    private fun availableEquipment(
        selection: Int,
        additional: Set<TrainingEquipment>
    ): Set<TrainingEquipment> =
        TrainingEquipmentAvailability.fromPreference(selection) + additional

    private fun estimate(moves: List<TrainingMove>, level: Int): Int {
        val active = moves.sumOf { WorkoutDurationPlanner.activeSeconds(it).toLong() }
        val rest = moves.sumOf { WorkoutDurationPlanner.prescribedRestSeconds(it).toLong() }
        val transitions = (moves.size - 1).coerceAtLeast(0).toLong() *
            WorkoutDurationPlanner.transitionAllowance(level)
        return (active + rest + transitions)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
