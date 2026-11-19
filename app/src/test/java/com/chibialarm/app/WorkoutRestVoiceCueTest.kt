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
            "Đã hết giờ nghỉ. Khi sẵn sàng, bắt đầu hiệp 2/2 của Squat không tạ; mục tiêu 8 đến 12 lần. Hãy tập theo sức mình.",
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
        assertEquals("Sau nghỉ: hiệp 2/3 của Squat không tạ; mục tiêu 8 đến 12 lần.", preview)
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
}
