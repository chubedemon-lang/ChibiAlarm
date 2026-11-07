package com.chibialarm.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GoalDataRecoveryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("chibi_goals_v12", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }
    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun damagedNeighborDoesNotHideValidGoalsOrMutateOriginal() {
        val raw = """[{"id":1,"title":"Read","span":30,"started":1000,"checked":["2026-01-01"]},null,{"id":"broken"},{"id":2,"title":"Walk","span":90,"started":2000,"checked":[]}]"""
        assertTrue(prefs.edit().putString("goals", raw).commit())
        assertEquals(listOf(1, 2), GoalData.goals(context).map { it.id })
        assertEquals(raw, prefs.getString("goals", null))
        assertFalse(prefs.contains("goals_corrupt_backup"))
    }

    @Test fun firstDamagedPayloadIsPreservedOnEditAndNeverReplaced() {
        val damaged = """[{"id":1,"title":"Read","started":1000},false]"""
        assertTrue(prefs.edit().putString("goals", damaged).commit())
        assertTrue(GoalData.save(context, GoalData.goals(context)))
        assertEquals(damaged, prefs.getString("goals_corrupt_backup", null))
        assertTrue(GoalData.save(context, emptyList()))
        assertEquals(damaged, prefs.getString("goals_corrupt_backup", null))
    }

    @Test fun invalidPreferenceTypeIsNotModifiedUntilEdit() {
        assertTrue(prefs.edit().putInt("goals", 42).commit())
        assertTrue(GoalData.goals(context).isEmpty())
        assertEquals(42, prefs.getInt("goals", -1))
        assertTrue(GoalData.save(context, emptyList()))
        assertEquals("42", prefs.getString("goals_corrupt_backup", null))
    }

    @Test fun staleCardReadsCurrentCheckInAndCannotRecreateDeletedGoal() {
        val goal = LifeGoal(10, "Read", 30, 1000L, emptySet())
        assertTrue(GoalData.save(context, listOf(goal)))
        assertTrue(GoalData.toggle(context, goal))
        assertTrue(LifeData.today() in GoalData.goals(context).single().checked)
        assertTrue(GoalData.toggle(context, goal))
        assertFalse(LifeData.today() in GoalData.goals(context).single().checked)
        assertTrue(GoalData.save(context, emptyList()))
        assertFalse(GoalData.toggle(context, goal))
        assertTrue(GoalData.goals(context).isEmpty())
    }

    @Test fun failedCommitCannotClaimSuccessOrChangeStoredGoals() {
        val goal = LifeGoal(20, "Workout", 30, 1000L, emptySet())
        assertTrue(GoalData.save(context, listOf(goal)))
        val before = prefs.getString("goals", null)
        val failing = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                val source = super.getSharedPreferences(name, mode)
                if (name != "chibi_goals_v12") return source
                return object : SharedPreferences by source {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = source.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean = false
                        }
                    }
                }
            }
        }
        assertFalse(GoalData.toggle(failing, goal))
        assertEquals(before, prefs.getString("goals", null))
        assertFalse(prefs.contains("goals_corrupt_backup"))
    }
}
