package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChibiPetSessionTest {
    @Test fun firstTwoPetsSurviveUnrelatedDashboardRefreshAndRotation() {
        val day = "2026-11-02"
        val session = ChibiPetSession()
        assertEquals(0, session.progress(day))
        assertEquals(1, session.pet(day))
        assertEquals(2, session.pet(day))
        // Rendering the dashboard again must not reset the session.
        assertEquals(2, session.progress(day))
        val restored = ChibiPetSession()
        restored.restore(day, session.snapshot(day))
        assertEquals(2, restored.progress(day))
        assertEquals(3, restored.pet(day))
    }

    @Test fun thirdTapIsNotAClaimAndMustBeReplayedAfterRecreation() {
        val day = "2026-11-02"
        val session = ChibiPetSession()
        repeat(5) { session.pet(day) }
        assertEquals(3, session.progress(day))
        assertEquals(0, session.snapshot(day).taps)
        val restored = ChibiPetSession()
        restored.restore(day, session.snapshot(day))
        assertEquals(0, restored.progress(day))
        // Only the Room ledger can grant a reward, not this UI state.
        assertEquals(1, restored.pet(day))
    }

    @Test fun dailyProgressResetsAcrossMidnightAndAfterFailedReward() {
        val session = ChibiPetSession()
        session.pet("2026-11-02")
        session.pet("2026-11-02")
        assertEquals(0, session.progress("2026-11-03"))
        assertEquals(1, session.pet("2026-11-03"))
        session.clear("2026-11-03")
        assertEquals(0, session.progress("2026-11-03"))
    }

    @Test fun malformedOrOldBundleCannotInventProgress() {
        val today = "2026-11-02"
        for (snapshot in listOf(
            ChibiPetSession.Snapshot("2026-11-01", 2),
            ChibiPetSession.Snapshot(today, -1),
            ChibiPetSession.Snapshot(today, 3),
            ChibiPetSession.Snapshot(today, Int.MAX_VALUE),
            ChibiPetSession.Snapshot("", 2)
        )) {
            val session = ChibiPetSession()
            session.restore(today, snapshot)
            assertEquals(0, session.progress(today))
        }
    }
}
