package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoiceCommandsTest {
    private val C = WorkoutVoiceCommands.Command

    @Test fun vietnameseDiacriticsAndEnglishCommands() {
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("Tạm dừng"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("pause"))
        assertEquals(C.RESUME, WorkoutVoiceCommands.parse("Bắt đầu"))
        assertEquals(C.RESUME, WorkoutVoiceCommands.parse("tiếp tục"))
        assertEquals(C.NEXT, WorkoutVoiceCommands.parse("Bài tiếp theo"))
        assertEquals(C.PREVIOUS, WorkoutVoiceCommands.parse("quay lại"))
        assertEquals(C.REPEAT, WorkoutVoiceCommands.parse("nhắc lại".replace("nhắc", "lặp")))
        assertEquals(C.EASIER, WorkoutVoiceCommands.parse("Nhẹ hơn"))
        assertEquals(C.REST, WorkoutVoiceCommands.parse("Nghỉ thêm"))
    }

    @Test fun safetyTakesPriorityOverStartAndNext() {
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("Tôi đang đau, cho bài tiếp theo"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("bị chấn thương, bắt đầu"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("dừng tập"))
    }

    @Test fun unknownOrOversizedInputNeverExecutesAction() {
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse(""))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("thời tiết hôm nay thế nào"))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("a".repeat(161)))
    }
}
