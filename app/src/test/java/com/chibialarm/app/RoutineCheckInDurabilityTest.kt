package com.chibialarm.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Check-in XP must reflect committed preferences, never an optimistic UI toggle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoutineCheckInDurabilityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs: SharedPreferences get() =
        context.getSharedPreferences("chibi_life_v12", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }
    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun successfulCheckInAndUncheckSurviveReload() {
        val id = 876542
        assertFalse(LifeData.isDone(context, id))
        assertTrue(LifeData.mark(context, id, true))
        assertTrue(LifeData.isDone(context, id))
        assertTrue(LifeData.mark(context, id, false))
        assertFalse(LifeData.isDone(context, id))
    }

    @Test fun failedCommitDoesNotChangePreviousCheckIn() {
        val id = 876543
        assertTrue(LifeData.mark(context, id, true))
        val original = prefs.getBoolean("done:${LifeData.today()}:$id", false)
        val failing = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                val source = super.getSharedPreferences(name, mode)
                if (name != "chibi_life_v12") return source
                return object : SharedPreferences by source {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = source.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                                editor.putBoolean(key, value)
                                return this
                            }
                            override fun commit(): Boolean = false
                        }
                    }
                }
            }
        }
        assertFalse(LifeData.mark(failing, id, false))
        assertEquals(original, prefs.getBoolean("done:${LifeData.today()}:$id", false))
        assertTrue(LifeData.isDone(context, id))
    }

    @Test fun corruptLegacyCheckboxIsReadOnlyUntilExplicitEdit() {
        val id = 876544
        val key = "done:${LifeData.today()}:$id"
        assertTrue(prefs.edit().putString(key, "broken").commit())
        assertFalse(LifeData.isDone(context, id))
        assertEquals("broken", prefs.getString(key, null))
        assertTrue(LifeData.mark(context, id, true))
        assertTrue(LifeData.isDone(context, id))
    }
}
