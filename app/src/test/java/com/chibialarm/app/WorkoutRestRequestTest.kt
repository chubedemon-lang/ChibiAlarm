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

    @Test fun understandsSpokenVietnameseDurationsWithoutDigits() {
        assertEquals(60, WorkoutRestRequest.seconds("cho tao nghỉ một phút"))
        assertEquals(90, WorkoutRestRequest.seconds("nghỉ thêm một phút rưỡi"))
        assertEquals(120, WorkoutRestRequest.seconds("nghỉ hai phút"))
        assertEquals(90, WorkoutRestRequest.seconds("nghỉ chín mươi giây"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ ba mươi giây"))
        assertEquals(30, WorkoutRestRequest.seconds("bài một phút rưỡi"))
    }


    @Test fun parsesCompoundMinuteAndSecondDurationsWithoutTruncating() {
        assertEquals(90, WorkoutRestRequest.seconds("nghỉ 1 phút 30 giây"))
        assertEquals(75, WorkoutRestRequest.seconds("nghỉ 1 phút 15 giây"))
        assertEquals(90, WorkoutRestRequest.seconds("nghỉ một phút ba mươi giây"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ 1 phút 90 giây"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ 2 phút 30 giây"))
    }

    @Test fun voicePreviewUsesTheRecognizedRestDuration() {
        assertEquals("Bắt đầu nghỉ riêng 90 giây",
            WorkoutVoiceCommands.describe(WorkoutVoiceCommands.Command.REST, "nghỉ 1 phút 30 giây"))
        assertEquals("Bắt đầu nghỉ riêng 30 giây",
            WorkoutVoiceCommands.describe(WorkoutVoiceCommands.Command.REST))
        assertEquals("Hiển thị cách tập dễ hơn và thêm 60 giây nghỉ",
            WorkoutVoiceCommands.describe(WorkoutVoiceCommands.Command.EASIER_AND_REST, "nhẹ hơn và nghỉ 60 giây"))
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
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ một phút rưỡi"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ hai phút"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ 90 giây"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("cho tôi nghỉ 2 phút"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ thêm 45 s"))
        assertEquals(WorkoutVoiceCommands.Command.PAIN,
            WorkoutVoiceCommands.parse("đau gối, nghỉ 60 giây"))
        assertEquals(WorkoutVoiceCommands.Command.UNKNOWN,
            WorkoutVoiceCommands.parse("bài 60 giây"))
    }
}
