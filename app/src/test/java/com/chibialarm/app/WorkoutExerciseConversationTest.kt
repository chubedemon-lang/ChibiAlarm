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

    @Test fun unaccentedGenericPlankQuestionAsksForSafeDisambiguation() {
        val answer = WorkoutExerciseConversation.answer("cach tap plank nhu the nao")

        assertNotNull(answer)
        assertTrue(answer!!.contains("vài động tác gần giống"))
        assertTrue(answer.contains("Plank khuỷu hoặc plank gối"))
        assertTrue(answer.contains("Side plank gối"))
    }

    @Test fun unaccentedFullExerciseNameReturnsItsGuidance() {
        val answer = WorkoutExerciseConversation.answer(
            "cach tap plank khuyu hoac plank goi nhu the nao"
        )

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

    @Test fun contextualQuestionUsesTheCurrentlySelectedMoveAndAdjustedDose() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            input = "Bao nhiêu lần?",
            move = squat,
            currentDose = "10 lần (mục tiêu đã chỉnh)"
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("10 lần (mục tiêu đã chỉnh)"))
        assertFalse(answer.contains("8–12 lần"))
        assertTrue(answer.contains("chỉ xác nhận hiệp"))
    }

    @Test fun contextualTechniqueAndRestQuestionsNeedNoRepeatedExerciseName() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val technique = WorkoutExerciseConversation.answerForCurrentMove(
            "Cách tập bài này?", squat
        )
        val rest = WorkoutExerciseConversation.answerForCurrentMove(
            "Nghỉ bao lâu?", squat
        )

        assertNotNull(technique)
        assertTrue(technique!!.contains("Đẩy hông nhẹ về sau"))
        assertTrue(technique.contains("Hình minh họa không đánh giá tư thế"))
        assertNotNull(rest)
        assertTrue(rest!!.contains("60 giây"))
        assertTrue(rest.contains("nghỉ lâu hơn"))
    }

    @Test fun contextualQuestionsDoNotInventSetCountsForTimedMoves() {
        val walk = TrainingCatalog.allMoves.single { it.name == "Đi bộ nhanh tại chỗ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Bao nhiêu lần?", walk, "3–6 phút"
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("3–6 phút"))
        assertTrue(answer.contains("không quy đổi"))
        assertTrue(answer.contains("không quy đổi bài này thành số hiệp hoặc số lần"))
        assertFalse(answer.contains("hiệp bắt buộc"))
        assertFalse(answer.contains("lần/hiệp"))
    }

    @Test fun unrelatedTextIsNotAnsweredAsAContextualExerciseQuestion() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        assertNull(WorkoutExerciseConversation.answerForCurrentMove("Tao muốn đổi bài", squat))
        assertNull(WorkoutExerciseConversation.answerForCurrentMove("x".repeat(200), squat))
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
