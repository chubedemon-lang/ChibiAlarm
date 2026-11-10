package com.chibialarm.app

/**
 * Configuration-change-only coaching state.
 *
 * Deliberately stores only coarse option indexes: never a transcript, raw request,
 * generated reply, account identifier, or long-lived health profile.
 */
data class FitnessCoachSessionSnapshot(
    val goal: Int? = null,
    val level: Int? = null,
    val energy: Int? = null,
    val minutes: Int? = null,
    val equipment: Int? = null,
    val impact: Int? = null
) {
    fun restore(): FitnessNaturalRequest.Result? {
        if (goal != null && goal !in 0..5) return null
        if (level != null && level !in 0..2) return null
        if (energy != null && energy !in 0..2) return null
        if (minutes != null && minutes !in 5..120) return null
        if (equipment != null && equipment !in 0..2) return null
        if (impact != null && impact !in 0..2) return null
        if (goal == null && level == null && energy == null && minutes == null &&
            equipment == null && impact == null) return null
        return FitnessNaturalRequest.Result(
            goal = goal,
            level = level,
            energy = energy,
            minutes = minutes,
            equipment = equipment,
            impact = impact,
            reply = "Đã giữ các lựa chọn trong phiên hiện tại. Hãy kiểm tra lại trước khi xem giáo án."
        )
    }

    companion object {
        /**
         * A pain/injury report remains sticky for this screen session, even if the
         * spinner is changed accidentally. Low-impact preference remains user-editable.
         */
        fun effectiveImpact(previousImpact: Int?, selectedImpact: Int): Int {
            require(selectedImpact in 0..2)
            require(previousImpact == null || previousImpact in 0..2)
            return if (previousImpact == 2 || selectedImpact == 2) 2 else selectedImpact
        }

        fun capture(
            result: FitnessNaturalRequest.Result?,
            selectedImpact: Int?
        ): FitnessCoachSessionSnapshot? {
            if (selectedImpact != null && selectedImpact !in 0..2) return null
            val retainedImpact = when {
                result?.impact == 2 || selectedImpact == 2 -> 2
                selectedImpact != null -> selectedImpact
                else -> result?.impact
            }
            val snapshot = FitnessCoachSessionSnapshot(
                goal = result?.goal,
                level = result?.level,
                energy = result?.energy,
                minutes = result?.minutes,
                equipment = result?.equipment,
                impact = retainedImpact
            )
            return snapshot.takeUnless {
                it.goal == null && it.level == null && it.energy == null &&
                    it.minutes == null && it.equipment == null && it.impact == null
            }
        }

        fun restore(snapshot: FitnessCoachSessionSnapshot?): FitnessNaturalRequest.Result? =
            snapshot?.restore()
    }
}
