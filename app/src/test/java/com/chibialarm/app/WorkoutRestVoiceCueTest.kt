package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRestVoiceCueTest {
    @Test fun announcesOnceInsideTheTenSecondWindow() {
        assertTrue(WorkoutRestVoiceCue.shouldAnnounce(10_000L, false, false))
        assertTrue(WorkoutRestVoiceCue.shouldAnnounce(1L, false, false))
        assertEquals(
            "Còn 10 giây nghỉ. Chuẩn bị cho động tác tiếp theo.",
            WorkoutRestVoiceCue.message(10_000L)
        )
    }

    @Test fun doesNotAnnounceOutsideTheWindowOrAfterTheCountdownEnds() {
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(10_001L, false, false))
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(0L, false, false))
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(-1L, false, false))
        assertEquals(null, WorkoutRestVoiceCue.message(0L))
    }

    @Test fun doesNotRepeatAnAlreadyAnnouncedCue() {
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(5_000L, true, false))
    }

    @Test fun safetyPauseSuppressesTheCue() {
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(5_000L, false, true))
    }

    @Test fun roundsRemainingSecondsUpForSpeech() {
        assertEquals("Còn 2 giây nghỉ. Chuẩn bị cho động tác tiếp theo.", WorkoutRestVoiceCue.message(1_001L))
        assertEquals("Còn 1 giây nghỉ. Chuẩn bị cho động tác tiếp theo.", WorkoutRestVoiceCue.message(1L))
    }
}
