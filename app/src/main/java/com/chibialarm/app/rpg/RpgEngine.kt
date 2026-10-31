package com.chibialarm.app.rpg

import androidx.room.withTransaction
import java.time.LocalDate

/** Deterministic, offline RPG rules. */
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

    fun progressNext(current: Int, target: Int, amount: Int): Int {
        require(target > 0 && amount > 0)
        return (current.toLong().coerceAtLeast(0L) + amount)
            .coerceAtMost(target.toLong()).toInt()
    }

    fun progressObserved(current: Int, target: Int, observed: Int): Int {
        require(target > 0 && observed >= 0)
        return maxOf(current.coerceAtLeast(0), observed.coerceAtMost(target))
            .coerceAtMost(target)
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
    /** Reconcile existing and newly added milestones from durable ledger state. */
    private suspend fun updateMilestones(dao: RpgDao) {
        val level = RpgRules.level((dao.character() ?: RpgCharacter()).totalXp)
        val questClaims = dao.ledgerEntries().count { it.dedupeKey.startsWith("quest:") }
        val now = System.currentTimeMillis()
        dao.achievements().forEach { achievement ->
            if (achievement.unlockedAt != null) return@forEach
            val observed = when (achievement.id) {
                "first_quest", "quest_10" -> questClaims
                "level_5", "level_10" -> level
                else -> return@forEach
            }
            val progress = observed.coerceAtMost(achievement.target)
            if (progress != achievement.progress || observed >= achievement.target) {
                dao.updateAchievement(achievement.copy(
                    progress = progress,
                    unlockedAt = if (observed >= achievement.target) now else null
                ))
            }
        }
    }

    /** Optional offline daily check-in. Deduplicated per local calendar day. */
    suspend fun claimDailyLogin(today: LocalDate = LocalDate.now()): Boolean = db.withTransaction {
        val dao = db.dao()
        val xpReward = 15
        val coinReward = 3
        if (dao.insertLedger(XpLedger(dedupeKey = "daily_login:$today",
                questId = "daily-login", amount = xpReward)) == -1L) return@withTransaction false
        val character = dao.character() ?: RpgCharacter().also { dao.insertCharacter(it) }
        val nextXp = character.totalXp + xpReward
        val nextCoins = character.coins + coinReward
        require(nextXp >= character.totalXp && nextCoins >= character.coins) { "Reward overflow" }
        dao.updateCharacter(character.copy(totalXp = nextXp, coins = nextCoins))
        updateMilestones(dao)
        true
    }

    /** User-editable local character name; never changes character identity or rewards. */
    suspend fun renameCharacter(name: String): Boolean = db.withTransaction {
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        require(normalized.length in 2..24 && normalized.none { Character.isISOControl(it) }) {
            "Tên nhân vật phải có 2–24 ký tự hợp lệ"
        }
        val dao = db.dao()
        val character = dao.character() ?: return@withTransaction false
        if (character.name == normalized) return@withTransaction false
        dao.updateCharacter(character.copy(name = normalized))
        true
    }

    /**
     * Copies the legacy 90-day XP snapshot exactly once. Existing preferences remain untouched.
     * This is a one-time starting balance, NOT a live sync with LifeData's rolling XP counter.
     */
    suspend fun initialize(legacyXp: Int, today: LocalDate = LocalDate.now()) = db.withTransaction {
        val dao = db.dao()
        if (dao.character() == null) dao.insertCharacter(RpgCharacter())
        val amount = legacyXp.coerceAtLeast(0)
        if (dao.insertLedger(XpLedger(dedupeKey = "migration:life_v12:xp90:v1",
                questId = "legacy", amount = amount)) != -1L) {
            val character = dao.character() ?: error("Character not created")
            dao.updateCharacter(character.copy(totalXp = character.totalXp + amount.toLong()))
        }
        val day = today.toString()
        listOf(
            RpgQuest("routine:$day", "daily_routine", day, "Hoàn thành 1 hoạt động trong lịch", "ROUTINE", 1, 0, 30, 5),
            RpgQuest("focus:$day", "daily_focus", day, "Tập trung 2 phiên", "FOCUS", 2, 0, 50, 10),
            RpgQuest("fitness:$day", "daily_fitness", day, "Vận động 1 lần", "FITNESS", 1, 0, 40, 5),
            RpgQuest("growth:$day", "daily_growth", day, "Hoàn thành 1 thói quen", "GROWTH", 1, 0, 25, 5)
        ).forEach { dao.insertQuest(it) }
        listOf(
            RpgAchievement("first_quest", "Bước chân đầu tiên", target = 1),
            RpgAchievement("level_5", "Chibi cấp 5", target = 5),
            RpgAchievement("streak_3", "Kiên trì 3 ngày", target = 3),
            RpgAchievement("streak_7", "Kiên trì 7 ngày", target = 7),
            RpgAchievement("quest_10", "Hoàn thành 10 nhiệm vụ", target = 10),
            RpgAchievement("level_10", "Chibi cấp 10", target = 10)
        ).forEach { dao.insertAchievement(it) }
        // Backfill achievements on upgrade without resetting any previously unlocked milestone.
        updateMilestones(dao)
    }

    /**
     * Non-destructive Growth import and incremental sync.
     * Existing preferences are never modified. Sync both checked and unchecked entries so
     * changing a habit back to unchecked cannot leave a phantom streak in the RPG database.
     * The migration ledger is a historical marker, not a gate on later check-ins.
     */
    suspend fun syncGrowth(days: List<StreakDay>, today: LocalDate = LocalDate.now()) =
        db.withTransaction {
            val dao = db.dao()
            dao.insertLedger(XpLedger(dedupeKey = "migration:growth:v1",
                questId = "legacy-growth", amount = 0))
            days.distinctBy { it.habitId to it.localDate }.forEach { dao.recordStreak(it) }
            var longestCurrent = 0
            for (habitId in days.map { it.habitId }.distinct()) {
                val current = RpgRules.streak(dao.streakDays(habitId), today)
                if (current > longestCurrent) longestCurrent = current
            }
            dao.achievements().filter { it.id == "streak_3" || it.id == "streak_7" }
                .forEach { achievement ->
                    if (achievement.unlockedAt == null) {
                        dao.updateAchievement(achievement.copy(
                            progress = longestCurrent.coerceAtMost(achievement.target),
                            unlockedAt = if (longestCurrent >= achievement.target)
                                System.currentTimeMillis() else null
                        ))
                    }
                }
            longestCurrent
        }

    /** Advance an active quest; reaching its target does not auto-award XP. */
    suspend fun recordProgress(questId: String, amount: Int = 1): Boolean = db.withTransaction {
        require(amount > 0)
        val dao = db.dao()
        val quest = dao.quest(questId) ?: return@withTransaction false
        if (quest.state != "ACTIVE" || quest.target <= 0) return@withTransaction false
        val next = RpgRules.progressNext(quest.progress, quest.target, amount)
        if (next == quest.progress) return@withTransaction false
        dao.updateQuest(quest.copy(progress = next,
            state = if (next >= quest.target) "COMPLETED" else "ACTIVE"))
        true
    }

    /**
     * Checkbox-driven observations can be undone before claiming the reward.
     * Claimed quests remain immutable: a later uncheck never withdraws paid XP.
     */
    suspend fun syncCheckableProgress(questId: String, observed: Int): Boolean = db.withTransaction {
        require(observed >= 0)
        val dao = db.dao()
        val quest = dao.quest(questId) ?: return@withTransaction false
        if (quest.state == "CLAIMED" || quest.target <= 0) return@withTransaction false
        val next = observed.coerceAtMost(quest.target)
        val nextState = if (next >= quest.target) "COMPLETED" else "ACTIVE"
        if (next == quest.progress && nextState == quest.state) return@withTransaction false
        dao.updateQuest(quest.copy(progress = next, state = nextState))
        true
    }

    /** Sync cumulative progress as an absolute count; repeated reads cannot duplicate credit. */
    suspend fun syncProgress(questId: String, observed: Int): Boolean = db.withTransaction {
        require(observed >= 0)
        val dao = db.dao()
        val quest = dao.quest(questId) ?: return@withTransaction false
        if (quest.state != "ACTIVE" || quest.target <= 0) return@withTransaction false
        val next = RpgRules.progressObserved(quest.progress, quest.target, observed)
        if (next == quest.progress) return@withTransaction false
        dao.updateQuest(quest.copy(progress = next,
            state = if (next >= quest.target) "COMPLETED" else "ACTIVE"))
        true
    }

    /** Atomic and idempotent quest claim, including achievements. */
    suspend fun claimQuest(questId: String): Boolean = db.withTransaction {
        val dao = db.dao()
        val quest = dao.quest(questId) ?: return@withTransaction false
        if (quest.state != "COMPLETED" || quest.target <= 0 ||
            quest.progress < quest.target || quest.rewardXp < 0 || quest.rewardCoins < 0)
            return@withTransaction false
        val key = "quest:${quest.id}:${quest.periodKey}"
        if (dao.insertLedger(XpLedger(dedupeKey = key, questId = quest.id,
                amount = quest.rewardXp)) == -1L) return@withTransaction false
        val character = dao.character() ?: RpgCharacter().also { dao.insertCharacter(it) }
        val xp = character.totalXp + quest.rewardXp.toLong()
        val coins = character.coins + quest.rewardCoins.toLong()
        require(xp >= character.totalXp && coins >= character.coins) { "Reward overflow" }
        dao.updateCharacter(character.copy(totalXp = xp, coins = coins))
        dao.updateQuest(quest.copy(state = "CLAIMED"))
        updateMilestones(dao)
        true
    }
}
