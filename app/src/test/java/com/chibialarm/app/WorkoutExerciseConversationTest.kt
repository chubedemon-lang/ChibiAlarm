package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutExerciseConversationTest {
    @Test fun exactExerciseQuestionReturnsDoseStepsRestAndSafetyCopy() {
        val answer = WorkoutExerciseConversation.answer("Cách tập Squat không tạ?")

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("2–3 hiệp × 8–12 lần"))
        assertTrue(answer.contains("Nghỉ giữa hiệp đề xuất: 60 giây"))
        assertTrue(answer.contains("1. Chân rộng bằng vai"))
        assertTrue(answer.contains("Dễ hơn:"))
        assertTrue(answer.contains("Lưu ý:"))
        assertTrue(answer.contains("không phân tích tư thế thực tế"))
    }

    @Test fun partialCommonNameAsksForDisambiguationInsteadOfGuessing() {
        val answer = WorkoutExerciseConversation.answer("Cách tập squat?")

        assertNotNull(answer)
        assertTrue(answer!!.contains("vài động tác gần giống"))
        assertTrue(answer.contains("Squat không tạ"))
        assertTrue(answer.contains("Squat chậm 3 giây"))
    }

    @Test fun unaccentedSpeechRecognitionCanFindAnExercise() {
        val answer = WorkoutExerciseConversation.answer("cach tap plank nhu the nao")

        assertNotNull(answer)
        assertTrue(answer!!.contains("Plank khuỷu hoặc plank gối"))
        assertTrue(answer.contains("Các bước:"))
    }

    @Test fun timeBasedMoveDoesNotInventASetRestInterval() {
        val answer = WorkoutExerciseConversation.answer("Hướng dẫn cách tập Đi bộ nhanh tại chỗ")

        assertNotNull(answer)
        assertTrue(answer!!.contains("Đi bộ nhanh tại chỗ"))
        assertTrue(answer.contains("3–6 phút"))
        assertFalse(answer.contains("Nghỉ giữa hiệp đề xuất"))
    }

    @Test fun missingExerciseNameAsksForTheName() {
        val answer = WorkoutExerciseConversation.answer("Cách tập đúng?")

        assertNotNull(answer)
        assertTrue(answer!!.contains("nêu rõ tên bài"))
    }

    @Test fun ordinaryPlanRequestIsLeftForThePlanConversation() {
        assertNull(
            WorkoutExerciseConversation.answer(
                "Hôm nay tao muốn tập squat 20 phút không dụng cụ"
            )
        )
    }

    @Test fun oversizedInputIsIgnored() {
        assertNull(WorkoutExerciseConversation.answer("Cách tập squat " + "x".repeat(400)))
    }
}
