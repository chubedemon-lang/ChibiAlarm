package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachSpeechTranscriptTest {
    @Test fun alternativesAreTrimmedWhitespaceNormalizedAndDeduplicatedInRecognizerOrder() {
        val result = CoachSpeechTranscript.candidates(
            listOf(
                "  giãn   cơ\n15 phút ",
                "",
                "giãn cơ 15 phút",
                "cardio 10 phút"
            )
        )

        assertEquals(listOf("giãn cơ 15 phút", "cardio 10 phút"), result.map { it.text })
        assertTrue(result.all { !it.truncated })
    }

    @Test fun caseOnlySpeechAlternativesAreDeduplicatedKeepingTheFirstDisplayText() {
        val result = CoachSpeechTranscript.candidates(
            listOf(
                "Cardio 10 phút",
                "cardio 10 phút",
                "GIÃN CƠ 15 PHÚT",
                "giãn cơ 15 phút"
            )
        )

        assertEquals(
            listOf("Cardio 10 phút", "GIÃN CƠ 15 PHÚT"),
            result.map { it.text }
        )
    }

    @Test fun longSpeechIsBoundedAndExplicitlyMarkedForReview() {
        val candidate = CoachSpeechTranscript.candidates(listOf("a".repeat(950))).single()

        assertEquals(CoachSpeechTranscript.MAX_CHARS, candidate.text.length)
        assertTrue(candidate.truncated)
        assertTrue(candidate.label.contains("đã rút gọn"))
    }

    @Test fun blankAndExcessAlternativesAreIgnored() {
        val result = CoachSpeechTranscript.candidates(
            listOf(" ", "one", "two", "three", "four", "five", "six", "seven")
        )

        assertEquals(CoachSpeechTranscript.MAX_ALTERNATIVES, result.size)
        assertEquals(listOf("one", "two", "three", "four", "five"), result.map { it.text })
    }

    @Test fun customLimitsAreClampedAndDuplicatesAfterTruncationAreRemoved() {
        val result = CoachSpeechTranscript.candidates(
            listOf("abcdef-one", "abcdef-two", "short"),
            maxChars = 6,
            maxAlternatives = 2
        )

        assertEquals(listOf("abcdef", "short"), result.map { it.text })
        assertTrue(result.first().truncated)
        assertFalse(result.last().truncated)
    }

    @Test fun callersCannotRaiseTheTranscriptOrAlternativeSafetyBounds() {
        val raw = (1..10).map { index -> "choice$index " + "x".repeat(1_000) }
        val result = CoachSpeechTranscript.candidates(
            raw,
            maxChars = 2_000,
            maxAlternatives = 20
        )

        assertEquals(CoachSpeechTranscript.MAX_ALTERNATIVES, result.size)
        assertTrue(result.all { it.text.length == CoachSpeechTranscript.MAX_CHARS && it.truncated })
    }
}
