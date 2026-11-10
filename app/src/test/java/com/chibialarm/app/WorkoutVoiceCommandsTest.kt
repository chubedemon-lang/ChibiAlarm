package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test
import com.chibialarm.app.WorkoutVoiceCommands.Command as C

class WorkoutVoiceCommandsTest {
    @Test fun vietnameseDiacriticsAndEnglishCommands() {
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("Tạm dừng"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("pause"))
        assertEquals(C.RESUME, WorkoutVoiceCommands.parse("Bắt đầu"))
        assertEquals(C.RESUME, WorkoutVoiceCommands.parse("bat dau"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Bắt đầu nhưng tôi đau chân"))
        assertEquals(C.RESUME, WorkoutVoiceCommands.parse("tiếp tục"))
        assertEquals(C.NEXT, WorkoutVoiceCommands.parse("Bài tiếp theo"))
        assertEquals(C.PREVIOUS, WorkoutVoiceCommands.parse("quay lại"))
        assertEquals(C.REPEAT, WorkoutVoiceCommands.parse("nhắc lại"))
        assertEquals(C.REPEAT, WorkoutVoiceCommands.parse("nhac lai huong dan"))
        assertEquals(C.NEXT, WorkoutVoiceCommands.parse("bỏ qua bài này"))
        assertEquals(C.NEXT, WorkoutVoiceCommands.parse("chuyển bài"))
        assertEquals(C.EASIER, WorkoutVoiceCommands.parse("Nhẹ hơn"))
        assertEquals(C.SWAP_EASIER, WorkoutVoiceCommands.parse("Đổi động tác"))
        assertEquals(C.SWAP_EASIER, WorkoutVoiceCommands.parse("Đổi sang bài nhẹ hơn"))
        assertEquals(C.REST, WorkoutVoiceCommands.parse("Nghỉ thêm"))
        assertEquals(C.SHORTER, WorkoutVoiceCommands.parse("Rút ngắn bài tập"))
        assertEquals(C.SHORTER, WorkoutVoiceCommands.parse("giam thoi gian"))
        assertEquals(C.SHORTER_REMAINING, WorkoutVoiceCommands.parse("Rút ngắn tất cả động tác còn lại"))
        assertEquals(C.SHORTER_REMAINING, WorkoutVoiceCommands.parse("Giảm thời gian toàn bộ phần sau"))
        assertEquals(C.SHORTER_REMAINING, WorkoutVoiceCommands.parse("rut ngan ca buoi"))
    }

    @Test fun safetyTakesPriorityOverStartAndNext() {
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Tôi đang đau, cho bài tiếp theo"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("bị chấn thương, bắt đầu"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("đang đau, rút ngắn bài tập"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("đau chân, rút ngắn tất cả còn lại"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("dừng tập"))
    }

    @Test fun shortSafetyReportsOverrideOtherActions() {
        for (report in listOf("Đau", "Tôi đau chân, tiếp tục", "khó thở, bài tiếp theo",
            "chóng mặt", "choáng váng, bắt đầu", "đau ngực", "ngất")) {
            assertEquals(report, C.PAIN, WorkoutVoiceCommands.parse(report))
        }
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("Tập ở đâu?"))
    }

    @Test fun tirednessAndSafetyAreActionableOffline() {
        for (phrase in listOf("Mệt rồi", "Hơi mệt", "Mệt quá", "Mệt lắm", "Tôi cần nghỉ")) {
            assertEquals(phrase, C.REST, WorkoutVoiceCommands.parse(phrase))
        }
        for (phrase in listOf("Không tập nổi", "Hết sức rồi")) {
            assertEquals(phrase, C.PAUSE, WorkoutVoiceCommands.parse(phrase))
        }
        for (phrase in listOf("Đau ở đầu gối", "Đau gối", "Đau lưng, tiếp tục", "Buồn nôn", "Muốn ngất", "Đau quá, bài tiếp theo", "Tê tay", "Tê chân")) {
            assertEquals(phrase, C.PAIN, WorkoutVoiceCommands.parse(phrase))
        }
        assertEquals(C.RESUME, WorkoutVoiceCommands.parse("Bắt đầu"))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("Tập ở đâu?"))
    }

    @Test fun combinedRestAndEasierIsOneExplicitPreviewedAction() {
        assertEquals(C.EASIER_AND_REST, WorkoutVoiceCommands.parse("Giảm độ khó squat và nghỉ thêm một phút"))
        assertEquals(C.EASIER_AND_REST, WorkoutVoiceCommands.parse("Nhẹ hơn và nghỉ 60 giây"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Đau gối, giảm độ khó và nghỉ một phút"))
        assertEquals(C.EASIER, WorkoutVoiceCommands.parse("Giảm độ khó"))
        assertEquals(C.REST, WorkoutVoiceCommands.parse("Nghỉ thêm 60 giây"))
    }

    @Test fun spokenRepsCannotOverrideSafetyPauseOrOtherWorkoutActions() {
        for (phrase in listOf(
            "Dừng tập và giảm còn 10 lần",
            "Tôi đau gối, giảm còn 6 lần",
            "Nghỉ thêm 30 giây và giảm còn 8 lần",
            "Bài tiếp theo, giảm còn 12 lần",
            "Tiếp tục và giảm còn 15 lần",
            "Đổi bài và giảm còn 8 lần"
        )) {
            org.junit.Assert.assertFalse(phrase, WorkoutVoiceCommands.mayEditReps(phrase))
        }
        org.junit.Assert.assertTrue(WorkoutVoiceCommands.mayEditReps("Giảm squat còn 6 lần"))
        org.junit.Assert.assertTrue(WorkoutVoiceCommands.mayEditReps("Giảm xuống hai mươi lăm lần"))
    }

    @Test fun unknownOrOversizedInputNeverExecutesAction() {
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse(""))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("thời tiết hôm nay thế nào"))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("a".repeat(161)))
    }
    @Test fun spokenActionPreviewCoversEveryCommand() {
        for (command in C.values()) {
            val preview = WorkoutVoiceCommands.describe(command)
            org.junit.Assert.assertTrue(command.name, preview.isNotBlank())
            org.junit.Assert.assertTrue(command.name, preview.length < 100)
        }
        assertEquals("Chưa hiểu yêu cầu; không tự thay đổi buổi tập",
            WorkoutVoiceCommands.describe(C.UNKNOWN))
        assertEquals("Dừng tập vì cảnh báo an toàn",
            WorkoutVoiceCommands.describe(WorkoutVoiceCommands.parse("đau ngực, tiếp tục")))
    }
}
