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
}
