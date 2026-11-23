package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachSpeechPreviewTest {
    @Test fun missingVietnameseTtsNeverStartsAPreview() {
        assertEquals(
            CoachSpeechPreview.Decision.UNAVAILABLE,
            CoachSpeechPreview.decision(languageSupported = false, networkRequired = false)
        )
    }

    @Test fun knownOfflineVoiceCanPreviewImmediately() {
        assertEquals(
            CoachSpeechPreview.Decision.PLAY_WITHOUT_NETWORK_PROMPT,
            CoachSpeechPreview.decision(languageSupported = true, networkRequired = false)
        )
    }

    @Test fun networkRequiredVoiceNeedsExplicitConsent() {
        assertEquals(
            CoachSpeechPreview.Decision.REQUIRE_NETWORK_CONSENT,
            CoachSpeechPreview.decision(languageSupported = true, networkRequired = true)
        )
    }

    @Test fun unknownNetworkBehaviorAlsoNeedsExplicitConsent() {
        assertEquals(
            CoachSpeechPreview.Decision.REQUIRE_UNKNOWN_NETWORK_CONSENT,
            CoachSpeechPreview.decision(languageSupported = true, networkRequired = null)
        )
    }

    @Test fun previewUsesOnlyABoundedGenericSampleNotChatOrWorkoutData() {
        assertTrue(CoachSpeechPreview.SAMPLE_TEXT.length in 20..160)
        assertTrue(CoachSpeechPreview.SAMPLE_TEXT.contains("Chibi"))
    }
    @Test fun autoReadUsesTheSameExplicitNetworkConsentGateAsVoicePreview() {
        assertEquals(
            CoachSpeechPreview.Decision.PLAY_WITHOUT_NETWORK_PROMPT,
            CoachSpeechPreview.autoReadDecision(languageSupported = true, networkRequired = false)
        )
        assertEquals(
            CoachSpeechPreview.Decision.REQUIRE_NETWORK_CONSENT,
            CoachSpeechPreview.autoReadDecision(languageSupported = true, networkRequired = true)
        )
        assertEquals(
            CoachSpeechPreview.Decision.REQUIRE_UNKNOWN_NETWORK_CONSENT,
            CoachSpeechPreview.autoReadDecision(languageSupported = true, networkRequired = null)
        )
        assertEquals(
            CoachSpeechPreview.Decision.UNAVAILABLE,
            CoachSpeechPreview.autoReadDecision(languageSupported = false, networkRequired = false)
        )
    }

    @Test fun chatAndReplayPlaybackHoldNetworkVoiceUntilSessionConsent() {
        assertEquals(
            CoachSpeechPreview.Decision.REQUIRE_NETWORK_CONSENT,
            CoachSpeechPreview.playbackDecision(true, true, false)
        )
        assertEquals(
            CoachSpeechPreview.Decision.PLAY_WITHOUT_NETWORK_PROMPT,
            CoachSpeechPreview.playbackDecision(true, true, true)
        )
    }

    @Test fun unknownVoiceMustAlsoBeConfirmedAndConsentIsOnlyAnInputToThisAttempt() {
        assertEquals(
            CoachSpeechPreview.Decision.REQUIRE_UNKNOWN_NETWORK_CONSENT,
            CoachSpeechPreview.playbackDecision(true, null, false)
        )
        assertEquals(
            CoachSpeechPreview.Decision.PLAY_WITHOUT_NETWORK_PROMPT,
            CoachSpeechPreview.playbackDecision(true, null, true)
        )
        assertEquals(
            CoachSpeechPreview.Decision.REQUIRE_UNKNOWN_NETWORK_CONSENT,
            CoachSpeechPreview.playbackDecision(true, null, false)
        )
    }

    @Test fun offlineVoiceNeedsNoConsentAndUnavailableVoiceCannotBeOverridden() {
        assertEquals(
            CoachSpeechPreview.Decision.PLAY_WITHOUT_NETWORK_PROMPT,
            CoachSpeechPreview.playbackDecision(true, false, false)
        )
        assertEquals(
            CoachSpeechPreview.Decision.UNAVAILABLE,
            CoachSpeechPreview.playbackDecision(false, true, true)
        )
    }


}
