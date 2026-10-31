package com.chibialarm.app.rpg

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Explicit, user-initiated local export. No network, legacy preferences, coach chat,
 * or Gemini credentials are read. Caller writes JSON via Android's document picker.
 */
object RpgDataExport {
    suspend fun create(db: RpgDatabase): String = db.withTransaction {
        val dao = db.dao()
        val character = dao.character()
        val result = JSONObject()
            .put("format", "chibi-life-rpg-export")
            .put("schemaVersion", 1)
            .put("scope", "rpg-only")
            .put("exportedAt", Instant.now().toString())
            .put("character", if (character == null) JSONObject.NULL else JSONObject()
                .put("id", character.id)
                .put("name", character.name)
                .put("totalXp", character.totalXp)
                .put("coins", character.coins)
                .put("createdAt", character.createdAt))
        result.put("quests", JSONArray().apply {
            dao.quests().forEach { quest ->
                put(JSONObject().put("id", quest.id)
                    .put("templateId", quest.templateId)
                    .put("periodKey", quest.periodKey)
                    .put("title", quest.title)
                    .put("category", quest.category)
                    .put("target", quest.target)
                    .put("progress", quest.progress)
                    .put("rewardXp", quest.rewardXp)
                    .put("rewardCoins", quest.rewardCoins)
                    .put("state", quest.state))
            }
        })
        result.put("achievements", JSONArray().apply {
            dao.achievements().forEach { achievement ->
                put(JSONObject().put("id", achievement.id)
                    .put("title", achievement.title)
                    .put("progress", achievement.progress)
                    .put("target", achievement.target)
                    .put("unlockedAt", achievement.unlockedAt ?: JSONObject.NULL))
            }
        })
        result.put("streakDays", JSONArray().apply {
            dao.allStreakDays().forEach { day ->
                put(JSONObject().put("habitId", day.habitId)
                    .put("localDate", day.localDate)
                    .put("completed", day.completed)
                    .put("excused", day.excused))
            }
        })
        result.put("ledger", JSONArray().apply {
            dao.ledgerEntries().forEach { entry ->
                put(JSONObject().put("dedupeKey", entry.dedupeKey)
                    .put("questId", entry.questId)
                    .put("amount", entry.amount)
                    .put("createdAt", entry.createdAt))
            }
        })
        result.toString(2)
    }
}
