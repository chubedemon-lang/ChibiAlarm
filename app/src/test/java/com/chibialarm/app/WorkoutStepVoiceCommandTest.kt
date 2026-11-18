package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepVoiceCommandTest {
    @Test fun recognizesVietnameseNavigationWithAndWithoutDiacritics() {
        assertEquals(WorkoutStepVoiceCommand.Command.NEXT, WorkoutStepVoiceCommand.parse("Bước tiếp"))
        assertEquals(WorkoutStepVoiceCommand.Command.NEXT, WorkoutStepVoiceCommand.parse("tiep theo"))
        assertEquals(WorkoutStepVoiceCommand.Command.PREVIOUS, WorkoutStepVoiceCommand.parse("Quay lại bước trước"))
        assertEquals(WorkoutStepVoiceCommand.Command.REPEAT, WorkoutStepVoiceCommand.parse("Đọc lại"))
        assertEquals(WorkoutStepVoiceCommand.Command.CLOSE, WorkoutStepVoiceCommand.parse("Đóng hướng dẫn"))
        assertEquals(WorkoutStepVoiceCommand.Command.SUMMARY, WorkoutStepVoiceCommand.parse("Đọc mục tiêu"))
        assertEquals(WorkoutStepVoiceCommand.Command.SUMMARY, WorkoutStepVoiceCommand.parse("doc so hiep"))
        assertEquals(WorkoutStepVoiceCommand.Command.SUMMARY, WorkoutStepVoiceCommand.parse("Mục tiêu bài tập"))
    }

    @Test fun canRestartOnlyTheGuideAtItsFirstStepAndKeepPainPriority() {
        assertEquals(WorkoutStepVoiceCommand.Command.FIRST,
            WorkoutStepVoiceCommand.parse("Quay về bước đầu"))
        assertEquals(WorkoutStepVoiceCommand.Command.FIRST,
            WorkoutStepVoiceCommand.parse("Bước đầu tiên"))
        assertEquals(WorkoutStepVoiceCommand.Command.FIRST,
            WorkoutStepVoiceCommand.parse("buoc dau tien"))
        assertEquals(WorkoutStepVoiceCommand.Command.FIRST,
            WorkoutStepVoiceCommand.parse("Start guide over"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Không quay về bước đầu"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("khong quay ve buoc dau"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("dau co tay"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("buoc dau tien nhung dau co tay"))
    }

    @Test fun guideCanReadEasierOptionsAndExerciseSafetyNotes() {
        assertEquals(WorkoutStepVoiceCommand.Command.EASIER,
            WorkoutStepVoiceCommand.parse("Đọc cách tập dễ hơn"))
        assertEquals(WorkoutStepVoiceCommand.Command.EASIER,
            WorkoutStepVoiceCommand.parse("doc phuong an nhe hon"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY_NOTE,
            WorkoutStepVoiceCommand.parse("Đọc lưu ý an toàn"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY_NOTE,
            WorkoutStepVoiceCommand.parse("nhắc lại lưu ý"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("không đọc lưu ý"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("không đọc cách tập dễ hơn"))
    }

    @Test fun symptomReportsStillOverrideRequestsToReadNotes() {
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Đọc lưu ý an toàn, tôi đau cổ tay"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Đọc cách dễ hơn nhưng tôi khó thở"))
    }

    @Test fun safetyLanguageTakesPriorityOverNavigation() {
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY, WorkoutStepVoiceCommand.parse("Bước tiếp nhưng tôi khó thở"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY, WorkoutStepVoiceCommand.parse("đau cổ tay"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY, WorkoutStepVoiceCommand.parse("dau lung"))
        assertEquals(
            WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Đọc mục tiêu nhưng tôi đau cổ tay")
        )
    }

    @Test fun unaccentedBodyPartWordIsNotMistakenForPain() {
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("đầu gối hướng về phía trước"))
    }

    @Test fun recognizesExplicitRestExtensionWithoutTreatingNegationAsACommand() {
        assertEquals(WorkoutStepVoiceCommand.Command.REST, WorkoutStepVoiceCommand.parse("Nghỉ thêm 30 giây"))
        assertEquals(WorkoutStepVoiceCommand.Command.REST, WorkoutStepVoiceCommand.parse("nghi them 1 phut"))
        assertEquals(WorkoutStepVoiceCommand.Command.REST, WorkoutStepVoiceCommand.parse("extend rest 45 seconds"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("không nghỉ thêm"))
    }

    @Test fun safetyOverridesRestExtension() {
        assertEquals(
            WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("nghỉ thêm 30 giây nhưng tôi khó thở")
        )
    }

    @Test fun asksQuestionsAboutTheCurrentlyOpenExercise() {
        assertEquals(WorkoutStepVoiceCommand.Command.ASK_EXERCISE,
            WorkoutStepVoiceCommand.parse("Cách tập bài này?"))
        assertEquals(WorkoutStepVoiceCommand.Command.ASK_EXERCISE,
            WorkoutStepVoiceCommand.parse("Bao nhiêu lần?"))
        assertEquals(WorkoutStepVoiceCommand.Command.ASK_EXERCISE,
            WorkoutStepVoiceCommand.parse("Nghỉ bao lâu?"))
        assertEquals(WorkoutStepVoiceCommand.Command.ASK_EXERCISE,
            WorkoutStepVoiceCommand.parse("cach tap nhu the nao"))
    }

    @Test fun explicitGuideCommandsAndSafetyKeepPriorityOverQuestions() {
        assertEquals(WorkoutStepVoiceCommand.Command.EASIER,
            WorkoutStepVoiceCommand.parse("Đọc cách tập dễ hơn"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY_NOTE,
            WorkoutStepVoiceCommand.parse("Đọc lưu ý an toàn"))
        assertEquals(WorkoutStepVoiceCommand.Command.REST,
            WorkoutStepVoiceCommand.parse("Nghỉ thêm 30 giây"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Cách tập bài này nhưng tôi đau cổ tay"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Bao nhiêu lần nhưng tôi khó thở"))
    }

    @Test fun canIncreaseWorkoutSpeechRateByVoiceWithoutChangingWorkoutCommands() {
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_FASTER,
            WorkoutStepVoiceCommand.parse("Nói nhanh hơn"))
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_FASTER,
            WorkoutStepVoiceCommand.parse("doc nhanh hon"))
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_FASTER,
            WorkoutStepVoiceCommand.parse("Tăng tốc độ đọc"))
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_FASTER,
            WorkoutStepVoiceCommand.parse("Speak faster"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Không đọc nhanh hơn"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Đừng tăng tốc độ"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Nói nhanh hơn nhưng tôi đau cổ tay"))
    }

    @Test fun canPauseAndResumeTheWorkoutFromTheStepGuideVoiceCommand() {
        assertEquals(WorkoutStepVoiceCommand.Command.PAUSE,
            WorkoutStepVoiceCommand.parse("Tạm dừng buổi tập"))
        assertEquals(WorkoutStepVoiceCommand.Command.PAUSE,
            WorkoutStepVoiceCommand.parse("tam dung buoi tap"))
        assertEquals(WorkoutStepVoiceCommand.Command.PAUSE,
            WorkoutStepVoiceCommand.parse("Pause workout"))
        assertEquals(WorkoutStepVoiceCommand.Command.RESUME,
            WorkoutStepVoiceCommand.parse("Tiếp tục buổi tập"))
        assertEquals(WorkoutStepVoiceCommand.Command.RESUME,
            WorkoutStepVoiceCommand.parse("tiep tuc buoi tap"))
        assertEquals(WorkoutStepVoiceCommand.Command.RESUME,
            WorkoutStepVoiceCommand.parse("Resume workout"))

        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Không tạm dừng buổi tập"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Đừng tiếp tục buổi tập"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Tiếp tục buổi tập nhưng tôi khó thở"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Tạm dừng buổi tập, tôi đau cổ tay"))
    }

    @Test fun canReplayTheLatestCoachAnswerWithoutRepeatingTheCurrentStep() {
        assertEquals(WorkoutStepVoiceCommand.Command.REPEAT_ANSWER,
            WorkoutStepVoiceCommand.parse("Đọc lại câu trả lời"))
        assertEquals(WorkoutStepVoiceCommand.Command.REPEAT_ANSWER,
            WorkoutStepVoiceCommand.parse("nhac lai cau tra loi"))
        assertEquals(WorkoutStepVoiceCommand.Command.REPEAT,
            WorkoutStepVoiceCommand.parse("Đọc lại"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Đọc lại câu trả lời, tôi đau cổ tay"))
    }

    @Test fun recognizesQuestionsAboutExerciseEquipmentWithAndWithoutDiacritics() {
        assertEquals(
            WorkoutStepVoiceCommand.Command.ASK_EXERCISE,
            WorkoutStepVoiceCommand.parse("Bài này cần dụng cụ gì?")
        )
        assertEquals(
            WorkoutStepVoiceCommand.Command.ASK_EXERCISE,
            WorkoutStepVoiceCommand.parse("co can ta khong")
        )
        assertEquals(
            WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Cần dụng cụ gì nhưng tôi đau cổ tay")
        )
    }

    @Test fun incompleteOrUnrelatedPhrasesDoNotMoveTheGuide() {
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("tiếp tục tập"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("nói to hơn"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("mục tiêu tuần"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse(""))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse(null))
    }
    @Test fun recognizesNaturalQuestionsAboutTheCurrentExerciseFocus() {
        for (phrase in listOf(
            "Bài này tác động nhóm cơ nào?",
            "Nhóm cơ chính?",
            "Tập vào đâu?",
            "Tác dụng của bài này là gì?"
        )) {
            assertEquals(
                WorkoutStepVoiceCommand.Command.ASK_EXERCISE,
                WorkoutStepVoiceCommand.parse(phrase)
            )
        }
    }

    @Test fun canAdjustWorkoutSpeechRateByVoiceWithoutChangingWorkoutCommands() {
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_SLOWER,
            WorkoutStepVoiceCommand.parse("Nói chậm lại"))
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_SLOWER,
            WorkoutStepVoiceCommand.parse("doc cham hon"))
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_SLOWER,
            WorkoutStepVoiceCommand.parse("Speak slower"))
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_NORMAL,
            WorkoutStepVoiceCommand.parse("Tốc độ bình thường"))
        assertEquals(WorkoutStepVoiceCommand.Command.SPEECH_NORMAL,
            WorkoutStepVoiceCommand.parse("noi nhu cu"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Không nói chậm lại"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Giữ tốc độ hiện tại"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Nói chậm lại, tôi đau cổ tay"))
    }

    @Test fun canEnableAndDisableAutomaticStepNarrationByVoice() {
        assertEquals(WorkoutStepVoiceCommand.Command.AUTO_SPEAK_ON,
            WorkoutStepVoiceCommand.parse("Bật tự đọc"))
        assertEquals(WorkoutStepVoiceCommand.Command.AUTO_SPEAK_ON,
            WorkoutStepVoiceCommand.parse("Tự đọc bước khi chuyển"))
        assertEquals(WorkoutStepVoiceCommand.Command.AUTO_SPEAK_ON,
            WorkoutStepVoiceCommand.parse("Turn on auto speak"))
        assertEquals(WorkoutStepVoiceCommand.Command.AUTO_SPEAK_OFF,
            WorkoutStepVoiceCommand.parse("Tắt tự đọc"))
        assertEquals(WorkoutStepVoiceCommand.Command.AUTO_SPEAK_OFF,
            WorkoutStepVoiceCommand.parse("Tắt đọc tự động"))
        assertEquals(WorkoutStepVoiceCommand.Command.AUTO_SPEAK_OFF,
            WorkoutStepVoiceCommand.parse("Disable auto narration"))
    }

    @Test fun autoNarrationNegationAndSafetyTakePriority() {
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Không tự đọc"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN,
            WorkoutStepVoiceCommand.parse("Đừng bật tự đọc"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Bật tự đọc nhưng tôi đau cổ tay"))
        assertEquals(WorkoutStepVoiceCommand.Command.SAFETY,
            WorkoutStepVoiceCommand.parse("Tắt tự đọc, tôi khó thở"))
    }
}
