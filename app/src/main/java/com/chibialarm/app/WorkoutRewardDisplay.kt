package com.chibialarm.app

import com.chibialarm.app.rpg.RpgRules

/** Small, non-sensitive snapshot used only to render the post-workout RPG reward. */
data class WorkoutRewardDisplay(
    val awardedXp: Int,
    val totalXp: Long,
    val level: Int,
    val xpInLevel: Long,
    val xpRequired: Long,
    val maxLevelReached: Boolean
)

object WorkoutRewardDisplayFactory {
    /** Build a bounded display model from the committed RPG balance. */
    fun create(awardedXp: Int, totalXp: Long): WorkoutRewardDisplay {
        require(awardedXp in 0..30) { "Workout reward must respect the per-session cap" }
        require(totalXp >= 0L) { "XP balance cannot be negative" }

        val level = RpgRules.level(totalXp)
        var xpSpentBeforeLevel = 0L
        for (completedLevel in 1 until level) {
            xpSpentBeforeLevel = Math.addExact(
                xpSpentBeforeLevel,
                RpgRules.xpToNext(completedLevel)
            )
        }
        val xpRequired = RpgRules.xpToNext(level)
        val xpInLevel = (totalXp - xpSpentBeforeLevel).coerceIn(0L, xpRequired)

        return WorkoutRewardDisplay(
            awardedXp = awardedXp,
            totalXp = totalXp,
            level = level,
            xpInLevel = xpInLevel,
            xpRequired = xpRequired,
            maxLevelReached = level >= 1000
        )
    }
}
