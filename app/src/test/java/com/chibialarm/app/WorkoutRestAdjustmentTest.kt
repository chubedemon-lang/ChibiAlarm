package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutRestAdjustmentTest {
    @Test fun addsRequestedTimeToAnExistingRestCountdown() {
        val preview = WorkoutRestAdjustment.previewExtension(45_000L, 30)
        assertNotNull(preview)
        assertEquals(45_000L, preview!!.beforeRemainingMs)
        assertEquals(30, preview.requestedSeconds)
        assertEquals(75_000L, preview.afterRemainingMs)
        assertEquals(30_000L, preview.actualAddedMs)
        assertFalse(preview.wasCapped)
    }

    @Test fun capsCombinedRestAtTenMinutesAndReportsTheCap() {
        val preview = WorkoutRestAdjustment.previewExtension(590_000L, 30)
        assertNotNull(preview)
        assertEquals(600_000L, preview!!.afterRemainingMs)
        assertEquals(10_000L, preview.actualAddedMs)
        assertTrue(preview.wasCapped)
    }

    @Test fun canExtendAZeroSecondRestThatHasNotYetBeenDismissed() {
        val preview = WorkoutRestAdjustment.previewExtension(0L, 15)
        assertNotNull(preview)
        assertEquals(15_000L, preview!!.afterRemainingMs)
    }

    @Test fun refusesAnAlreadyMaximumRest() {
        assertNull(WorkoutRestAdjustment.previewExtension(600_000L, 1))
    }

    @Test fun rejectsInvalidCurrentTimeAndRequestedDuration() {
        assertNull(WorkoutRestAdjustment.previewExtension(-1L, 30))
        assertNull(WorkoutRestAdjustment.previewExtension(600_001L, 30))
        assertNull(WorkoutRestAdjustment.previewExtension(10_000L, 0))
        assertNull(WorkoutRestAdjustment.previewExtension(10_000L, 601))
    }
}
