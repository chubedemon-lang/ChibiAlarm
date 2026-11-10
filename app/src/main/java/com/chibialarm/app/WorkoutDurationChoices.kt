package com.chibialarm.app

/**
 * Workout duration choices shared by the profile form and natural-language coach.
 *
 * The first five legacy preference indices keep their original meaning (10, 15,
 * 20, 30, 40 minutes). New choices are stored as an explicit minute value so
 * adding options never silently changes an existing user's saved selection.
 */
object WorkoutDurationChoices {
    private val values = intArrayOf(5, 10, 15, 20, 30, 40, 45, 60, 90, 120)
    private val legacyValues = intArrayOf(10, 15, 20, 30, 40)

    val minutes: IntArray
        get() = values.copyOf()

    val labels: Array<String>
        get() = values.map { "$it phút" }.toTypedArray()

    /** Return the closest supported choice; ties prefer the shorter duration. */
    fun indexForMinutes(requested: Int): Int {
        val bounded = requested.coerceIn(values.first(), values.last())
        return values.indices.minByOrNull { kotlin.math.abs(values[it] - bounded) } ?: 0
    }

    /** Decode the old fitTime preference, which stored an index rather than minutes. */
    fun legacyMinutesForIndex(index: Int): Int =
        legacyValues[index.coerceIn(legacyValues.indices)]

    /** Keep the legacy key meaningful for older app builds while storing exact minutes separately. */
    fun legacyIndexForMinutes(requested: Int): Int {
        val bounded = requested.coerceIn(values.first(), values.last())
        return legacyValues.indices.minByOrNull {
            kotlin.math.abs(legacyValues[it] - bounded)
        } ?: 0
    }
}
