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
    @Test fun switchingLocalDatesNeverCarriesUnpaidPetProgress() {
        val session = ChibiPetSession()
        val monday = "2026-11-02"
        val tuesday = "2026-11-03"
        assertEquals(1, session.pet(monday))
        assertEquals(2, session.pet(monday))
        assertEquals(0, session.progress(tuesday))
        assertEquals(1, session.pet(tuesday))
        // A manual clock rollback must not resurrect Monday's partial taps.
        assertEquals(0, session.progress(monday))
        assertEquals(1, session.pet(monday))
        assertEquals(0, session.progress(tuesday))
    }

    @Test fun staleSnapshotCannotRestoreAcrossDateBoundary() {
        val session = ChibiPetSession()
        val monday = "2026-11-02"
        val tuesday = "2026-11-03"
        repeat(2) { session.pet(monday) }
        val stale = session.snapshot(monday)
        val restored = ChibiPetSession()
        restored.restore(tuesday, stale)
        assertEquals(0, restored.progress(tuesday))
        assertEquals(1, restored.pet(tuesday))
        // Repeated restore from an old saved instance must remain harmless.
        restored.restore(tuesday, stale)
        assertEquals(0, restored.progress(tuesday))
    }

    @Test fun repeatedSameDayRestoreCannotUndoNewerInteractions() {
        val day = "2026-11-06"
        val session = ChibiPetSession()
        session.pet(day)
        val oldSnapshot = session.snapshot(day)
        session.pet(day)
        session.restore(day, oldSnapshot)
        assertEquals(2, session.progress(day))
        // A malformed same-day snapshot must not erase valid live progress.
        session.restore(day, ChibiPetSession.Snapshot(day, Int.MAX_VALUE))
        assertEquals(2, session.progress(day))
        // A newer snapshot can still restore progress on a fresh instance.
        val recreated = ChibiPetSession()
        recreated.restore(day, session.snapshot(day))
        assertEquals(2, recreated.progress(day))
    }

    @Test fun restoreFromYesterdayResetsOnlyWhenCurrentDayChanges() {
        val session = ChibiPetSession()
        session.pet("2026-11-05")
        session.restore("2026-11-06", ChibiPetSession.Snapshot("2026-11-05", 2))
        assertEquals(0, session.progress("2026-11-06"))
        session.pet("2026-11-06")
        session.restore("2026-11-06", ChibiPetSession.Snapshot("2026-11-05", 2))
        assertEquals(1, session.progress("2026-11-06"))
    }

}
