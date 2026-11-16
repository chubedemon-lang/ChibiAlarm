package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRepTargetsTest {
    @Test fun restoresOnlyCurrentAndFutureSteps() {
        assertEquals(mapOf(2 to 6, 3 to 12),
            WorkoutRepTargets.decode("0:30,1:9,2:6,3:12", 4, 2))
    }
    @Test fun rejectsMalformedAndOutOfRangeValues() {
        assertEquals(mapOf(1 to 8),
            WorkoutRepTargets.decode("x:9,1:0,1:101,9:5,1:8,1:-2,3", 3, 1))
        assertTrue(WorkoutRepTargets.decode("1:8", 0, 0).isEmpty())
    }
}
