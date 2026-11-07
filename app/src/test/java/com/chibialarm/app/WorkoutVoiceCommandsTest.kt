package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test
import com.chibialarm.app.WorkoutVoiceCommands.Command as C

class WorkoutVoiceCommandsTest {
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
        assertEquals(C.SHORTER, WorkoutVoiceCommands.parse("Rút ngắn bài tập"))
        assertEquals(C.SHORTER, WorkoutVoiceCommands.parse("giam thoi gian"))
    }

    @Test fun safetyTakesPriorityOverStartAndNext() {
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Tôi đang đau, cho bài tiếp theo"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("bị chấn thương, bắt đầu"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("đang đau, rút ngắn bài tập"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("dừng tập"))
    }

    @Test fun shortSafetyReportsOverrideOtherActions() {
        for (report in listOf("Đau", "Tôi đau chân, tiếp tục", "khó thở, bài tiếp theo",
            "chóng mặt", "choáng váng, bắt đầu", "đau ngực", "ngất")) {
            assertEquals(report, C.PAIN, WorkoutVoiceCommands.parse(report))
        }
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("Tập ở đâu?"))
    }

    @Test fun unknownOrOversizedInputNeverExecutesAction() {
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse(""))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("thời tiết hôm nay thế nào"))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("a".repeat(161)))
    }
}
