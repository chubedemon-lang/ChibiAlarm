package com.chibialarm.app.rpg

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/** Durable local rewards: never duplicate XP or consume a charm on failure. */
@RunWith(RobolectricTestRunner::class)
class RpgGardenCharmBoostTest {
    private lateinit var db: RpgDatabase
    private val today = LocalDate.of(2026, 11, 2)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RpgDatabase::class.java
        ).build()
    }

    @After fun close() { db.close() }

    private suspend fun makeCharm() {
        val items = RpgInventoryEngine(db)
        assertTrue(items.grant("herb", 1, "boost:test:herb"))
        assertTrue(items.grant("star_fragment", 1, "boost:test:star"))
        assertTrue(items.craftGardenCharm("craft:garden_charm:boosttest"))
    }

    @Test fun missingCharmDoesNotCreateRewardOrLedger() = runBlocking {
        val engine = RpgEngine(db)
        engine.initialize(0, today)
        assertFalse(engine.activateDailyGardenCharm(today))
        assertFalse(db.dao().hasLedgerEntry("garden_charm:boost:$today"))
        assertEquals(0L, db.dao().character()!!.totalXp)
    }

    @Test fun activationDebitsOnceAndPaysOncePerLocalDay() = runBlocking {
        val engine = RpgEngine(db)
        engine.initialize(0, today)
        makeCharm()
        assertTrue(engine.activateDailyGardenCharm(today))
        assertFalse(engine.activateDailyGardenCharm(today))
        assertEquals(20L, db.dao().character()!!.totalXp)
        assertEquals(1L, db.dao().character()!!.coins)
        assertEquals(0L, db.dao().itemStack("garden_charm")!!.quantity)
        assertTrue(db.dao().hasLedgerEntry("garden_charm:boost:$today"))
    }

    @Test fun simultaneousTapsCannotDoubleSpendOrDoublePay() = runBlocking {
        val engine = RpgEngine(db)
        engine.initialize(0, today)
        makeCharm()
        val results = coroutineScope {
            (1..12).map {
                async(Dispatchers.Default) { engine.activateDailyGardenCharm(today) }
            }.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(20L, db.dao().character()!!.totalXp)
        assertEquals(0L, db.dao().itemStack("garden_charm")!!.quantity)
    }

    @Test fun overflowRollsBackCharmDebitAndLedgerReceipt() = runBlocking {
        val engine = RpgEngine(db)
        engine.initialize(0, today)
        makeCharm()
        val character = db.dao().character()!!
        db.dao().updateCharacter(character.copy(totalXp = Long.MAX_VALUE))
        try {
            engine.activateDailyGardenCharm(today)
            fail("Expected XP overflow")
        } catch (_: ArithmeticException) { }
        assertEquals(Long.MAX_VALUE, db.dao().character()!!.totalXp)
        assertEquals(1L, db.dao().itemStack("garden_charm")!!.quantity)
        assertFalse(db.dao().hasLedgerEntry("garden_charm:boost:$today"))
    }

    @Test fun nextDayRequiresAnotherCharm() = runBlocking {
        val engine = RpgEngine(db)
        engine.initialize(0, today)
        makeCharm()
        assertTrue(engine.activateDailyGardenCharm(today))
        assertFalse(engine.activateDailyGardenCharm(today.plusDays(1)))
        assertEquals(20L, db.dao().character()!!.totalXp)
    }
}
