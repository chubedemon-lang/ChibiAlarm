package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAlternativeSpeechTest {
    private fun move(name: String, dose: String = "2 hiệp × 8–12 lần") = TrainingMove(
        name = name,
        dose = dose,
        steps = "Chuẩn bị. Thực hiện.",
        easier = "Giảm biên độ.",
        avoid = "Dừng nếu đau."
    )

    @Test fun emptyAlternativeListHasNoSpokenSummary() {
        assertNull(WorkoutAlternativeSpeech.summary(emptyList()))
    }

    @Test fun summaryNamesCuratedOptionsAndStatesThatPlanWasNotChanged() {
        val summary = WorkoutAlternativeSpeech.summary(
            listOf(move("Chống đẩy tường"), move("Chống đẩy trên bề mặt cao"))
        )!!

        assertTrue(summary.contains("2 phương án thay thế"))
        assertTrue(summary.contains("Phương án 1: Chống đẩy tường"))
        assertTrue(summary.contains("Mục tiêu: 2 hiệp × 8–12 lần"))
        assertTrue(summary.contains("Phương án 2: Chống đẩy trên bề mặt cao"))
        assertTrue(summary.contains("Chibi chưa thay đổi giáo án"))
    }

    @Test fun spokenListIsBoundedAndMentionsRemainingOptions() {
        val options = (1..6).map { move("Biến thể " + it) }
        val summary = WorkoutAlternativeSpeech.summary(options)!!

        assertTrue(summary.contains("6 phương án thay thế"))
        assertTrue(summary.contains("Phương án 4: Biến thể 4"))
        assertFalse(summary.contains("Phương án 5:"))
        assertTrue(summary.contains("Còn 2 phương án khác"))
    }

    @Test fun catalogControlCharactersAreRemovedBeforeTts() {
        val summary = WorkoutAlternativeSpeech.summary(
            listOf(move("Wall\npush\u0000", "2 hiệp × 8 lần\n"))
        )!!

        assertFalse(summary.contains('\n'))
        assertFalse(summary.contains('\u0000'))
        assertTrue(summary.contains("Wall push"))
    }

    @Test fun longCatalogFieldsAreTruncated() {
        val longName = "A".repeat(200)
        val summary = WorkoutAlternativeSpeech.summary(listOf(move(longName)))!!

        assertTrue(summary.contains("A".repeat(80)))
        assertFalse(summary.contains("A".repeat(81)))
    }
}
