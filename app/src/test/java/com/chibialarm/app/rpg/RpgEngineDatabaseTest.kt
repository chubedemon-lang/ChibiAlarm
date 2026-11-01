package com.chibialarm.app.rpg

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import org.json.JSONObject

/** Runs real Room transactions against Robolectric's in-memory SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RpgEngineDatabaseTest {
    private lateinit var database: RpgDatabase
    private val day = LocalDate.of(2026, 11, 1)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RpgDatabase::class.java
        ).build()
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun legacyXpImportRunsOnlyOnceAndUnlocksLevelMilestone() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(620, day)
        engine.initialize(620, day)
        assertEquals(620L, database.dao().character()!!.totalXp)
        val achievement = database.dao().achievements().first { it.id == "level_5" }
        assertEquals(5, achievement.progress)
        assertNotNull(achievement.unlockedAt)
    }

    @Test fun dailyQuestClaimIsAtomicAndCannotPayTwice() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val id = "growth:$day"
        assertTrue(engine.syncProgress(id, 1))
        assertFalse(engine.syncProgress(id, 1))
        assertTrue(engine.claimQuest(id))
        assertFalse(engine.claimQuest(id))
        assertEquals(25L, database.dao().character()!!.totalXp)
        assertEquals(5L, database.dao().character()!!.coins)
        assertEquals("CLAIMED", database.dao().quest(id)!!.state)
        assertNotNull(database.dao().achievements().first { it.id == "first_quest" }.unlockedAt)
    }

    @Test fun growthSyncHandlesUncheckedEntriesWithoutErasingLegacyData() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val today = day.toString()
        val yesterday = day.minusDays(1).toString()
        val first = listOf(
            StreakDay("growth:read", today, true),
            StreakDay("growth:read", yesterday, true)
        )
        assertEquals(2, engine.syncGrowth(first, day))
        val changed = listOf(
            StreakDay("growth:read", today, false),
            StreakDay("growth:read", yesterday, true)
        )
        assertEquals(1, engine.syncGrowth(changed, day))
        assertFalse(database.dao().streakDays("growth:read")
            .first { it.localDate == today }.completed)
        assertEquals(1, database.dao().achievements().first { it.id == "streak_3" }.progress)
    }
    @Test fun multipleHabitsUseLongestCurrentStreakAndRespectCorrections() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val history = (0L..4L).map {
            StreakDay("growth:reading", day.minusDays(it).toString(), true)
        } + (0L..1L).map {
            StreakDay("growth:exercise", day.minusDays(it).toString(), true)
        }
        assertEquals(5, engine.syncGrowth(history, day))
        val corrected = history.map {
            if (it.habitId == "growth:reading" && it.localDate == day.toString())
                it.copy(completed = false) else it
        }
        assertEquals(4, engine.syncGrowth(corrected, day))
        assertEquals(3, database.dao().achievements()
            .first { it.id == "streak_3" }.progress)
        assertEquals(4, database.dao().achievements()
            .first { it.id == "streak_7" }.progress)
    }

    @Test fun characterNameCanBeChangedWithoutResettingXp() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(620, day)
        assertTrue(engine.renameCharacter("  Chibi   Nam  "))
        assertEquals("Chibi Nam", database.dao().character()!!.name)
        assertEquals(620L, database.dao().character()!!.totalXp)
        assertFalse(engine.renameCharacter("Chibi Nam"))
        try {
            engine.renameCharacter(" ")
            fail("Blank name must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals("Chibi Nam", database.dao().character()!!.name)
        }
    }

    @Test fun checkboxUncheckRevokesUnclaimedQuestButNotPaidReward() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val id = "growth:$day"
        assertTrue(engine.syncCheckableProgress(id, 1))
        assertEquals("COMPLETED", database.dao().quest(id)!!.state)
        assertTrue(engine.syncCheckableProgress(id, 0))
        assertEquals("ACTIVE", database.dao().quest(id)!!.state)
        assertEquals(0, database.dao().quest(id)!!.progress)
        assertFalse(engine.claimQuest(id))
        assertTrue(engine.syncCheckableProgress(id, 1))
        assertTrue(engine.claimQuest(id))
        assertFalse(engine.syncCheckableProgress(id, 0))
        assertEquals("CLAIMED", database.dao().quest(id)!!.state)
        assertEquals(25L, database.dao().character()!!.totalXp)
    }

    @Test fun deletedWorkoutCannotBeClaimedUntilJournalHasAnEntryAgain() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val id = "fitness:$day"
        assertTrue(engine.syncCheckableProgress(id, 1))
        assertEquals("COMPLETED", database.dao().quest(id)!!.state)
        // Deleting a manually entered workout must invalidate an unpaid reward.
        assertTrue(engine.syncCheckableProgress(id, 0))
        assertEquals("ACTIVE", database.dao().quest(id)!!.state)
        assertEquals(0, database.dao().quest(id)!!.progress)
        assertFalse(engine.claimQuest(id))
        assertEquals(0L, database.dao().character()!!.totalXp)
        assertTrue(engine.syncCheckableProgress(id, 1))
        assertTrue(engine.claimQuest(id))
        assertFalse(engine.claimQuest(id))
        assertEquals(40L, database.dao().character()!!.totalXp)
        assertEquals(5L, database.dao().character()!!.coins)
        // Historical paid rewards stay immutable after a later journal deletion.
        assertFalse(engine.syncCheckableProgress(id, 0))
        assertEquals("CLAIMED", database.dao().quest(id)!!.state)
    }

    @Test fun exportIncludesRpgHistoryButNotPrivateGeminiData() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(200, day)
        assertTrue(engine.syncProgress("growth:$day", 1))
        assertTrue(engine.claimQuest("growth:$day"))
        engine.syncGrowth(listOf(StreakDay("growth:read", day.toString(), true)), day)
        val snapshot = JSONObject(RpgDataExport.create(database))
        assertEquals("chibi-life-rpg-export", snapshot.getString("format"))
        assertEquals("rpg-only", snapshot.getString("scope"))
        assertEquals(225L, snapshot.getJSONObject("character").getLong("totalXp"))
        assertEquals(4, snapshot.getJSONArray("quests").length())
        assertEquals(3, snapshot.getJSONArray("ledger").length())
        assertEquals(1, snapshot.getJSONArray("streakDays").length())
        assertFalse(snapshot.toString().contains("gemini_secure"))
        assertFalse(snapshot.toString().contains("coach_history"))
    }

    @Test fun reopeningDiskDatabasePreservesProgressAndDoesNotReimportLegacyXp() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val fileName = "rpg-reopen-regression.db"
        context.deleteDatabase(fileName)
        try {
            val first = Room.databaseBuilder(context, RpgDatabase::class.java, fileName).build()
            try {
                val engine = RpgEngine(first)
                engine.initialize(620, day)
                assertTrue(engine.syncCheckableProgress("routine:$day", 1))
                assertTrue(engine.claimQuest("routine:$day"))
                assertEquals(650L, first.dao().character()!!.totalXp)
            } finally {
                first.close()
            }
            val reopened = Room.databaseBuilder(context, RpgDatabase::class.java, fileName).build()
            try {
                val engine = RpgEngine(reopened)
                engine.initialize(620, day)
                assertEquals(650L, reopened.dao().character()!!.totalXp)
                assertEquals("CLAIMED", reopened.dao().quest("routine:$day")!!.state)
                assertFalse(engine.claimQuest("routine:$day"))
                assertEquals(1, reopened.dao().ledgerEntries()
                    .count { it.dedupeKey == "migration:life_v12:xp90:v1" })
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(fileName)
        }
    }

    @Test fun dashboardQueriesOnlyReturnCurrentPeriodWithoutDeletingHistory() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        engine.initialize(0, day.plusDays(1))
        val dao = database.dao()
        assertEquals(8, dao.quests().size)
        assertEquals(4, dao.questsForPeriod(day.toString()).size)
        assertEquals(4, dao.questsForPeriod(day.plusDays(1).toString()).size)
        assertTrue(dao.questsForPeriod(day.minusDays(1).toString()).isEmpty())
        assertTrue(dao.quests().any { it.periodKey == day.toString() })
    }

    @Test fun ledgerAggregateExcludesDailyCheckInsAndLegacyImport() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(100, day)
        val dao = database.dao()
        assertEquals(0, dao.questClaimCount())
        assertFalse(dao.hasLedgerEntry("daily_login:$day"))
        assertTrue(engine.claimDailyLogin(day))
        assertTrue(dao.hasLedgerEntry("daily_login:$day"))
        assertEquals(0, dao.questClaimCount())
        assertTrue(engine.syncCheckableProgress("routine:$day", 1))
        assertTrue(engine.claimQuest("routine:$day"))
        assertEquals(1, dao.questClaimCount())
        assertFalse(engine.claimQuest("routine:$day"))
        assertEquals(1, dao.questClaimCount())
    }

    @Test fun petMiniGameRewardsOnlyOncePerDayAndSurvivesEngineRecreation() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        assertTrue(engine.claimDailyPetGame(day))
        assertFalse(engine.claimDailyPetGame(day))
        assertFalse(RpgEngine(database).claimDailyPetGame(day))
        assertEquals(10L, database.dao().character()!!.totalXp)
        assertEquals(2L, database.dao().character()!!.coins)
        assertEquals(0, database.dao().questClaimCount())
        assertTrue(database.dao().hasLedgerEntry("pet_game:$day"))
        assertTrue(engine.claimDailyPetGame(day.plusDays(1)))
        assertEquals(20L, database.dao().character()!!.totalXp)
        assertEquals(4L, database.dao().character()!!.coins)
    }

    @Test fun dailyCheckInRewardsOncePerDateAndDoesNotCountAsQuest() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        assertTrue(engine.claimDailyLogin(day))
        assertFalse(engine.claimDailyLogin(day))
        assertTrue(engine.claimDailyLogin(day.plusDays(1)))
        val character = database.dao().character()!!
        assertEquals(30L, character.totalXp)
        assertEquals(6L, character.coins)
        assertEquals(2, database.dao().ledgerEntries()
            .count { it.dedupeKey.startsWith("daily_login:") })
        assertNull(database.dao().achievements().first { it.id == "first_quest" }.unlockedAt)
    }

    @Test fun newLevelMilestoneBackfillsFromLegacyXpWithoutReimport() = runBlocking {
        val engine = RpgEngine(database)
        val xpForLevelTen = (1..9).sumOf { RpgRules.xpToNext(it) }.toInt()
        engine.initialize(xpForLevelTen, day)
        engine.initialize(xpForLevelTen, day)
        val milestone = database.dao().achievements().first { it.id == "level_10" }
        assertEquals(10, milestone.progress)
        assertNotNull(milestone.unlockedAt)
        assertEquals(xpForLevelTen.toLong(), database.dao().character()!!.totalXp)
    }

    @Test fun sevenDayStreakUnlocksMilestone() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val days = (0L..6L).map {
            StreakDay("growth:reading", day.minusDays(it).toString(), completed = true)
        }
        assertEquals(7, engine.syncGrowth(days, day))
        val milestone = database.dao().achievements().first { it.id == "streak_7" }
        assertEquals(7, milestone.progress)
        assertNotNull(milestone.unlockedAt)
    }

    @Test fun tenDistinctQuestClaimsUnlockMilestoneAndDuplicateClaimsDoNotCount() = runBlocking {
        val engine = RpgEngine(database)
        repeat(10) { index ->
            val date = day.plusDays(index.toLong())
            engine.initialize(0, date)
            val id = "routine:$date"
            assertTrue(engine.syncCheckableProgress(id, 1))
            assertTrue(engine.claimQuest(id))
            assertFalse(engine.claimQuest(id))
        }
        val milestone = database.dao().achievements().first { it.id == "quest_10" }
        assertEquals(10, milestone.progress)
        assertNotNull(milestone.unlockedAt)
        assertEquals(10, database.dao().ledgerEntries()
            .count { it.dedupeKey.startsWith("quest:") })
    }

    @Test fun repeatedGrowthRefreshIsIdempotentAndUncheckedDayIsReconciled() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val yesterday = StreakDay("growth:read", day.minusDays(1).toString(), true)
        val today = StreakDay("growth:read", day.toString(), true)
        assertEquals(2, engine.syncGrowth(listOf(yesterday, today), day))
        assertEquals(2, engine.syncGrowth(listOf(yesterday, today), day))
        assertEquals(2, database.dao().allStreakDays().size)
        assertEquals(1, engine.syncGrowth(listOf(yesterday, today.copy(completed = false)), day))
        assertFalse(database.dao().allStreakDays().first { it.localDate == day.toString() }.completed)
        assertEquals(2, database.dao().allStreakDays().size)
    }

    @Test fun removingAllActiveGrowthHabitsResetsUnpaidStreakProgressButPreservesHistory() = runBlocking {
        val engine = RpgEngine(database)
        engine.initialize(0, day)
        val days = listOf(
            StreakDay("growth:read", day.toString(), true),
            StreakDay("growth:read", day.minusDays(1).toString(), true)
        )
        assertEquals(2, engine.syncGrowth(days, day))
        assertEquals(2, database.dao().achievements().first { it.id == "streak_3" }.progress)
        assertEquals(0, engine.syncGrowth(emptyList(), day))
        assertEquals(0, database.dao().achievements().first { it.id == "streak_3" }.progress)
        // Historical entries are retained for recovery and export.
        assertEquals(2, database.dao().allStreakDays().size)
    }

}
