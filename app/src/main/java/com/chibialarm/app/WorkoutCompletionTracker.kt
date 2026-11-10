package com.chibialarm.app

/**
 * Evidence gate for the guided workout journal.
 *
 * The Activity calls [complete] only when its exercise timer reaches zero.
 * A manual "next" action is navigation, not proof of completed activity.
 * This is an offline honesty guard, not anti-cheat or medical monitoring.
 */
object WorkoutCompletionTracker {
    const val MIN_COMPLETED_MOVES_TO_LOG = 2
    const val MIN_ACTIVE_MILLIS_TO_LOG = 60_000L
    private const val MAX_MOVES = 500
    private const val MAX_SERIALIZED_CHARS = 4_096

    fun complete(completed: Set<Int>, currentIndex: Int, totalMoves: Int): Set<Int> {
        require(totalMoves in 1..MAX_MOVES)
        require(currentIndex in 0 until totalMoves)
        return completed.filterTo(sortedSetOf()) { it in 0 until totalMoves }.apply {
            add(currentIndex)
        }
    }

    fun encode(completed: Set<Int>, totalMoves: Int): String {
        require(totalMoves in 1..MAX_MOVES)
        return completed.asSequence()
            .filter { it in 0 until totalMoves }
            .distinct()
            .sorted()
            .joinToString(",")
    }

    /** Corrupt, stale or out-of-range checkpoint entries are ignored, never trusted. */
    fun decode(serialized: String?, totalMoves: Int): Set<Int> {
        require(totalMoves in 1..MAX_MOVES)
        if (serialized.isNullOrBlank()) return emptySet()
        return serialized.take(MAX_SERIALIZED_CHARS)
            .split(',')
            .take(MAX_MOVES)
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 0 until totalMoves }
            .toSortedSet()
    }

    fun canLog(completed: Set<Int>, totalMoves: Int, activeMillis: Long): Boolean {
        if (totalMoves !in 1..MAX_MOVES || activeMillis < MIN_ACTIVE_MILLIS_TO_LOG) return false
        return completed.count { it in 0 until totalMoves } >= MIN_COMPLETED_MOVES_TO_LOG
    }
}
