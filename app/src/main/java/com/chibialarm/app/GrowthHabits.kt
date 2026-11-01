package com.chibialarm.app

import android.content.Context
import com.chibialarm.app.rpg.StreakDay
import java.time.LocalDate

/**
 * Read the active Growth habit set without mutating legacy preferences.
 * Deleted habits keep their historical check-ins for possible recovery, but must
 * not grant current RPG quest credit or contribute to the current streak.
 */
object GrowthHabits {
    val defaultNames = listOf("Uống nước", "Tập thể dục", "Đọc sách", "Học kỹ năng", "Ngủ đúng giờ")

    fun activeNames(context: Context): Set<String> {
        val stored = context.getSharedPreferences("growth", Context.MODE_PRIVATE)
            .getString("habits", null)
        return (stored?.split("\n") ?: defaultNames).filter { it.isNotBlank() }.toSet()
    }

    fun activeHistory(context: Context): List<StreakDay> {
        val prefs = context.getSharedPreferences("growth", Context.MODE_PRIVATE)
        val active = activeNames(context)
        return prefs.all.mapNotNull { (key, value) ->
            if (value !is Boolean || !key.startsWith("done:") ||
                key.length <= 16 || key[15] != ':') return@mapNotNull null
            val date = key.substring(5, 15)
            val name = key.substring(16)
            if (name !in active || runCatching { LocalDate.parse(date) }.isFailure) null
            else StreakDay("growth:" + name, date, completed = value)
        }
    }
}
