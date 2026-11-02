package com.chibialarm.app

import android.content.Context
import com.chibialarm.app.rpg.StreakDay
import java.time.LocalDate

/**
 * Read the active Growth habit set without mutating legacy preferences.
 * Deleted habits keep their historical check-ins for possible recovery, but must
 * not grant current RPG quest credit or contribute to the current streak.
 *
 * A corrupt legacy habit list fails closed: treating it as the default list could
 * accidentally grant RPG progress for habits the user previously deleted.
 */
object GrowthHabits {
    private const val PREF = "growth"
    private const val CORRUPT_BACKUP = "habits_corrupt_backup"
    val defaultNames = listOf("Uống nước", "Tập thể dục", "Đọc sách", "Học kỹ năng", "Ngủ đúng giờ")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Preserve the stored display order; never repair malformed data on read. */
    fun habitNames(context: Context): List<String> = when (val raw = prefs(context).all["habits"]) {
        null -> defaultNames
        is String -> raw.split("\n").filter { it.isNotBlank() }.distinct()
        else -> emptyList()
    }

    fun hasMalformedHabitList(context: Context): Boolean {
        val stored = prefs(context).all
        return stored.containsKey("habits") && stored["habits"] !is String
    }

    fun activeNames(context: Context): Set<String> = habitNames(context).toSet()

    /** Keep a damaged old value in the same durable commit as its replacement. */
    @Synchronized fun saveHabits(context: Context, names: List<String>): Boolean {
        val preferences = prefs(context)
        val original = preferences.all
        val editor = preferences.edit()
        if (original.containsKey("habits") && original["habits"] !is String &&
            !original.containsKey(CORRUPT_BACKUP)) {
            editor.putString(CORRUPT_BACKUP, original["habits"].toString())
        }
        return editor.putString("habits", names.joinToString("\n")).commit()
    }

    /** An invalid old check-in is not a completed habit and remains untouched. */
    fun isDone(context: Context, name: String, day: String): Boolean =
        prefs(context).all["done:$day:$name"] == true

    /** A failed write must never be reported as a successful daily check-in. */
    @Synchronized fun setDone(context: Context, name: String, day: String, done: Boolean): Boolean =
        prefs(context).edit().putBoolean("done:$day:$name", done).commit()

    fun activeHistory(context: Context): List<StreakDay> {
        val active = activeNames(context)
        return prefs(context).all.mapNotNull { (key, value) ->
            if (value !is Boolean || !key.startsWith("done:") ||
                key.length <= 16 || key[15] != ':') return@mapNotNull null
            val date = key.substring(5, 15)
            val name = key.substring(16)
            if (name !in active || runCatching { LocalDate.parse(date) }.isFailure) null
            else StreakDay("growth:" + name, date, completed = value)
        }
    }
}
