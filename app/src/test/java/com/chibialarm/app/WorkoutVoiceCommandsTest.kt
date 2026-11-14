package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(C.REPEAT_STEP, WorkoutVoiceCommands.parse("Đọc lại bước vừa rồi"))
        assertEquals(C.REPEAT_STEP, WorkoutVoiceCommands.parse("Nhắc lại bước này"))
        assertEquals(C.REPEAT_STEP, WorkoutVoiceCommands.parse("Đọc lại bước đang xem"))
        assertEquals(C.REPEAT, WorkoutVoiceCommands.parse("Nhắc lại toàn bộ hướng dẫn"))
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

    @Test fun naturalDifficultyFeedbackOpensTheEasierCueInsteadOfUnknown() {
        for (phrase in listOf(
            "Bài này khó quá",
            "Khó quá",
            "Tôi không theo kịp",
            "Không theo nổi",
            "Động tác này quá khó"
        )) {
            assertEquals(phrase, C.EASIER, WorkoutVoiceCommands.parse(phrase))
            assertFalse("$phrase must not be treated as a repetition edit", WorkoutVoiceCommands.mayEditReps(phrase))
        }
    }

    @Test fun difficultyFeedbackCanCombineWithAnExplicitRestRequest() {
        assertEquals(
            C.EASIER_AND_REST,
            WorkoutVoiceCommands.parse("Bài này khó quá, nghỉ thêm 30 giây")
        )
        assertEquals(
            C.EASIER_AND_REST,
            WorkoutVoiceCommands.parse("Tôi không theo kịp, nghỉ thêm một phút")
        )
    }

    @Test fun breathingDifficultyStillTriggersSafetyBeforeAnEasierCue() {
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Khó thở, bài này khó quá"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Tôi khó thở, không theo kịp"))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("Bài này không khó"))
    }

    @Test fun noEquipmentVoiceRequestsShowTheCorrectPlanAdjustmentPreview() {
        val phrases = listOf(
            "Không có dụng cụ",
            "Không có tạ đơn",
            "Không có dây kháng lực",
            "Tao để tạ ở nhà rồi",
            "Đổi bài tập tay không"
        )
        for (phrase in phrases) {
            assertEquals(phrase, C.NO_EQUIPMENT, WorkoutVoiceCommands.parse(phrase))
            assertFalse(phrase, WorkoutVoiceCommands.mayEditReps(phrase))
        }
        assertEquals(
            "Đề xuất thay động tác cần tạ/dây ở phần chưa tập bằng biến thể không cần tải ngoài",
            WorkoutVoiceCommands.describe(C.NO_EQUIPMENT)
        )
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("Tao có tạ và dây kháng lực"))
    }

    @Test fun statusRequestsAreRecognizedWithoutChangingTheWorkout() {
        for (phrase in listOf(
            "Đọc tiến độ",
            "Tiến độ buổi tập",
            "Xem tiến độ hiện tại",
            "Còn bao nhiêu bài",
            "Tao đã làm bao nhiêu hiệp",
            "Còn bao nhiêu hiệp?",
            "Còn mấy hiệp?",
            "Mấy hiệp nữa?",
            "Tôi còn mấy hiệp?",
            "Số hiệp còn lại",
            "Còn bao lâu?",
            "Bao lâu nữa xong?",
            "Mấy phút nữa?",
            "Còn bao nhiêu phút?",
            "Còn bao nhiêu giây?",
            "Thời gian còn lại",
            "Ước tính thời gian còn lại",
            "Khi nào xong?",
            "progress",
            "status"
        )) {
            assertEquals(phrase, C.STATUS, WorkoutVoiceCommands.parse(phrase))
        }
        assertEquals("Đọc tiến độ, số hiệp và thời gian còn lại", WorkoutVoiceCommands.describe(C.STATUS))
    }

    @Test fun stepByStepGuideCanBeOpenedWithNaturalVietnameseCommands() {
        for (phrase in listOf(
            "Hướng dẫn từng bước",
            "Đọc từng bước",
            "Xem từng bước",
            "Mở hướng dẫn"
        )) {
            assertEquals(phrase, C.STEP_GUIDE, WorkoutVoiceCommands.parse(phrase))
        }
        assertEquals("Mở hướng dẫn từng bước cho động tác hiện tại",
            WorkoutVoiceCommands.describe(C.STEP_GUIDE))
        assertFalse(WorkoutVoiceCommands.mayEditReps("Hướng dẫn từng bước"))
        assertFalse(WorkoutVoiceCommands.mayEditReps("Đọc lại bước vừa rồi"))
        assertEquals("Đọc lại bước hướng dẫn đang xem", WorkoutVoiceCommands.describe(C.REPEAT_STEP))
    }

    @Test fun safetyAndPauseTakePriorityOverStepByStepGuide() {
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Đau gối, hướng dẫn từng bước"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("Tạm dừng và mở hướng dẫn"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Đau gối, đọc lại bước vừa rồi"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("Tạm dừng rồi nhắc lại bước này"))
    }

    @Test fun safetyAndPauseTakePriorityOverStatusRequests() {
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Đau gối, đọc tiến độ"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("Tạm dừng và đọc tiến độ"))
    }

    @Test fun safetyTakesPriorityOverStartAndNext() {
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Tôi đang đau, cho bài tiếp theo"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("bị chấn thương, bắt đầu"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("đang đau, rút ngắn bài tập"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("đau chân, rút ngắn tất cả còn lại"))
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("đau gối, đổi bài"))
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

    @Test fun explicitSetCompletionRequiresAnAffirmativeSetPhrase() {
        for (phrase in listOf(
            "Xong hiệp rồi",
            "Tôi đã hoàn thành hiệp này",
            "Đã tập xong hiệp một",
            "Xác nhận hiệp",
            "hoan thanh hiep"
        )) {
            assertEquals(phrase, C.COMPLETE_SET, WorkoutVoiceCommands.parse(phrase))
        }
        for (phrase in listOf(
            "Chưa xong hiệp",
            "Chưa hoàn thành hiệp",
            "Không xong hiệp",
            "Không hoàn thành hiệp",
            "Xong bài tập"
        )) {
            assertEquals(phrase, C.UNKNOWN, WorkoutVoiceCommands.parse(phrase))
        }
        assertEquals("Xác nhận hoàn thành hiệp hiện tại",
            WorkoutVoiceCommands.describe(C.COMPLETE_SET))
    }

    @Test fun safetyPauseAndStatusTakePriorityOverSetCompletion() {
        assertEquals(C.PAIN, WorkoutVoiceCommands.parse("Đau gối, xong hiệp"))
        assertEquals(C.PAUSE, WorkoutVoiceCommands.parse("Dừng tập rồi xong hiệp"))
        assertEquals(C.STATUS, WorkoutVoiceCommands.parse("Khi nào xong hiệp?"))
        assertFalse(WorkoutVoiceCommands.mayEditReps("Xong hiệp và giảm còn 10 lần"))
    }

    @Test fun unknownOrOversizedInputNeverExecutesAction() {
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse(""))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("thời tiết hôm nay thế nào"))
        assertEquals(C.UNKNOWN, WorkoutVoiceCommands.parse("a".repeat(161)))
    }

    @Test fun restPreviewIncludesTheDurationFromRecognizedSpeech() {
        assertEquals("Bắt đầu nghỉ riêng 75 giây",
            WorkoutVoiceCommands.describe(C.REST, "nghỉ 1 phút 15 giây"))
        assertEquals("Hiển thị cách tập dễ hơn và thêm 90 giây nghỉ",
            WorkoutVoiceCommands.describe(C.EASIER_AND_REST, "nhẹ hơn và nghỉ một phút rưỡi"))
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
