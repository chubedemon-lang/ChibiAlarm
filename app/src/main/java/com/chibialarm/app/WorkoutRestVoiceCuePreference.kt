package com.chibialarm.app

import android.content.Context

/**
 * Device-local preferences for guest guided workouts.
 *
 * These settings are deliberately not shared with authenticated profiles. If account-backed
 * workouts are introduced, voice preferences must be stored under the verified account identity.
 */
internal object WorkoutRestVoiceCuePreference {
    const val PREFERENCES_NAME = "chibi_workout_preferences_guest"
    const val KEY_ENABLED = "rest_voice_cue_enabled"
    const val KEY_LEAD_SECONDS = "rest_voice_cue_lead_seconds"

    const val DEFAULT_LEAD_SECONDS = 10
    const val MIN_LEAD_SECONDS = 5
    const val MAX_LEAD_SECONDS = 20
    const val LEAD_STEP_SECONDS = 5
    const val LEAD_PROGRESS_MAX = (MAX_LEAD_SECONDS - MIN_LEAD_SECONDS) / LEAD_STEP_SECONDS

    fun normalizeLeadSeconds(value: Int): Int {
        val bounded = value.coerceIn(MIN_LEAD_SECONDS, MAX_LEAD_SECONDS)
        val steps = ((bounded - MIN_LEAD_SECONDS + LEAD_STEP_SECONDS / 2) / LEAD_STEP_SECONDS)
            .coerceIn(0, LEAD_PROGRESS_MAX)
        return MIN_LEAD_SECONDS + steps * LEAD_STEP_SECONDS
    }

    fun progressForLeadSeconds(value: Int): Int =
        (normalizeLeadSeconds(value) - MIN_LEAD_SECONDS) / LEAD_STEP_SECONDS

    fun leadSecondsForProgress(progress: Int): Int =
        MIN_LEAD_SECONDS + progress.coerceIn(0, LEAD_PROGRESS_MAX) * LEAD_STEP_SECONDS

    fun load(context: Context, defaultValue: Boolean = true): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, defaultValue)

    fun save(context: Context, enabled: Boolean): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()

    fun loadLeadSeconds(
        context: Context,
        defaultValue: Int = DEFAULT_LEAD_SECONDS
    ): Int = normalizeLeadSeconds(
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LEAD_SECONDS, normalizeLeadSeconds(defaultValue))
    )

    /** Persist a bounded five-second step before the Activity can be recreated. */
    fun saveLeadSeconds(context: Context, seconds: Int): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LEAD_SECONDS, normalizeLeadSeconds(seconds))
            .commit()
}
