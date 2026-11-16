package com.chibialarm.app

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** A failed preference commit must not cancel reminders or replace the user's routines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoutineSaveDurabilityTest {
    @Test fun failedWriteKeepsPreviouslyScheduledReminderAndSavedData() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("chibi_life_v12", Context.MODE_PRIVATE)
        val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        prefs.edit().clear().commit()
        val existing = Routine(195501, "Existing reminder", "Study", 8, 30, 20)
        try {
            LifeData.save(context, listOf(existing))
            assertEquals(1, alarms.scheduledAlarms.size)
            val originalJson = prefs.getString("routines", null)
            // Intercept only the routines preference. Other Android services
            // still use the real Context and its AlarmManager.
            val failingContext = object : ContextWrapper(context) {
                override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                    val original = super.getSharedPreferences(name, mode)
                    if (name != "chibi_life_v12") return original
                    return object : SharedPreferences by original {
                        override fun edit(): SharedPreferences.Editor {
                            val delegate = original.edit()
                            return object : SharedPreferences.Editor by delegate {
                                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                    delegate.putString(key, value)
                                    return this
                                }
                                override fun commit(): Boolean = false
                            }
                        }
                    }
                }
            }
            try {
                LifeData.save(failingContext, emptyList())
                fail("A failed commit must be reported")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("Cannot save routines"))
            }
            assertEquals(originalJson, prefs.getString("routines", null))
            assertEquals(listOf(existing), LifeData.routines(context))
            assertEquals(1, alarms.scheduledAlarms.size)
        } finally {
            LifeData.save(context, emptyList())
            prefs.edit().clear().commit()
        }
    }
}
