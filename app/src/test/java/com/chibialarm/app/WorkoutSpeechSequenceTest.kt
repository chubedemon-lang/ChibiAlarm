package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSpeechSequenceTest {
    @Test fun longCoachAnswerIsQueuedInOrderWithOnlyTheFirstChunkFlushing() {
        val answer = (1..8).joinToString(" ") { index ->
            "Bước $index: giữ nhịp thở đều và thực hiện chuyển động có kiểm soát."
        }

        val planned = WorkoutSpeechSequence.plan(answer)

        assertTrue(planned.size > 1)
        assertTrue(planned.first().flushQueue)
        assertTrue(planned.drop(1).all { !it.flushQueue })
        assertTrue(planned.all { it.text.length <= WorkoutSpeechText.DEFAULT_MAX_CHARS })
        assertEquals(WorkoutSpeechText.chunks(answer), planned.map { it.text })
        assertEquals(answer, planned.joinToString(" ") { it.text })
    }

    @Test fun appendedLongNarrationKeepsEveryChunkInAddMode() {
        val answer = (1..8).joinToString(" ") { index ->
            "Bước $index: giữ nhịp thở đều và thực hiện chuyển động có kiểm soát."
        }

        val planned = WorkoutSpeechSequence.plan(answer, flushFirst = false)

        assertTrue(planned.size > 1)
        assertTrue(planned.all { !it.flushQueue })
        assertEquals(WorkoutSpeechText.chunks(answer), planned.map { it.text })
        assertEquals(answer, planned.joinToString(" ") { it.text })
    }

    @Test fun blankCoachReplyDoesNotCreateAnEmptyTtsUtterance() {
        assertTrue(WorkoutSpeechSequence.plan("  \n  ").isEmpty())
    }

    @Test fun shortCoachReplyUsesOneFlushUtterance() {
        val planned = WorkoutSpeechSequence.plan("Nghỉ thêm nếu cần.")

        assertEquals(1, planned.size)
        assertTrue(planned.single().flushQueue)
        assertFalse(planned.single().text.isBlank())
    }
}
