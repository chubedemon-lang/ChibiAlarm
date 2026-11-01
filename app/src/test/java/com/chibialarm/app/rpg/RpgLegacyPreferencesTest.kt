package com.chibialarm.app.rpg

import android.content.Context
import androidx.room.Room
import com.chibialarm.app.LifeData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

/**
 * Simulates a populated pre-RPG routine preference file. This does NOT replace
 * a real installed v1.3 upgrade test; it guards the one-time import contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RpgLegacyPreferencesTest {
    @Test fun importingLegacyRoutineXpIsNonDestructiveAndIdempotentAfterReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("chibi_life_v12", Context.MODE_PRIVATE)
        val original = preferences.all.toMap()
        val date = LifeData.today()
        val file = "rpg_legacy_upgrade_test_${UUID.randomUUID()}.db"
        var database: RpgDatabase? = null
        try {
            assertTrue(preferences.edit().clear()
                .putString("routines", """[{"id":70001,"title":"Học bài","category":"📚","hour":7,"minute":0,"duration":30,"repeatMask":127}]""")
                .putBoolean("done:$date:70001", true)
                .putString("goalTitle", "Giữ thói quen cũ")
                .commit())
            val before = preferences.all.toMap()
            assertEquals(10, LifeData.points(context))

            val first = Room.databaseBuilder(context, RpgDatabase::class.java, file).build()
            database = first
            RpgEngine(first).initialize(LifeData.points(context), LocalDate.parse(date))
            assertEquals(10L, first.dao().character()!!.totalXp)
            assertEquals(before, preferences.all)

            first.close()
            database = null
            val reopened = Room.databaseBuilder(context, RpgDatabase::class.java, file).build()
            database = reopened
            RpgEngine(reopened).initialize(LifeData.points(context), LocalDate.parse(date))
            assertEquals(10L, reopened.dao().character()!!.totalXp)
            assertEquals(before, preferences.all)
        } finally {
            database?.close()
            context.deleteDatabase(file)
            val editor = preferences.edit().clear()
            for ((key, value) in original) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key, value as Set<String>)
                    }
                }
            }
            editor.commit()
        }
    }
}
