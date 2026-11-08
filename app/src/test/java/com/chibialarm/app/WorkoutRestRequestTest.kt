package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutRestRequestTest {
    @Test fun understandsVietnameseSecondsAndMinutes() {
        assertEquals(60, WorkoutRestRequest.seconds("Cho tôi nghỉ thêm 60 giây"))
        assertEquals(90, WorkoutRestRequest.seconds("nghỉ 90 giây"))
        assertEquals(120, WorkoutRestRequest.seconds("nghi them 2 phut"))
        assertEquals(45, WorkoutRestRequest.seconds("rest 45 s"))
    }

    @Test fun ambiguousOrUnsafeDurationsFallBackToThirtySeconds() {
        assertEquals(30, WorkoutRestRequest.seconds("mệt quá, cần nghỉ"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ 999 phút"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ 5 giây"))
        assertEquals(30, WorkoutRestRequest.seconds("bài 60 giây"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ thêm 60 lần"))
    }

    @Test fun interpreterStillRoutesRestRequests() {
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ thêm 60 giây"))
    }
}
