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

    @Test fun incompleteOrUnrelatedPhrasesDoNotMoveTheGuide() {
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("tiếp tục tập"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("nói to hơn"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse("mục tiêu tuần"))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse(""))
        assertEquals(WorkoutStepVoiceCommand.Command.UNKNOWN, WorkoutStepVoiceCommand.parse(null))
    }
}
