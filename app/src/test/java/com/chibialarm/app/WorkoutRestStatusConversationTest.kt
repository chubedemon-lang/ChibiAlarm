package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRestStatusConversationTest {
    @Test fun liveRestQuestionUsesRoundedUpRemainingTime() {
        assertEquals(
            "Còn khoảng 32 giây nghỉ. Đồng hồ vẫn chạy; Chibi chưa thay đổi giáo án.",
            WorkoutRestStatusConversation.answer(
                input = "Còn bao lâu nghỉ?",
                restIsActive = true,
                remainingMs = 31_200L
            )
        )
    }

    @Test fun genericRemainingQuestionUsesRestContextOnlyWhileResting() {
        assertEquals(
            "Còn khoảng 1 phút nghỉ. Đồng hồ vẫn chạy; Chibi chưa thay đổi giáo án.",
            WorkoutRestStatusConversation.answer(
                input = "Còn bao lâu?",
                restIsActive = true,
                remainingMs = 60_000L
            )
        )
        assertNull(
            WorkoutRestStatusConversation.answer(
                input = "Còn bao lâu?",
                restIsActive = false,
                remainingMs = null
            )
        )
    }

    @Test fun pausedRestIsReportedAsPausedWithoutRestartingIt() {
        assertEquals(
            "Đồng hồ nghỉ đang tạm dừng; còn khoảng 1 phút 31 giây. Chibi chưa tự chạy lại.",
            WorkoutRestStatusConversation.answer(
                input = "Nghỉ còn lại bao lâu?",
                restIsActive = true,
                remainingMs = 90_001L,
                restIsPaused = true
            )
        )
    }

    @Test fun noActiveRestDoesNotInventALiveCountdown() {
        val answer = WorkoutRestStatusConversation.answer(
            input = "Còn mấy giây nghỉ?",
            restIsActive = false,
            remainingMs = null
        )
        assertTrue(answer!!.contains("không có đồng hồ nghỉ đang chạy"))
        assertTrue(answer.contains("nghỉ bao lâu"))
    }

    @Test fun recommendedRestQuestionIsNotMistakenForRemainingTime() {
        assertNull(
            WorkoutRestStatusConversation.answer(
                input = "Nghỉ bao lâu?",
                restIsActive = true,
                remainingMs = 20_000L
            )
        )
    }

    @Test fun ordinaryRestCommandsAreNotMistakenForStatusQuestions() {
        assertNull(
            WorkoutRestStatusConversation.answer(
                input = "Thêm 30 giây nữa",
                restIsActive = true,
                remainingMs = 20_000L
            )
        )
        assertNull(
            WorkoutRestStatusConversation.answer(
                input = "Còn 30 giây",
                restIsActive = true,
                remainingMs = 20_000L
            )
        )
    }

    @Test fun invalidOrMissingCountdownIsReportedWithoutGuessing() {
        val answer = WorkoutRestStatusConversation.answer(
            input = "How much rest left?",
            restIsActive = true,
            remainingMs = 600_001L
        )
        assertTrue(answer!!.contains("Chibi chưa đọc được"))
    }

    @Test fun countdownIsBoundedToTheTenMinuteRestLimit() {
        assertEquals(
            "Còn khoảng 10 phút nghỉ. Đồng hồ vẫn chạy; Chibi chưa thay đổi giáo án.",
            WorkoutRestStatusConversation.answer(
                input = "Còn bao nhiêu phút nghỉ?",
                restIsActive = true,
                remainingMs = 600_000L
            )
        )
    }
}
