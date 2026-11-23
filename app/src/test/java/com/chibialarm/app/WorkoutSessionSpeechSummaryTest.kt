package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionSpeechSummaryTest {
    @Test fun emptyWorkoutDetailsDoNotCreateASpokenRecap() {
        assertNull(WorkoutSessionSpeechSummary.build("", "   "))
    }

    @Test fun spokenRecapIncludesBothSelfReportedSectionsAndTheirLimits() {
        val result = WorkoutSessionSpeechSummary.build(
            "Squat: hiệp 1: 10 lần",
            "Squat: hiệp 1: Khó, cần nghỉ thêm"
        ).orEmpty()

        assertTrue(result.startsWith("Kết quả buổi tập."))
        assertTrue(result.contains("Squat: hiệp 1: 10 lần"))
        assertTrue(result.contains("Squat: hiệp 1: Khó, cần nghỉ thêm"))
        assertTrue(result.contains("không được cảm biến xác minh"))
        assertTrue(result.contains("không phải phép đo thể lực"))
    }

    @Test fun eitherStructuredSectionCanBeReadWithoutTheOther() {
        val setOnly = WorkoutSessionSpeechSummary.build("Plank: hiệp 1: 20 giây", "").orEmpty()
        val effortOnly = WorkoutSessionSpeechSummary.build("", "Plank: hiệp 1: Vừa sức").orEmpty()

        assertTrue(setOnly.contains("Plank: hiệp 1: 20 giây"))
        assertFalse(setOnly.contains("Cảm nhận các hiệp"))
        assertTrue(effortOnly.contains("Plank: hiệp 1: Vừa sức"))
        assertFalse(effortOnly.contains("Giá trị từng hiệp"))
    }

    @Test fun controlCharactersAreRemovedAndLongSectionsAreBounded() {
        val result = WorkoutSessionSpeechSummary.build(
            "Squat\n\u0000" + "x".repeat(900),
            "Plank\r\nVừa sức"
        ).orEmpty()

        assertTrue(result.length <= WorkoutSessionSpeechSummary.MAX_TOTAL_CHARS)
        assertFalse(result.any { it.isISOControl() })
        assertTrue(result.contains("…"))
        assertTrue(result.contains("Plank Vừa sức"))
    }
}
