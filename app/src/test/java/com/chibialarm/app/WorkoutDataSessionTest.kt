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
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkoutDataSessionTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After fun tearDown() {
        context.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun appendsGuidedSessionOnceAndPreservesLegacyJournalEntries() {
        val legacy = Workout("2026-11-09", "Đi bộ", 20, "Nhập thủ công")
        val legacyJson = JSONArray().put(
            JSONObject()
                .put("date", legacy.date)
                .put("name", legacy.name)
                .put("minutes", legacy.minutes)
                .put("note", legacy.note)
        )
        context.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE)
            .edit().putString("history", legacyJson.toString()).commit()

        assertEquals("", WorkoutData.history(context).single().sessionId)
        val guided = Workout(
            date = "2026-11-10",
            name = "Toàn thân",
            minutes = 8,
            note = "3 động tác; 4 hiệp tự xác nhận",
            sessionId = "550e8400-e29b-41d4-a716-446655440010",
            completedMoves = 3,
            selfReportedSets = 4
        )

        assertTrue(WorkoutData.appendSession(context, guided))
        assertTrue(WorkoutData.appendSession(context, guided))

        val history = WorkoutData.history(context)
        assertEquals(2, history.size)
        assertEquals(legacy, history[0])
        assertEquals(guided, history[1])
    }

    @Test fun malformedLegacyJournalIsNeverSilentlyReplaced() {
        val prefs = context.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE)
        val corrupt = "{legacy-journal-unreadable"
        prefs.edit().putString("history", corrupt).commit()

        val guided = Workout(
            date = "2026-11-10",
            name = "Toàn thân",
            minutes = 8,
            note = "guided",
            sessionId = "550e8400-e29b-41d4-a716-446655440012",
            completedMoves = 3,
            selfReportedSets = 4
        )
        assertFalse(WorkoutData.appendSession(context, guided))
        // The ordinary save path also refuses to overwrite data it cannot parse.
        WorkoutData.save(context, emptyList())
        assertEquals(corrupt, prefs.getString("history", null))
    }

    @Test fun invalidOrInsufficientSessionEvidenceIsNotAppended() {
        val invalidId = Workout("2026-11-10", "Toàn thân", 8, "", "not-a-uuid", 3, 4)
        val insufficientMoves = Workout(
            "2026-11-10", "Toàn thân", 8, "",
            "550e8400-e29b-41d4-a716-446655440011", 1, 4
        )
        assertFalse(WorkoutData.appendSession(context, invalidId))
        assertFalse(WorkoutData.appendSession(context, insufficientMoves))
        assertTrue(WorkoutData.history(context).isEmpty())
    }
}
