package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRestCompletionSpeechQueueTest {
    @Test fun keepsAnnouncementWhileMicrophoneIsActiveThenDeliversOnce() {
        val queue = WorkoutRestCompletionSpeechQueue()
        queue.enqueue("Đã hết giờ nghỉ. Bắt đầu hiệp tiếp theo khi sẵn sàng.")

        assertNull(queue.takeIfReady(microphoneActive = true, pausedForSafety = false, speechEngineReady = true))
        assertTrue(queue.pendingMessage!!.contains("Bắt đầu hiệp tiếp theo"))
        assertEquals(
            "Đã hết giờ nghỉ. Bắt đầu hiệp tiếp theo khi sẵn sàng.",
            queue.takeIfReady(microphoneActive = false, pausedForSafety = false, speechEngineReady = true)
        )
        assertNull(queue.pendingMessage)
        assertNull(queue.takeIfReady(microphoneActive = false, pausedForSafety = false, speechEngineReady = true))
    }

    @Test fun keepsAnnouncementUntilSpeechEngineIsReady() {
        val queue = WorkoutRestCompletionSpeechQueue()
        queue.enqueue("Đã hết giờ nghỉ.")

        assertNull(queue.takeIfReady(microphoneActive = false, pausedForSafety = false, speechEngineReady = false))
        assertEquals("Đã hết giờ nghỉ.", queue.pendingMessage)
        assertEquals(
            "Đã hết giờ nghỉ.",
            queue.takeIfReady(microphoneActive = false, pausedForSafety = false, speechEngineReady = true)
        )
    }

    @Test fun safetyPauseNeverConsumesTheAnnouncement() {
        val queue = WorkoutRestCompletionSpeechQueue()
        queue.enqueue("Đã hết giờ nghỉ. Tiếp tục khi sẵn sàng.")

        assertNull(queue.takeIfReady(microphoneActive = false, pausedForSafety = true, speechEngineReady = true))
        assertEquals("Đã hết giờ nghỉ. Tiếp tục khi sẵn sàng.", queue.pendingMessage)
    }

    @Test fun restoredMessageIsSanitizedAndBounded() {
        val queue = WorkoutRestCompletionSpeechQueue()
        queue.restore("  Đã hết giờ nghỉ.\n\u202eTiếp tục.\u0000 " + "x".repeat(400))

        val restored = queue.pendingMessage!!
        assertTrue(restored.startsWith("Đã hết giờ nghỉ. Tiếp tục."))
        assertTrue(restored.length <= 350)
        assertTrue(!restored.contains('\n'))
    }

    @Test fun blankOrControlOnlyMessagesAreNotQueued() {
        val queue = WorkoutRestCompletionSpeechQueue()
        queue.enqueue("  \n\u0000  ")
        assertNull(queue.pendingMessage)
    }
}
