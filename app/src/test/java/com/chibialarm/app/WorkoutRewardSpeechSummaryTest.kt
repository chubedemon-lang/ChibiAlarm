package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRewardSpeechSummaryTest {
    @Test fun includesOnlyCommittedRewardAndLevelProgress() {
        val summary = WorkoutRewardSpeechSummary.build(
            WorkoutRewardDisplay(
                awardedXp = 15,
                totalXp = 120,
                level = 3,
                xpInLevel = 20,
                xpRequired = 50,
                maxLevelReached = false
            )
        )

        assertTrue(summary.contains("Buổi tập đã được lưu"))
        assertTrue(summary.contains("15 điểm EXP"))
        assertTrue(summary.contains("cấp 3"))
        assertTrue(summary.contains("Còn 30 EXP"))
    }

    @Test fun doesNotClaimNewRewardWhenTheLedgerAlreadyAwardedIt() {
        val summary = WorkoutRewardSpeechSummary.build(
            WorkoutRewardDisplay(
                awardedXp = 0,
                totalXp = 120,
                level = 3,
                xpInLevel = 20,
                xpRequired = 50,
                maxLevelReached = false
            )
        )

        assertTrue(summary.contains("đã được ghi nhận trước đó"))
        assertFalse(summary.contains("Bạn nhận được 0 điểm EXP"))
    }

    @Test fun maxLevelSummaryDoesNotAnnounceAProgressionTarget() {
        val summary = WorkoutRewardSpeechSummary.build(
            WorkoutRewardDisplay(
                awardedXp = 10,
                totalXp = 9_999_999,
                level = 1000,
                xpInLevel = 0,
                xpRequired = 0,
                maxLevelReached = true
            )
        )

        assertTrue(summary.contains("đã đạt cấp tối đa"))
        assertFalse(summary.contains("để lên cấp tiếp theo"))
    }

    @Test fun spokenSummaryIsBoundedAndContainsNoWorkoutOrHealthJournal() {
        val summary = WorkoutRewardSpeechSummary.build(
            WorkoutRewardDisplay(
                awardedXp = 30,
                totalXp = 50_000,
                level = 25,
                xpInLevel = 10,
                xpRequired = 500,
                maxLevelReached = false
            )
        )

        assertTrue(summary.length <= 240)
        assertFalse(summary.contains("Squat"))
        assertFalse(summary.contains("đau"))
        assertFalse(summary.contains("hiệp"))
    }
}
