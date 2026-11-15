package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class CoachSpeechVoicePolicyTest {
    private val voices = listOf(
        CoachSpeechVoicePolicy.VoiceOption("en-us-local", "en"),
        CoachSpeechVoicePolicy.VoiceOption("vi-vn-female", "vi"),
        CoachSpeechVoicePolicy.VoiceOption("vi-vn-male", "vi")
    )

    @Test fun keepsSavedVoiceWhenItIsAnInstalledVietnameseVoice() {
        assertEquals(
            "vi-vn-male",
            CoachSpeechVoicePolicy.selectVietnameseVoice("vi-vn-male", voices)
        )
    }

    @Test fun replacesMissingOrNonVietnameseSavedVoiceWithVietnameseFallback() {
        assertEquals(
            "vi-vn-female",
            CoachSpeechVoicePolicy.selectVietnameseVoice("en-us-local", voices)
        )
        assertEquals(
            "vi-vn-female",
            CoachSpeechVoicePolicy.selectVietnameseVoice("removed-voice", voices)
        )
    }

    @Test fun neverSelectsAnUnrelatedLanguageWhenVietnameseIsUnavailable() {
        assertNull(
            CoachSpeechVoicePolicy.selectVietnameseVoice(
                "en-us-local",
                listOf(CoachSpeechVoicePolicy.VoiceOption("en-us-local", "en"))
            )
        )
        assertNull(CoachSpeechVoicePolicy.selectVietnameseVoice(null, emptyList()))
    }

    @Test fun acceptsAllNonnegativeAndroidTtsLanguageStatuses() {
        assertTrue(CoachSpeechVoicePolicy.isSupportedLanguageStatus(0))
        assertTrue(CoachSpeechVoicePolicy.isSupportedLanguageStatus(1))
        assertTrue(CoachSpeechVoicePolicy.isSupportedLanguageStatus(2))
        assertFalse(CoachSpeechVoicePolicy.isSupportedLanguageStatus(-1))
        assertFalse(CoachSpeechVoicePolicy.isSupportedLanguageStatus(-2))
    }
}
