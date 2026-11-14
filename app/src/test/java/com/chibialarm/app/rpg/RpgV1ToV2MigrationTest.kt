package com.chibialarm.app.rpg

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/** Reconstructs the released R1 Room-v1 table contract before opening Room v2. */
@RunWith(RobolectricTestRunner::class)
class RpgV1ToV2MigrationTest {
    @Test fun upgradesPopulatedV1DatabaseWithoutDeletingCharacterQuestsOrLedger() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "rpg-v1-v2-migration.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL("CREATE TABLE IF NOT EXISTS `rpg_character` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `totalXp` INTEGER NOT NULL, `coins` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                old.execSQL("CREATE TABLE IF NOT EXISTS `rpg_quest` (`id` TEXT NOT NULL, `templateId` TEXT NOT NULL, `periodKey` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `target` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `rewardXp` INTEGER NOT NULL, `rewardCoins` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`id`))")
                old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rpg_quest_templateId_periodKey` ON `rpg_quest` (`templateId`, `periodKey`)")
                old.execSQL("CREATE TABLE IF NOT EXISTS `rpg_xp_ledger` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dedupeKey` TEXT NOT NULL, `questId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rpg_xp_ledger_dedupeKey` ON `rpg_xp_ledger` (`dedupeKey`)")
                old.execSQL("CREATE TABLE IF NOT EXISTS `rpg_achievement` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `progress` INTEGER NOT NULL, `target` INTEGER NOT NULL, `unlockedAt` INTEGER, PRIMARY KEY(`id`))")
                old.execSQL("CREATE TABLE IF NOT EXISTS `rpg_streak_day` (`habitId` TEXT NOT NULL, `localDate` TEXT NOT NULL, `completed` INTEGER NOT NULL, `excused` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `localDate`))")
                old.execSQL("INSERT INTO rpg_character VALUES ('local-player', 'Chibi cũ', 725, 18, 1000)")
                old.execSQL("INSERT INTO rpg_quest VALUES ('growth:old', 'daily_growth', '2026-10-31', 'Đọc sách', 'GROWTH', 1, 1, 25, 5, 'CLAIMED')")
                old.execSQL("INSERT INTO rpg_xp_ledger (dedupeKey, questId, amount, createdAt) VALUES ('migration:life_v12:xp90:v1', 'legacy', 700, 1000)")
                old.execSQL("INSERT INTO rpg_xp_ledger (dedupeKey, questId, amount, createdAt) VALUES ('quest:growth:old:2026-10-31', 'growth:old', 25, 1001)")
                old.execSQL("INSERT INTO rpg_streak_day VALUES ('growth:reading', '2026-10-31', 1, 0)")
                old.version = 1
            }
            val upgraded = Room.databaseBuilder(context, RpgDatabase::class.java, name)
                .addMigrations(RpgDatabase.MIGRATION_1_2).build()
            try {
                val dao = upgraded.dao()
                assertEquals("Chibi cũ", dao.character()!!.name)
                assertEquals(725L, dao.character()!!.totalXp)
                assertEquals(18L, dao.character()!!.coins)
                assertEquals("CLAIMED", dao.quest("growth:old")!!.state)
                assertEquals(2, dao.ledgerEntries().size)
                assertEquals(1, dao.allStreakDays().size)
                assertTrue(dao.itemStacks().isEmpty())
                RpgEngine(upgraded).initialize(700, LocalDate.of(2026, 11, 1))
                assertEquals(725L, dao.character()!!.totalXp)
                assertEquals(1L, dao.itemStack("camp_map")!!.quantity)
                assertEquals(1, dao.ledgerEntries().count {
                    it.dedupeKey == "migration:life_v12:xp90:v1"
                })
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }
}
