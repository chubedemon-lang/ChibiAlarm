package com.chibialarm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LifeGoal(val id: Int, val title: String, val span: Int, val started: Long, val checked: Set<String>)

object GoalData {
    private const val PREF = "chibi_goals_v12"
    private const val BACKUP = "goals_corrupt_backup"
    private fun pref(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private data class Decoded(val goals: List<LifeGoal>, val damaged: Boolean)

    /** Reading old challenges never repairs or overwrites preferences. */
    private fun decode(raw: Any?): Decoded {
        if (raw == null) return Decoded(emptyList(), false)
        if (raw !is String) return Decoded(emptyList(), true)
        val array = try { JSONArray(raw) } catch (_: Exception) {
            return Decoded(emptyList(), true)
        }
        val recovered = mutableListOf<LifeGoal>()
        var damaged = false
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item == null) { damaged = true; continue }
            try {
                val id = item.getInt("id")
                val title = item.getString("title")
                val span = item.optInt("span", 30)
                val started = item.getLong("started")
                require(title.isNotBlank() && span in 1..3650 && started > 0L)
                val marks = item.optJSONArray("checked")
                if (item.has("checked") && marks == null) damaged = true
                val checked = mutableSetOf<String>()
                if (marks != null) {
                    for (j in 0 until marks.length()) {
                        val value = marks.opt(j)
                        if (value is String) checked.add(value) else damaged = true
                    }
                }
                recovered.add(LifeGoal(id, title, span, started, checked))
            } catch (_: Exception) { damaged = true }
        }
        return Decoded(recovered, damaged)
    }

    fun goals(ctx: Context): List<LifeGoal> = decode(pref(ctx).all["goals"]).goals

    /**
     * Store the first malformed payload and replacement in the same durable commit.
     * A failed write must not update the widget or claim an edit succeeded.
     */
    @Synchronized fun save(ctx: Context, goals: List<LifeGoal>): Boolean {
        val preferences = pref(ctx)
        val original = preferences.all["goals"]
        val array = JSONArray()
        goals.forEach { goal ->
            array.put(JSONObject().put("id", goal.id).put("title", goal.title)
                .put("span", goal.span).put("started", goal.started)
                .put("checked", JSONArray(goal.checked.toList())))
        }
        val editor = preferences.edit()
        if (original != null && decode(original).damaged && !preferences.contains(BACKUP)) {
            editor.putString(BACKUP, original.toString())
        }
        val saved = editor.putString("goals", array.toString()).commit()
        if (saved) ChibiWidget.updateAll(ctx)
        return saved
    }

    /** A stale card cannot overwrite other check-ins or resurrect a deleted goal. */
    @Synchronized fun toggle(ctx: Context, goal: LifeGoal): Boolean {
        val current = goals(ctx)
        val index = current.indexOfFirst { it.id == goal.id }
        if (index < 0) return false
        val existing = current[index]
        val marks = existing.checked.toMutableSet()
        val today = LifeData.today()
        if (!marks.add(today)) marks.remove(today)
        return save(ctx, current.toMutableList().apply {
            this[index] = existing.copy(checked = marks)
        })
    }

    /** Challenge days follow the user's local calendar, not elapsed 24-hour blocks.
     * This stays correct across midnight and daylight-saving time changes.
     * A future-dated legacy goal displays day one but earns no completed days.
     */
    fun elapsed(g: LifeGoal): Int = elapsedAt(g, System.currentTimeMillis(), java.time.ZoneId.systemDefault())

    internal fun elapsedAt(g: LifeGoal, now: Long, zone: java.time.ZoneId): Int {
        val start = java.time.Instant.ofEpochMilli(g.started).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, today)
        return (days.coerceIn(0L, Int.MAX_VALUE.toLong() - 1L) + 1L).toInt()
    }

    /** Read-only, bounded progress. Malformed, future and out-of-window legacy
     * marks remain in storage for recovery but cannot inflate the displayed goal.
     */
    fun completedDays(g: LifeGoal): Int =
        completedDaysAt(g, System.currentTimeMillis(), java.time.ZoneId.systemDefault())

    internal fun completedDaysAt(g: LifeGoal, now: Long, zone: java.time.ZoneId): Int {
        if (g.span <= 0 || g.started > now) return 0
        val start = java.time.Instant.ofEpochMilli(g.started).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val lastDay = start.plusDays(g.span.toLong() - 1L)
        return g.checked.count { raw ->
            val day = try { java.time.LocalDate.parse(raw) } catch (_: Exception) { null }
            day != null && !day.isBefore(start) && !day.isAfter(lastDay) && !day.isAfter(today)
        }.coerceAtMost(g.span)
    }
}
