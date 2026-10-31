package com.chibialarm.app.rpg

import androidx.room.withTransaction
import java.time.LocalDate

/** Pure RPG rules: deterministic, independent of Gemini and Android UI. */
object RpgRules {
    fun xpToNext(level: Int): Long {
        require(level >= 1)
        val n = (level - 1).toLong()
        return 100L + 25L * n + 5L * n * n
    }

    fun level(totalXp: Long): Int {
        require(totalXp >= 0)
        var remaining = totalXp
        var level = 1
        while (level < 1000 && remaining >= xpToNext(level)) {
            remaining -= xpToNext(level)
            level++
        }
        return level
    }

    fun streak(days: List<StreakDay>, today: LocalDate): Int {
        val byDate = days.associateBy { it.localDate }
        var date = today
        if (byDate[date.toString()]?.let { it.completed || it.excused } != true) date = date.minusDays(1)
        var count = 0
        while (true) {
            val day = byDate[date.toString()] ?: break
            if (!day.completed && !day.excused) break
            if (day.completed) count++
            date = date.minusDays(1)
        }
        return count
    }
}

class RpgEngine(private val db: RpgDatabase) {
    /** Advance an active quest, capped at its target. Rewards require a separate claim. */
    suspend fun recordProgress(questId: String, amount: Int = 1): Boolean = db.withTransaction {
        require(amount > 0)
        val dao = db.dao()
        val quest = dao.quest(questId) ?: return@withTransaction false
        if (quest.state != "ACTIVE" || quest.target <= 0) return@withTransaction false
        val next = (quest.progress.toLong() + amount).coerceAtMost(quest.target.toLong()).toInt()
        if (next == quest.progress) return@withTransaction false
        dao.updateQuest(quest.copy(
            progress = next,
            state = if (next >= quest.target) "COMPLETED" else "ACTIVE"
        ))
        true
    }

    /** A single transaction prevents duplicate rewards and partial balance writes. */
    suspend fun claimQuest(questId: String): Boolean = db.withTransaction {
        val dao = db.dao()
        val quest = dao.quest(questId) ?: return@withTransaction false
        if (quest.state != "COMPLETED" || quest.progress < quest.target) return@withTransaction false
        val key = "quest:${quest.id}:${quest.periodKey}"
        if (dao.insertLedger(XpLedger(dedupeKey = key, questId = quest.id, amount = quest.rewardXp)) == -1L) {
            return@withTransaction false
        }
        val character = dao.character() ?: RpgCharacter().also { dao.insertCharacter(it) }
        dao.updateCharacter(character.copy(
            totalXp = character.totalXp + quest.rewardXp.coerceAtLeast(0),
            coins = character.coins + quest.rewardCoins.coerceAtLeast(0)
        ))
        dao.updateQuest(quest.copy(state = "CLAIMED"))
        true
    }
}
