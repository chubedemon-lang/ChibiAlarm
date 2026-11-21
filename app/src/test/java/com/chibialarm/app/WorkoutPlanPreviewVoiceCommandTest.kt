package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutPlanPreviewVoiceCommandTest {
    @Test fun acceptsOnlyExplicitConfirmationPhrases() {
        assertEquals(
            WorkoutPlanPreviewVoiceCommand.Action.CONFIRM,
            WorkoutPlanPreviewVoiceCommand.parse("xác nhận áp dụng")
        )
        assertEquals(
            WorkoutPlanPreviewVoiceCommand.Action.CONFIRM,
            WorkoutPlanPreviewVoiceCommand.parse("đồng ý áp dụng")
        )
        assertEquals(
            WorkoutPlanPreviewVoiceCommand.Action.CONFIRM,
            WorkoutPlanPreviewVoiceCommand.parse("ap dung dieu chinh")
        )
    }

    @Test fun cancellationNeverAppliesThePreview() {
        listOf("hủy", "hủy thay đổi", "giữ nguyên", "không áp dụng", "để sau").forEach { phrase ->
            assertEquals(
                "phrase=$phrase",
                WorkoutPlanPreviewVoiceCommand.Action.CANCEL,
                WorkoutPlanPreviewVoiceCommand.parse(phrase)
            )
        }
    }

    @Test fun mixedOrUnclearCommandsFailClosed() {
        listOf(
            "xác nhận áp dụng rồi bước tiếp",
            "có lẽ áp dụng",
            "xác nhận",
            "áp dụng phương án 2",
            ""
        ).forEach { phrase ->
            assertEquals(
                "phrase=$phrase",
                WorkoutPlanPreviewVoiceCommand.Action.UNSUPPORTED,
                WorkoutPlanPreviewVoiceCommand.parse(phrase)
            )
        }
        assertEquals(
            WorkoutPlanPreviewVoiceCommand.Action.UNSUPPORTED,
            WorkoutPlanPreviewVoiceCommand.parse(null)
        )
    }

    @Test fun safetySignalsOutrankPreviewConfirmation() {
        assertEquals(
            WorkoutPlanPreviewVoiceCommand.Action.SAFETY,
            WorkoutPlanPreviewVoiceCommand.parse("xác nhận áp dụng nhưng tôi đau cổ tay")
        )
        assertEquals(
            WorkoutPlanPreviewVoiceCommand.Action.SAFETY,
            WorkoutPlanPreviewVoiceCommand.parse("khó thở")
        )
    }
}
