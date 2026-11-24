package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRestVoiceCueTest {
    @Test fun announcesOnceInsideTheTenSecondWindow() {
        assertTrue(WorkoutRestVoiceCue.shouldAnnounce(10_000L, false, false))
        assertTrue(WorkoutRestVoiceCue.shouldAnnounce(1L, false, false))
        assertEquals(
            "Còn 10 giây nghỉ. Chuẩn bị cho động tác tiếp theo.",
            WorkoutRestVoiceCue.message(10_000L)
        )
    }

    @Test fun doesNotAnnounceOutsideTheWindowOrAfterTheCountdownEnds() {
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(10_001L, false, false))
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(0L, false, false))
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(-1L, false, false))
        assertEquals(null, WorkoutRestVoiceCue.message(0L))
    }

    @Test fun doesNotRepeatAnAlreadyAnnouncedCue() {
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(5_000L, true, false))
    }

    @Test fun safetyPauseSuppressesTheCue() {
        assertFalse(WorkoutRestVoiceCue.shouldAnnounce(5_000L, false, true))
    }

    @Test fun userCanDisableAutomaticRestVoiceCueWithoutConsumingTheOneShot() {
        assertFalse(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 5_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                voiceCueEnabled = false
            )
        )
        assertTrue(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 5_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                voiceCueEnabled = true
            )
        )
    }

    @Test fun microphoneInputSuppressesCueWithoutConsumingTheOneShot() {
        assertFalse(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 5_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                microphoneActive = true
            )
        )
        assertTrue(WorkoutRestVoiceCue.shouldAnnounce(5_000L, false, false))
    }

    @Test fun busyOrUnavailableSpeechEngineDoesNotConsumeTheOneShot() {
        assertFalse(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 5_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                speechEngineReady = false
            )
        )
        assertTrue(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 5_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                speechEngineReady = true
            )
        )
    }

    @Test fun roundsRemainingSecondsUpForSpeech() {
        assertEquals("Còn 2 giây nghỉ. Chuẩn bị cho động tác tiếp theo.", WorkoutRestVoiceCue.message(1_001L))
        assertEquals("Còn 1 giây nghỉ. Chuẩn bị cho động tác tiếp theo.", WorkoutRestVoiceCue.message(1L))
    }

    
    private val setPlan = WorkoutSetTracker.Plan(
        requiredSets = 2,
        maximumSets = 3,
        targetMin = 8,
        targetMax = 12,
        unit = "lần",
        restSeconds = 45
    )

    @Test fun restCompletionAnnouncesTheNextRequiredSetAndTarget() {
        assertEquals(
            "Đã hết giờ nghỉ. Khi sẵn sàng, bắt đầu hiệp bắt buộc 2/2 của Squat không tạ; mục tiêu 8 đến 12 lần. Hãy tập theo sức mình.",
            WorkoutRestVoiceCue.completionMessage("Squat không tạ", 1, setPlan)
        )
    }

    @Test fun restCompletionMarksAdditionalSetsAsOptional() {
        assertEquals(
            "Đã hết giờ nghỉ. Hiệp tiếp theo của Squat không tạ là tùy chọn (3/3), mục tiêu 8 đến 12 lần. Chỉ thực hiện nếu bạn còn sức; có thể bỏ qua.",
            WorkoutRestVoiceCue.completionMessage("Squat không tạ", 2, setPlan)
        )
    }

    @Test fun restCompletionDoesNotInviteAnotherSetAfterTheMaximum() {
        assertEquals(
            "Đã hết giờ nghỉ. Các hiệp của Squat không tạ đã được ghi nhận đủ; không cần tập thêm.",
            WorkoutRestVoiceCue.completionMessage("Squat không tạ", 99, setPlan)
        )
    }

    @Test fun restCompletionAnnouncesNextCatalogMoveAfterMaximumSetsWithoutStartingIt() {
        assertEquals(
            "Đã hết giờ nghỉ. Các hiệp của Squat không tạ đã được ghi nhận đủ. Tiếp theo: Đi bộ tại chỗ; mục tiêu 2 phút. Khi sẵn sàng, bạn có thể chuyển sang động tác tiếp theo.",
            WorkoutRestVoiceCue.completionMessage(
                moveName = "Squat không tạ",
                completedSets = 3,
                plan = setPlan,
                nextMoveName = "Đi bộ tại chỗ",
                nextMoveDose = "2 phút"
            )
        )
    }

    @Test fun restCompletionSanitizesTheNextMoveAndDoseBeforeSpeaking() {
        assertEquals(
            "Đã hết giờ nghỉ. Các hiệp của Squat không tạ đã được ghi nhận đủ. Tiếp theo: Đi bộ tại chỗ; mục tiêu 2 phút. Khi sẵn sàng, bạn có thể chuyển sang động tác tiếp theo.",
            WorkoutRestVoiceCue.completionMessage(
                moveName = "Squat không tạ",
                completedSets = 3,
                plan = setPlan,
                nextMoveName = "Đi bộ\n tại chỗ",
                nextMoveDose = "2 phút\n"
            )
        )
    }

    @Test fun restCompletionKeepsOptionalSetPromptWhenMaximumHasNotBeenReached() {
        assertEquals(
            "Đã hết giờ nghỉ. Hiệp tiếp theo của Squat không tạ là tùy chọn (3/3), mục tiêu 8 đến 12 lần. Chỉ thực hiện nếu bạn còn sức; có thể bỏ qua.",
            WorkoutRestVoiceCue.completionMessage(
                moveName = "Squat không tạ",
                completedSets = 2,
                plan = setPlan,
                nextMoveName = "Đi bộ tại chỗ",
                nextMoveDose = "2 phút"
            )
        )
    }

    @Test fun timedMoveRestCompletionNamesTheMoveWithoutInventingSets() {
        assertEquals(
            "Đã hết giờ nghỉ. Khi bạn sẵn sàng, tiếp tục: Đi bộ tại chỗ.",
            WorkoutRestVoiceCue.completionMessage("Đi bộ tại chỗ", 0, null)
        )
    }

    @Test fun restCompletionBoundsAndNormalizesTheMoveName() {
        assertEquals(
            "Đã hết giờ nghỉ. Khi bạn sẵn sàng, tiếp tục: Squat không tạ.",
            WorkoutRestVoiceCue.completionMessage("  Squat\nkhông tạ  ", 0, null)
        )
        assertEquals(
            "Đã hết giờ nghỉ. Tiếp tục khi bạn sẵn sàng.",
            WorkoutRestVoiceCue.completionMessage("  ", 0, null)
        )
    }
    @Test fun countdownNamesTheNextRequiredSetAndTarget() {
        val preview = WorkoutRestVoiceCue.previewMessage("Squat không tạ", 1, setPlan)
        assertEquals("Sau nghỉ: hiệp bắt buộc 2/2 của Squat không tạ; mục tiêu 8 đến 12 lần.", preview)
        assertEquals("Còn 10 giây nghỉ. $preview", WorkoutRestVoiceCue.message(10_000L, preview))
    }

    @Test fun restPreviewMarksAdditionalSetsAsOptional() {
        assertEquals(
            "Sau nghỉ: hiệp tùy chọn 3/3 của Squat không tạ; mục tiêu 8 đến 12 lần.",
            WorkoutRestVoiceCue.previewMessage("Squat không tạ", 2, setPlan)
        )
    }

    @Test fun restPreviewMovesToTheNextExerciseAfterTheMaximumSet() {
        assertEquals(
            "Tiếp theo: Đi bộ tại chỗ; mục tiêu 2 phút.",
            WorkoutRestVoiceCue.previewMessage("Squat không tạ", 3, setPlan, "Đi bộ tại chỗ", "2 phút")
        )
    }

    @Test fun timedMovePreviewKeepsTheCurrentExerciseAndRemainingTime() {
        assertEquals(
            "Sau nghỉ: tiếp tục Đi bộ tại chỗ; thời gian gợi ý còn 01:30.",
            WorkoutRestVoiceCue.previewMessage("Đi bộ tại chỗ", 0, null, currentRemainingLabel = "01:30")
        )
    }

    @Test fun timedMovePreviewAnnouncesTheNextCatalogMoveWithoutInventingSets() {
        assertEquals(
            "Tiếp theo: Đi bộ tại chỗ; mục tiêu 2 phút.",
            WorkoutRestVoiceCue.previewMessage(
                moveName = "Plank",
                completedSets = 0,
                plan = null,
                nextMoveName = "Đi bộ tại chỗ",
                nextMoveDose = "2 phút"
            )
        )
    }

    @Test fun timedMoveCompletionAnnouncesTheNextCatalogMoveWithoutStartingIt() {
        assertEquals(
            "Đã hết giờ nghỉ. Tiếp theo: Đi bộ tại chỗ; mục tiêu 2 phút. Khi sẵn sàng, bạn có thể chuyển sang động tác tiếp theo.",
            WorkoutRestVoiceCue.completionMessage(
                moveName = "Plank",
                completedSets = 0,
                plan = null,
                nextMoveName = "Đi bộ tại chỗ",
                nextMoveDose = "2 phút"
            )
        )
    }

    @Test fun countdownPreviewSanitizesControlCharactersAndBoundsLength() {
        assertEquals(
            "Còn 5 giây nghỉ. Sau nghỉ: hiệp 2 của Squat.",
            WorkoutRestVoiceCue.message(5_000L, "Sau nghỉ:\nhiệp 2\u0000 của Squat.")
        )
        assertTrue(WorkoutRestVoiceCue.message(5_000L, "x".repeat(300))!!.length <= 202)
    }

    @Test fun oneShotRearmsOnlyAboveTheConfiguredLeadWindowAndNeverRepeatsDuringCountdown() {
        for (leadSeconds in listOf(5L, 10L, 15L, 20L)) {
            val leadMs = leadSeconds * 1_000L
            assertFalse(
                "must stay armed at the exact lead boundary: $leadSeconds s",
                WorkoutRestVoiceCue.shouldRearm(leadMs, leadMs)
            )
            assertFalse(
                "must stay armed inside the lead window: $leadSeconds s",
                WorkoutRestVoiceCue.shouldRearm(leadMs - 1L, leadMs)
            )
            assertTrue(
                "must re-arm only after the rest is extended above the lead window: $leadSeconds s",
                WorkoutRestVoiceCue.shouldRearm(leadMs + 1L, leadMs)
            )

            var alreadyAnnounced = false
            var announcements = 0
            for (remaining in leadMs downTo 1L step 250L) {
                if (WorkoutRestVoiceCue.shouldRearm(remaining, leadMs)) {
                    alreadyAnnounced = false
                }
                if (WorkoutRestVoiceCue.shouldAnnounce(
                        remainingMs = remaining,
                        alreadyAnnounced = alreadyAnnounced,
                        pausedForSafety = false,
                        warningWindowMs = leadMs
                    )) {
                    announcements++
                    alreadyAnnounced = true
                }
            }
            assertEquals("exactly one voice cue for $leadSeconds-second lead", 1, announcements)
        }
    }

    @Test fun configurableLeadWindowControlsWhenTheReminderIsAnnounced() {
        assertFalse(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 10_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                warningWindowMs = 5_000L
            )
        )
        assertTrue(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 15_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                warningWindowMs = 15_000L
            )
        )
        assertEquals(
            "Còn 15 giây nghỉ. Chuẩn bị cho động tác tiếp theo.",
            WorkoutRestVoiceCue.message(15_000L, warningWindowMs = 15_000L)
        )
        assertEquals(null, WorkoutRestVoiceCue.message(15_001L, warningWindowMs = 15_000L))
        assertEquals(
            "Còn 20 giây nghỉ. Chuẩn bị cho động tác tiếp theo.",
            WorkoutRestVoiceCue.message(20_000L, warningWindowMs = 20_000L)
        )
    }

    @Test fun leadTimePreferenceIsClampedAndSnappedToFiveSecondSteps() {
        assertEquals(5, WorkoutRestVoiceCuePreference.normalizeLeadSeconds(-20))
        assertEquals(5, WorkoutRestVoiceCuePreference.normalizeLeadSeconds(7))
        assertEquals(10, WorkoutRestVoiceCuePreference.normalizeLeadSeconds(8))
        assertEquals(15, WorkoutRestVoiceCuePreference.normalizeLeadSeconds(17))
        assertEquals(20, WorkoutRestVoiceCuePreference.normalizeLeadSeconds(99))
        assertEquals(0, WorkoutRestVoiceCuePreference.progressForLeadSeconds(5))
        assertEquals(3, WorkoutRestVoiceCuePreference.progressForLeadSeconds(20))
        assertEquals(5, WorkoutRestVoiceCuePreference.leadSecondsForProgress(-1))
        assertEquals(20, WorkoutRestVoiceCuePreference.leadSecondsForProgress(99))
    }

    @Test fun warningWindowIsBoundedEvenForCorruptCheckpointValues() {
        assertTrue(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 20_000L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                warningWindowMs = Long.MAX_VALUE
            )
        )
        assertFalse(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 20_001L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                warningWindowMs = Long.MAX_VALUE
            )
        )
        assertEquals(
            "Còn 20 giây nghỉ. Chuẩn bị cho động tác tiếp theo.",
            WorkoutRestVoiceCue.message(20_000L, warningWindowMs = Long.MAX_VALUE)
        )
        assertTrue(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 1L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                warningWindowMs = Long.MIN_VALUE
            )
        )
        assertFalse(
            WorkoutRestVoiceCue.shouldAnnounce(
                remainingMs = 2L,
                alreadyAnnounced = false,
                pausedForSafety = false,
                warningWindowMs = Long.MIN_VALUE
            )
        )
    }

    @Test fun invalidWarningWindowCannotRearmTheCueDuringTheBoundedWindow() {
        assertFalse(WorkoutRestVoiceCue.shouldRearm(20_000L, Long.MAX_VALUE))
        assertTrue(WorkoutRestVoiceCue.shouldRearm(20_001L, Long.MAX_VALUE))
        assertFalse(WorkoutRestVoiceCue.shouldRearm(1L, Long.MIN_VALUE))
        assertTrue(WorkoutRestVoiceCue.shouldRearm(2L, Long.MIN_VALUE))
    }

}
