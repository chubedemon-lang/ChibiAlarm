package com.chibialarm.app

import android.content.Context
import androidx.room.Room
import com.chibialarm.app.rpg.RpgDatabase
import com.chibialarm.app.rpg.RpgEngine
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Deleting a Growth habit must not silently keep paying its RPG daily quest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GrowthHabitsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("growth", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }
    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun missingCustomListUsesSameDefaultsAsGrowthScreen() {
        assertEquals(GrowthHabits.defaultNames.toSet(), GrowthHabits.activeNames(context))
        prefs.edit().putBoolean("done:2026-11-01:Đọc sách", true).commit()
        assertEquals(1, GrowthHabits.activeHistory(context).size)
    }

    @Test fun deletedHabitKeepsLegacyCheckinButNoLongerCountsTowardRpg() {
        prefs.edit().putString("habits", "Đọc sách\nUống nước")
            .putBoolean("done:2026-11-01:Đọc sách", true)
            .putBoolean("done:2026-11-01:Uống nước", false).commit()
        assertEquals(1, GrowthHabits.activeHistory(context).count { it.completed })
        // GrowthActivity removes the name, not the historical done:* preference.
        prefs.edit().putString("habits", "Uống nước").commit()
        assertTrue(prefs.getBoolean("done:2026-11-01:Đọc sách", false))
        val active = GrowthHabits.activeHistory(context)
        assertEquals(1, active.size)
        assertEquals("growth:Uống nước", active.single().habitId)
        assertFalse(active.single().completed)
    }

    @Test fun emptyHabitListMeansNoActiveHistory() {
        prefs.edit().putString("habits", "")
            .putBoolean("done:2026-11-01:Đọc sách", true).commit()
        assertTrue(GrowthHabits.activeNames(context).isEmpty())
        assertTrue(GrowthHabits.activeHistory(context).isEmpty())
    }

    @Test fun malformedDateAndUnrelatedPreferencesAreIgnored() {
        prefs.edit().putString("habits", "Đọc sách")
            .putBoolean("done:not-a-date:Đọc sách", true)
            .putBoolean("done:2026-11-01:Đọc sách", true)
            .putBoolean("other:2026-11-01:Đọc sách", true).commit()
        assertEquals(1, GrowthHabits.activeHistory(context).size)
    }

    @Test fun deletingCompletedHabitRevokesUnclaimedDailyGrowthReward() = runBlocking {
        val date = LocalDate.of(2026, 11, 1)
        val db = Room.inMemoryDatabaseBuilder(context, RpgDatabase::class.java).build()
        try {
            val engine = RpgEngine(db)
            engine.initialize(0, date)
            val id = "growth:$date"
            prefs.edit().putString("habits", "Đọc sách")
                .putBoolean("done:2026-11-01:Đọc sách", true).commit()
            assertTrue(engine.syncCheckableProgress(id,
                GrowthHabits.activeHistory(context).count { it.localDate == date.toString() && it.completed }))
            assertEquals("COMPLETED", db.dao().quest(id)!!.state)
            prefs.edit().putString("habits", "").commit()
            assertTrue(engine.syncCheckableProgress(id,
                GrowthHabits.activeHistory(context).count { it.localDate == date.toString() && it.completed }))
            assertEquals("ACTIVE", db.dao().quest(id)!!.state)
            assertFalse(engine.claimQuest(id))
            assertEquals(0L, db.dao().character()!!.totalXp)
            assertTrue(prefs.getBoolean("done:2026-11-01:Đọc sách", false))
        } finally {
            db.close()
        }
    }


    @Test fun malformedLegacyHabitListFailsClosedWithoutOverwritingOriginal() {
        prefs.edit().putInt("habits", 42)
            .putBoolean("done:2026-11-01:Đọc sách", true).commit()
        assertTrue(GrowthHabits.hasMalformedHabitList(context))
        assertTrue(GrowthHabits.habitNames(context).isEmpty())
        assertTrue(GrowthHabits.activeHistory(context).isEmpty())
        assertEquals(42, prefs.getInt("habits", -1)) // Read never mutates old data.
    }

    @Test fun repairingMalformedHabitListBacksUpOriginalAndKeepsCheckins() {
        prefs.edit().putInt("habits", 42)
            .putBoolean("done:2026-11-01:Đọc sách", true).commit()
        assertTrue(GrowthHabits.saveHabits(context, listOf("Đọc sách", "Uống nước")))
        assertFalse(GrowthHabits.hasMalformedHabitList(context))
        assertEquals("42", prefs.getString("habits_corrupt_backup", null))
        assertEquals(listOf("Đọc sách", "Uống nước"), GrowthHabits.habitNames(context))
        assertEquals(1, GrowthHabits.activeHistory(context).count { it.completed })
        // Never overwrite the first backup when the user edits the list again.
        assertTrue(GrowthHabits.saveHabits(context, listOf("Uống nước")))
        assertEquals("42", prefs.getString("habits_corrupt_backup", null))
        assertTrue(prefs.getBoolean("done:2026-11-01:Đọc sách", false))
    }

    @Test fun malformedCheckinIsNotAwardedAndCanBeCorrectedDurably() {
        prefs.edit().putString("habits", "Đọc sách")
            .putString("done:2026-11-01:Đọc sách", "invalid").commit()
        assertFalse(GrowthHabits.isDone(context, "Đọc sách", "2026-11-01"))
        assertTrue(GrowthHabits.activeHistory(context).isEmpty())
        assertEquals("invalid", prefs.getString("done:2026-11-01:Đọc sách", null))
        assertTrue(GrowthHabits.setDone(context, "Đọc sách", "2026-11-01", true))
        assertTrue(GrowthHabits.isDone(context, "Đọc sách", "2026-11-01"))
        assertEquals(1, GrowthHabits.activeHistory(context).count { it.completed })
        assertTrue(GrowthHabits.setDone(context, "Đọc sách", "2026-11-01", false))
        assertFalse(GrowthHabits.isDone(context, "Đọc sách", "2026-11-01"))
        assertEquals(0, GrowthHabits.activeHistory(context).count { it.completed })
    }

    @Test fun storedHabitOrderAndEmptyListSurviveRestartStyleReads() {
        assertTrue(GrowthHabits.saveHabits(context, listOf("Học", "Thiền", "Học")))
        assertEquals(listOf("Học", "Thiền"), GrowthHabits.habitNames(context))
        assertTrue(GrowthHabits.saveHabits(context, emptyList()))
        assertTrue(GrowthHabits.habitNames(context).isEmpty())
        assertTrue(GrowthHabits.activeHistory(context).isEmpty())
    }

}
