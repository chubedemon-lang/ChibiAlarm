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

@RunWith(RobolectricTestRunner::class)
class RpgInventoryDatabaseTest {
    private lateinit var db: RpgDatabase
    private val today = LocalDate.of(2026, 11, 1)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), RpgDatabase::class.java
        ).build()
    }

    @After fun close() { db.close() }

    @Test fun starterMapIsSeededOnceWithoutResettingOldXp() = runBlocking {
        val engine = RpgEngine(db)
        engine.initialize(500, today)
        engine.initialize(500, today)
        assertEquals(500L, db.dao().character()!!.totalXp)
        assertEquals(1L, db.dao().itemStack("camp_map")!!.quantity)
        assertEquals(1, db.dao().itemReceiptCount("starter:camp_map:v1"))
        assertEquals(1, db.dao().itemStacks().size)
    }

    @Test fun grantsAreDurableAndIdempotentAndCannotGoNegative() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        assertTrue(inventory.grant("star_fragment", 3, "quest:2026-11-01:star"))
        assertFalse(inventory.grant("star_fragment", 3, "quest:2026-11-01:star"))
        assertEquals(3L, db.dao().itemStack("star_fragment")!!.quantity)
        assertFalse(inventory.consume("star_fragment", 4))
        assertTrue(inventory.consume("star_fragment", 2))
        assertEquals(1L, db.dao().itemStack("star_fragment")!!.quantity)
        assertTrue(inventory.consume("star_fragment", 1))
        assertFalse(inventory.consume("star_fragment", 1))
        assertTrue(inventory.stacks().isEmpty())
        assertEquals(0L, db.dao().itemStack("star_fragment")!!.quantity)
        assertFalse(inventory.grant("herb", 2, "quest:2026-11-01:star"))
        assertNull(db.dao().itemStack("herb"))
    }

    @Test fun rejectsInvalidIdsAndAmountsWithoutWritingReceipts() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        for (id in listOf("", "../path", "Gemini_Key", "bad item", "a".repeat(65))) {
            try { inventory.grant(id, 1, "test:key"); fail("Accepted invalid item ID") }
            catch (_: IllegalArgumentException) { }
        }
        for (amount in listOf(0L, -1L, 1_000_001L)) {
            try { inventory.grant("herb", amount, "test:key"); fail("Accepted invalid grant") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, db.dao().itemReceiptCount("test:key"))
        assertNull(db.dao().itemStack("herb"))
    }

    @Test fun concurrentSameReceiptCanOnlyGrantOnce() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        val results = coroutineScope {
            (1..12).map {
                async(Dispatchers.Default) {
                    inventory.grant("star_fragment", 7, "daily:unique:2026-11-01")
                }
            }.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(7L, db.dao().itemStack("star_fragment")!!.quantity)
    }

    @Test fun itemBalancesSurviveDiskReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "rpg-inventory-reopen.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, RpgDatabase::class.java, name)
                .addMigrations(RpgDatabase.MIGRATION_1_2).build()
            try {
                RpgEngine(first).initialize(20, today)
                assertTrue(RpgInventoryEngine(first).grant("herb", 5, "explore:first"))
                assertTrue(RpgInventoryEngine(first).consume("herb", 2))
            } finally { first.close() }
            val second = Room.databaseBuilder(context, RpgDatabase::class.java, name)
                .addMigrations(RpgDatabase.MIGRATION_1_2).build()
            try {
                RpgEngine(second).initialize(20, today)
                assertEquals(20L, second.dao().character()!!.totalXp)
                assertEquals(3L, second.dao().itemStack("herb")!!.quantity)
                assertEquals(1L, second.dao().itemStack("camp_map")!!.quantity)
                assertFalse(RpgInventoryEngine(second).grant("herb", 5, "explore:first"))
            } finally { second.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun gardenForageIsDailyIdempotentAndDeterministic() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        val tomorrow = today.plusDays(1)
        val first = RpgGardenRules.dailyFind(today)
        val second = RpgGardenRules.dailyFind(tomorrow)
        assertNotEquals(first, second)
        assertEquals(first, inventory.forageGarden(today))
        assertNull(inventory.forageGarden(today))
        assertEquals(second, inventory.forageGarden(tomorrow))
        assertNull(inventory.forageGarden(tomorrow))
        assertEquals(1L, db.dao().itemStack(first)!!.quantity)
        assertEquals(1L, db.dao().itemStack(second)!!.quantity)
        assertEquals(1, db.dao().itemReceiptCount("garden:forage:$today"))
    }

    @Test fun craftRequiresBothIngredientsAndDoesNotReserveFailedReceipt() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        val key = "craft:garden_charm:attempt1"
        assertFalse(inventory.craftGardenCharm(key))
        assertEquals(0, db.dao().itemReceiptCount(key))
        assertTrue(inventory.grant("herb", 2L, "test:herbs"))
        assertFalse(inventory.craftGardenCharm(key))
        assertEquals(2L, db.dao().itemStack("herb")!!.quantity)
        assertEquals(0, db.dao().itemReceiptCount(key))
        assertTrue(inventory.grant("star_fragment", 1L, "test:stars"))
        assertTrue(inventory.craftGardenCharm(key))
        assertFalse(inventory.craftGardenCharm(key))
        assertEquals(1, db.dao().itemReceiptCount(key))
        assertEquals(1L, db.dao().itemStack("herb")!!.quantity)
        assertEquals(0L, db.dao().itemStack("star_fragment")!!.quantity)
        assertEquals(1L, db.dao().itemStack("garden_charm")!!.quantity)
        assertFalse(inventory.craftGardenCharm("craft:garden_charm:attempt2"))
        assertEquals(1L, db.dao().itemStack("herb")!!.quantity)
    }

    @Test fun concurrentCraftsCannotSpendSameIngredientsTwice() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        assertTrue(inventory.grant("herb", 1L, "test:herb"))
        assertTrue(inventory.grant("star_fragment", 1L, "test:star"))
        val results = coroutineScope {
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    inventory.craftGardenCharm("craft:garden_charm:attempt$i")
                }
            }.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(1L, db.dao().itemStack("garden_charm")!!.quantity)
        assertEquals(0L, db.dao().itemStack("herb")!!.quantity)
        assertEquals(0L, db.dao().itemStack("star_fragment")!!.quantity)
    }

    @Test fun grantOverflowRollsBackReceiptAndPreservesBalance() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        val dao = db.dao()
        dao.insertItemStack(RpgItemStack("herb", Long.MAX_VALUE, 1L))
        try {
            inventory.grant("herb", 1L, "test:overflow:grant")
            fail("Expected quantity overflow")
        } catch (_: ArithmeticException) { }
        assertEquals(Long.MAX_VALUE, dao.itemStack("herb")!!.quantity)
        assertEquals(0, dao.itemReceiptCount("test:overflow:grant"))
    }

    @Test fun craftOverflowDoesNotSpendIngredientsOrReserveReceipt() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        val dao = db.dao()
        dao.insertItemStack(RpgItemStack("garden_charm", Long.MAX_VALUE, 1L))
        assertTrue(inventory.grant("herb", 1L, "test:craft:herb"))
        assertTrue(inventory.grant("star_fragment", 1L, "test:craft:star"))
        val receipt = "craft:garden_charm:overflow"
        try {
            inventory.craftGardenCharm(receipt)
            fail("Expected crafted stack overflow")
        } catch (_: ArithmeticException) { }
        assertEquals(1L, dao.itemStack("herb")!!.quantity)
        assertEquals(1L, dao.itemStack("star_fragment")!!.quantity)
        assertEquals(Long.MAX_VALUE, dao.itemStack("garden_charm")!!.quantity)
        assertEquals(0, dao.itemReceiptCount(receipt))
    }

    @Test fun invalidCraftKeyCannotConsumeIngredients() = runBlocking {
        val inventory = RpgInventoryEngine(db)
        assertTrue(inventory.grant("herb", 1L, "test:herb"))
        assertTrue(inventory.grant("star_fragment", 1L, "test:star"))
        for (key in listOf("other:receipt", "craft:garden_charm:BAD", "craft:garden_charm:../")) {
            try { inventory.craftGardenCharm(key); fail("Accepted invalid craft key") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(1L, db.dao().itemStack("herb")!!.quantity)
        assertEquals(1L, db.dao().itemStack("star_fragment")!!.quantity)
    }

}
