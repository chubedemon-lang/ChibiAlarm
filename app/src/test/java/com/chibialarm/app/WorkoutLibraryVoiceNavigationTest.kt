package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutLibraryVoiceNavigationTest {
    @Test fun mapsVietnameseNavigationCommandsToBoundedWalkthroughActions() {
        assertEquals(WorkoutLibraryVoiceNavigation.Action.NEXT, WorkoutLibraryVoiceNavigation.action("bước tiếp theo"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.PREVIOUS, WorkoutLibraryVoiceNavigation.action("bước trước"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.FIRST, WorkoutLibraryVoiceNavigation.action("về bước đầu"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.REPEAT, WorkoutLibraryVoiceNavigation.action("đọc lại"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.CLOSE, WorkoutLibraryVoiceNavigation.action("đóng hướng dẫn"))
    }

    @Test fun mapsCatalogSpeechAndPreviewControlsWithoutStartingAWorkout() {
        assertEquals(WorkoutLibraryVoiceNavigation.Action.AUTO_SPEAK_ON, WorkoutLibraryVoiceNavigation.action("bật tự đọc"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.AUTO_SPEAK_OFF, WorkoutLibraryVoiceNavigation.action("tắt tự đọc"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.SPEAK_SUMMARY, WorkoutLibraryVoiceNavigation.action("đọc mục tiêu"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.SPEAK_EASIER, WorkoutLibraryVoiceNavigation.action("đọc cách tập dễ hơn"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.SPEAK_SAFETY_NOTE, WorkoutLibraryVoiceNavigation.action("đọc lưu ý an toàn"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.SPEAK_EXERCISE, WorkoutLibraryVoiceNavigation.action("cách tập bài này"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.OPEN_ALTERNATIVES, WorkoutLibraryVoiceNavigation.action("mở phương án thay thế"))
    }

    @Test fun safetySignalsTakePriorityOverNavigationCommands() {
        assertEquals(WorkoutLibraryVoiceNavigation.Action.SAFETY, WorkoutLibraryVoiceNavigation.action("đau, bước tiếp theo"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.SAFETY, WorkoutLibraryVoiceNavigation.action("khó thở, đọc lại"))
    }

    @Test fun negatedCommandsNeverAdvanceOrChangeThePreview() {
        assertEquals(WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED, WorkoutLibraryVoiceNavigation.action("không bước tiếp"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED, WorkoutLibraryVoiceNavigation.action("đừng đọc lại"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED, WorkoutLibraryVoiceNavigation.action("không mở phương án thay thế"))
    }

    @Test fun activeWorkoutOnlyCommandsAreNotExecutedInsideTheLibraryPreview() {
        assertEquals(WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED, WorkoutLibraryVoiceNavigation.action("tạm dừng buổi tập"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED, WorkoutLibraryVoiceNavigation.action("nghỉ thêm 30 giây"))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED, WorkoutLibraryVoiceNavigation.action(""))
        assertEquals(WorkoutLibraryVoiceNavigation.Action.UNSUPPORTED, WorkoutLibraryVoiceNavigation.action(null))
    }
}
