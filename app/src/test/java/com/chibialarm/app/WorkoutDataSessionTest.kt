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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkoutDataSessionTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private val accountSubjects = mutableListOf<String>()

    private fun newAccountSubject(label: String): String =
        "test:${UUID.randomUUID()}:$label".also(accountSubjects::add)

    @After fun tearDown() {
        context.getSharedPreferences("workouts_v12", Context.MODE_PRIVATE)
            .edit().clear().commit()
        accountSubjects.forEach { subject ->
            context.getSharedPreferences(
                WorkoutData.accountPreferencesName(subject), Context.MODE_PRIVATE
            ).edit().clear().commit()
        }
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
            selfReportedSets = 4,
            setLog = "Squat: hiệp 1: 9 lần, hiệp 2: 10 lần",
            effortLog = "Squat: hiệp 1: Khó, cần nghỉ thêm"
        )

        assertTrue(WorkoutData.appendSession(context, guided))
        assertTrue(WorkoutData.appendSession(context, guided))

        val history = WorkoutData.history(context)
        assertEquals(2, history.size)
        assertEquals(legacy, history[0])
        assertEquals(guided, history[1])
        assertEquals("Squat: hiệp 1: 9 lần, hiệp 2: 10 lần", history[1].setLog)
        assertEquals("Squat: hiệp 1: Khó, cần nghỉ thêm", history[1].effortLog)
        assertEquals("", history[0].setLog) // legacy entries decode without the additive field
        assertEquals("", history[0].effortLog) // legacy entries decode without the additive field
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

    @Test fun rejectsOversizedPerSetDetailWithoutWritingJournal() {
        val oversized = Workout(
            date = "2026-11-12",
            name = "Toàn thân",
            minutes = 8,
            note = "guided",
            sessionId = "550e8400-e29b-41d4-a716-446655440013",
            completedMoves = 3,
            selfReportedSets = 4,
            setLog = "x".repeat(1_201)
        )
        assertFalse(WorkoutData.appendSession(context, oversized))
        val oversizedEffort = Workout(
            date = "2026-11-12",
            name = "Toàn thân",
            minutes = 8,
            note = "guided",
            sessionId = "550e8400-e29b-41d4-a716-446655440014",
            completedMoves = 3,
            selfReportedSets = 4,
            effortLog = "x".repeat(1_201)
        )
        assertFalse(WorkoutData.appendSession(context, oversizedEffort))
        assertTrue(WorkoutData.history(context).isEmpty())
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

    @Test fun verifiedAccountFitnessJournalsAreIsolatedFromEachOtherAndTheLegacyGuest() {
        val aliceSubject = newAccountSubject("alice")
        val bobSubject = newAccountSubject("bob")
        val guest = Workout(
            "2026-11-14", "Guest workout", 8, "guest-only",
            UUID.randomUUID().toString(), 3, 4
        )
        val alice = Workout(
            "2026-11-14", "Alice workout", 9, "account-a",
            UUID.randomUUID().toString(), 3, 5
        )
        val bob = Workout(
            "2026-11-14", "Bob workout", 10, "account-b",
            UUID.randomUUID().toString(), 4, 6
        )

        assertTrue(WorkoutData.appendSession(context, guest))
        assertTrue(WorkoutData.appendSessionForVerifiedAccount(context, aliceSubject, alice))
        assertTrue(WorkoutData.appendSessionForVerifiedAccount(context, bobSubject, bob))
        // Retry deduplication is local to Alice's journal, not device-global.
        assertTrue(WorkoutData.appendSessionForVerifiedAccount(context, aliceSubject, alice))

        assertEquals(listOf(guest), WorkoutData.history(context))
        assertEquals(listOf(alice), WorkoutData.historyForVerifiedAccount(context, aliceSubject))
        assertEquals(listOf(bob), WorkoutData.historyForVerifiedAccount(context, bobSubject))
        assertNotEquals(
            WorkoutData.accountPreferencesName(aliceSubject),
            WorkoutData.accountPreferencesName(bobSubject)
        )
        assertFalse(WorkoutData.accountPreferencesName(aliceSubject).contains(aliceSubject))
        assertFalse(WorkoutData.accountPreferencesName(bobSubject).contains(bobSubject))
    }

    @Test fun explicitAccountFitnessErasureDoesNotTouchGuestOrAnotherAccount() {
        val aliceSubject = newAccountSubject("erase")
        val bobSubject = newAccountSubject("keep")
        val guest = Workout(
            "2026-11-14", "Guest", 8, "",
            UUID.randomUUID().toString(), 2, 2
        )
        val alice = Workout(
            "2026-11-14", "Alice", 8, "",
            UUID.randomUUID().toString(), 2, 2
        )
        val bob = Workout(
            "2026-11-14", "Bob", 8, "",
            UUID.randomUUID().toString(), 2, 2
        )

        assertTrue(WorkoutData.appendSession(context, guest))
        assertTrue(WorkoutData.appendSessionForVerifiedAccount(context, aliceSubject, alice))
        assertTrue(WorkoutData.appendSessionForVerifiedAccount(context, bobSubject, bob))

        assertTrue(WorkoutData.deleteVerifiedAccountHistory(context, aliceSubject))
        assertTrue(WorkoutData.historyForVerifiedAccount(context, aliceSubject).isEmpty())
        assertEquals(listOf(bob), WorkoutData.historyForVerifiedAccount(context, bobSubject))
        assertEquals(listOf(guest), WorkoutData.history(context))
    }

    @Test fun malformedVerifiedAccountJournalIsNeverSilentlyReplaced() {
        val subject = newAccountSubject("corrupt")
        val name = WorkoutData.accountPreferencesName(subject)
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val corrupt = "{account-journal-unreadable"
        prefs.edit().putString("history", corrupt).commit()
        val guided = Workout(
            "2026-11-14", "Toàn thân", 8, "guided",
            UUID.randomUUID().toString(), 3, 4
        )

        assertFalse(WorkoutData.appendSessionForVerifiedAccount(context, subject, guided))
        WorkoutData.saveForVerifiedAccount(context, subject, emptyList())
        assertEquals(corrupt, prefs.getString("history", null))
    }

    @Test fun verifiedAccountJournalRejectsUntrustedSubjectShapes() {
        val invalidSubjects = listOf(
            "", " ", " alice", "alice ", "bad\nsubject", "a".repeat(129),
            "issuer:alice\u202E"
        )
        invalidSubjects.forEach { subject ->
            try {
                WorkoutData.historyForVerifiedAccount(context, subject)
                fail("Untrusted subject was accepted")
            } catch (_: IllegalArgumentException) {
                // The same strict subject validation used by account-scoped Room storage applies.
            }
        }
    }

}
