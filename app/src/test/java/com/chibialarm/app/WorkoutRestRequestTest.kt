package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRestRequestTest {
    @Test fun understandsVietnameseSecondsAndMinutes() {
        assertEquals(60, WorkoutRestRequest.seconds("Cho tôi nghỉ thêm 60 giây"))
        assertEquals(90, WorkoutRestRequest.seconds("nghỉ 90 giây"))
        assertEquals(120, WorkoutRestRequest.seconds("nghi them 2 phut"))
        assertEquals(180, WorkoutRestRequest.seconds("nghi them 3 phut"))
        assertEquals(600, WorkoutRestRequest.seconds("nghi 10 phut"))
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
        assertEquals(150, WorkoutRestRequest.seconds("nghỉ 2 phút 30 giây"))
    }

    @Test fun understandsNaturalVietnameseNumberWordsForRestDurations() {
        assertEquals(25, WorkoutRestRequest.seconds("nghỉ thêm hai mươi lăm giây"))
        assertEquals(35, WorkoutRestRequest.seconds("nghỉ thêm ba mươi lăm giây"))
        assertEquals(45, WorkoutRestRequest.seconds("nghỉ thêm bốn mươi lăm giây"))
        assertEquals(55, WorkoutRestRequest.seconds("nghỉ thêm năm mươi lăm giây"))
        assertEquals(85, WorkoutRestRequest.seconds("nghỉ thêm một phút hai mươi lăm giây"))
        assertEquals(85, WorkoutRestRequest.seconds("nghỉ thêm 1 phút hai mươi lăm giây"))
        assertEquals(95, WorkoutRestRequest.seconds("nghỉ thêm một phút ba mươi lăm giây"))
    }

    @Test fun identifiesOnlyUnambiguousSupportedSpokenRestDurations() {
        for (phrase in listOf(
            "nghỉ hai mươi lăm giây",
            "nghỉ ba mươi lăm giây",
            "nghỉ bốn mươi lăm giây",
            "nghỉ năm mươi lăm giây",
            "nghỉ ba phút",
            "nghỉ 10 phút",
            "nghỉ hai phút ba mươi giây",
            "nghỉ một phút hai mươi lăm giây",
        )) {
            assertTrue(phrase, WorkoutRestRequest.hasRecognizedDuration(phrase))
        }
        for (phrase in listOf(
            "bài 60 giây",
            "nghỉ 5 giây",
            "nghỉ thêm 60 lần",
            "mệt quá, cần nghỉ"
        )) {
            assertFalse(phrase, WorkoutRestRequest.hasRecognizedDuration(phrase))
        }
    }

    @Test fun rejectsSpokenDurationsOutsideTheSafeRange() {
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ thêm một phút chín mươi giây"))
        assertEquals(180, WorkoutRestRequest.seconds("nghỉ thêm ba phút"))
        assertEquals(150, WorkoutRestRequest.seconds("nghỉ thêm hai phút ba mươi giây"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ thêm mười một phút"))
    }

    @Test fun voicePreviewUsesTheRecognizedRestDuration() {
        assertEquals("Bắt đầu nghỉ riêng 90 giây",
            WorkoutVoiceCommands.describe(WorkoutVoiceCommands.Command.REST, "nghỉ 1 phút 30 giây"))
        assertEquals("Bắt đầu nghỉ riêng 85 giây",
            WorkoutVoiceCommands.describe(WorkoutVoiceCommands.Command.REST, "nghỉ một phút hai mươi lăm giây"))
        assertEquals("Bắt đầu nghỉ riêng 180 giây",
            WorkoutVoiceCommands.describe(WorkoutVoiceCommands.Command.REST, "nghỉ ba phút"))
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
            WorkoutVoiceCommands.parse("nghỉ ba phút"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ 90 giây"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("cho tôi nghỉ 2 phút"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ thêm 45 s"))
        assertEquals(WorkoutVoiceCommands.Command.REST,
            WorkoutVoiceCommands.parse("nghỉ thêm hai mươi lăm giây"))
        assertEquals(WorkoutVoiceCommands.Command.PAIN,
            WorkoutVoiceCommands.parse("đau gối, nghỉ 60 giây"))
        assertEquals(WorkoutVoiceCommands.Command.UNKNOWN,
            WorkoutVoiceCommands.parse("bài 60 giây"))
    }

    @Test fun contextualExtensionRequiresActiveRestAndAnExplicitIncrease() {
        assertEquals(30, WorkoutRestRequest.contextualExtensionSeconds("Thêm 30 giây nữa", true))
        assertEquals(30, WorkoutRestRequest.contextualExtensionSeconds("Đúng, thêm 30 giây nữa", true))
        assertEquals(60, WorkoutRestRequest.contextualExtensionSeconds("Cộng thêm một phút", true))
        assertEquals(45, WorkoutRestRequest.contextualExtensionSeconds("Kéo dài thêm 45 giây", true))
        assertEquals(180, WorkoutRestRequest.contextualExtensionSeconds("Thêm ba phút nữa", true))
        assertEquals(600, WorkoutRestRequest.contextualExtensionSeconds("Thêm mười phút nữa", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Thêm 30 giây nữa", false))
    }

    @Test fun understandsHalfMinuteAsAnExplicitVietnameseRestDuration() {
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ nửa phút"))
        assertEquals(30, WorkoutRestRequest.seconds("nghỉ thêm nửa phút nữa"))
        assertTrue(WorkoutRestRequest.hasRecognizedDuration("nghỉ nửa phút"))
        assertEquals(30, WorkoutRestRequest.contextualExtensionSeconds("thêm nửa phút nữa", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("thêm nửa phút nữa", false))
    }

    @Test fun contextualExtensionNeverOverridesAdditionalWarningSymptoms() {
        for (phrase in listOf(
            "Tê tay, thêm 30 giây nữa",
            "Buồn nôn, thêm một phút",
            "Đau ngực, thêm 30 giây",
            "Choáng và kiệt sức, kéo dài thêm 45 giây"
        )) {
            assertNull(phrase, WorkoutRestRequest.contextualExtensionSeconds(phrase, true))
        }
    }

    @Test fun contextualExtensionRejectsAmbiguousNegativeAndUnrelatedPhrases() {
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Thêm chút nữa", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Thêm 10 lần", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Không thêm 30 giây", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Đừng thêm 30 giây", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Dừng lại, thêm 30 giây", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Đau gối, thêm 30 giây", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Bài này 30 giây", true))
        assertNull(WorkoutRestRequest.contextualExtensionSeconds("Còn 30 giây", true))
    }
}
