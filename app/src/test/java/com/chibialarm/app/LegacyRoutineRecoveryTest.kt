package com.chibialarm.app

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Damaged v1.x preferences must not make unrelated valid routines disappear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyRoutineRecoveryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() =
        context.getSharedPreferences("chibi_life_v12", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }
    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun malformedNeighborsDoNotHideValidEntriesOrRewriteLegacyData() {
        val raw = """[
            {"id":11,"title":"Evening reading","hour":20,"minute":30},
            null,
            {"id":"bad","title":"Broken","hour":7,"minute":0},
            ["unexpected"],
            {"id":12,"title":"Morning workout","category":"Fitness","hour":7,"minute":15,"duration":25,"repeatMask":62}
        ]""".trimIndent()
        assertTrue(prefs.edit().putString("routines", raw).commit())

        val recovered = LifeData.routines(context)
        assertEquals(listOf(12, 11), recovered.map { it.id })
        assertEquals("Morning workout", recovered.first().title)
        assertEquals(62, recovered.first().repeatMask)
        assertEquals(127, recovered.last().repeatMask)
        assertEquals(raw, prefs.getString("routines", null))
        assertEquals(recovered, LifeData.routines(context))
    }

    @Test fun invalidJsonAndWrongPreferenceTypeDoNotCrashOrMutateData() {
        assertTrue(prefs.edit().putString("routines", "{truncated").commit())
        assertTrue(LifeData.routines(context).isEmpty())
        assertEquals("{truncated", prefs.getString("routines", null))

        assertTrue(prefs.edit().putInt("routines", 42).commit())
        assertTrue(LifeData.routines(context).isEmpty())
        assertEquals(42, prefs.getInt("routines", -1))
    }
}
