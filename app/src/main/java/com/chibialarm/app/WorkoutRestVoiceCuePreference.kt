package com.chibialarm.app

import android.content.Context

/**
 * Device-local preference for guest guided workouts.
 *
 * This is deliberately not shared with authenticated profiles. If account-backed
 * workouts are introduced, their voice preferences must be stored under the
 * verified account identity instead of reusing this guest preference file.
 */
internal object WorkoutRestVoiceCuePreference {
    const val PREFERENCES_NAME = "chibi_workout_preferences_guest"
    const val KEY_ENABLED = "rest_voice_cue_enabled"

    fun load(context: Context, defaultValue: Boolean = true): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, defaultValue)

    /** Synchronously persist a user choice before the Activity may be recreated. */
    fun save(context: Context, enabled: Boolean): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()
}
