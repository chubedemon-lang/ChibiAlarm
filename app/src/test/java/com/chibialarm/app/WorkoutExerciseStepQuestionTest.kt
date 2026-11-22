package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExerciseStepQuestionTest {
    private val instructions = "Chuẩn bị tư thế.\nHạ người chậm và có kiểm soát.\nTrở về vị trí ban đầu."

    @Test fun recognizesNumberedAndOrdinalQuestionsWithVietnameseAndAsciiSpeech() {
        assertTrue(WorkoutExerciseStepQuestion.isQuestion("Bước 2 là gì?"))
        assertTrue(WorkoutExerciseStepQuestion.isQuestion("Bước thứ hai của bài này là gì?"))
        assertTrue(WorkoutExerciseStepQuestion.isQuestion("Đọc bước 3"))
        assertTrue(WorkoutExerciseStepQuestion.isQuestion("Bước đầu là gì?"))
        assertTrue(WorkoutExerciseStepQuestion.isQuestion("buoc thu hai la gi"))
        assertTrue(WorkoutExerciseStepQuestion.isQuestion("buoc dau la gi"))
    }

    @Test fun aBareStepReferenceDoesNotAccidentallyChangeTheGuide() {
        assertFalse(WorkoutExerciseStepQuestion.isQuestion("Bước 2"))
        assertFalse(WorkoutExerciseStepQuestion.isQuestion("Bước tiếp"))
        assertFalse(WorkoutExerciseStepQuestion.isQuestion("Bước thứ hai nhé"))
        assertNull(WorkoutExerciseStepQuestion.answer("Cách tập bài này?", "Squat", instructions))
    }

    @Test fun returnsOnlyTheRequestedCatalogStepForTts() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "Bước thứ hai của bài này là gì?",
            "Squat không tạ",
            instructions
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 2/3 của Squat không tạ"))
        assertTrue(answer.contains("Hạ người chậm và có kiểm soát"))
        assertTrue(answer.contains("không đánh giá tư thế thực tế"))
        assertFalse(answer.contains("Chuẩn bị tư thế"))
        assertFalse(answer.contains("Trở về vị trí ban đầu"))
    }

    @Test fun unaccentedFirstStepQuestionIsNotMisclassifiedAsPain() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "buoc dau la gi",
            "Squat không tạ",
            instructions
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 1/3"))
        assertTrue(answer.contains("Chuẩn bị tư thế"))
    }

    @Test fun multipleRequestedStepsAskForClarificationInsteadOfGuessing() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "Bước 2 và bước 3 là gì?",
            "Squat không tạ",
            instructions
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("nhiều bước được nhắc tới"))
        assertTrue(answer.contains("hỏi một bước cụ thể"))
    }

    @Test fun outOfRangeStepIsNotSilentlyReplaced() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "Đọc bước 9",
            "Squat không tạ",
            instructions
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("không có bước 9"))
        assertTrue(answer.contains("không tự đổi sang bước khác"))
    }

    @Test fun rejectsOversizedVoiceInput() {
        assertFalse(WorkoutExerciseStepQuestion.isQuestion("Bước 2 là gì? " + "x".repeat(170)))
        assertNull(WorkoutExerciseStepQuestion.answer("Bước 2 là gì? " + "x".repeat(170), "Squat", instructions))
    }

    @Test fun answersTwoAdjacentRequestedStepsInOneBoundedCoachReply() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "Bước 2 và bước 3 là gì?",
            "Squat không tạ",
            instructions
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 2/3 của Squat không tạ: Hạ người chậm và có kiểm soát."))
        assertTrue(answer.contains("Bước 3/3 của Squat không tạ: Trở về vị trí ban đầu."))
        assertTrue(answer.contains("không đánh giá tư thế thực tế"))
        assertFalse(answer.contains("Chuẩn bị tư thế"))
    }

    @Test fun nonConsecutiveStepsAskForClarificationInsteadOfReadingAFragmentedList() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "Bước 1 và bước 3 là gì?",
            "Squat không tạ",
            instructions
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("nhiều bước được nhắc tới"))
        assertTrue(answer.contains("hai bước liền nhau"))
        assertFalse(answer.contains("Chuẩn bị tư thế"))
    }

    @Test fun aPairWithAnOutOfRangeStepNeverReturnsOnlyTheValidStep() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "Bước 2 và bước 3 là gì?",
            "Squat không tạ",
            "Chuẩn bị tư thế.\nHạ người chậm và có kiểm soát."
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("không thể đọc đủ bước 2 và 3"))
        assertTrue(answer.contains("không tự bỏ qua bước ngoài phạm vi"))
        assertFalse(answer.contains("Hạ người chậm và có kiểm soát"))
    }

    @Test fun moreThanTwoRequestedStepsAskForACompactRange() {
        val answer = WorkoutExerciseStepQuestion.answer(
            "Bước 1, bước 2 và bước 3 là gì?",
            "Squat không tạ",
            instructions
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("nhiều bước được nhắc tới"))
        assertTrue(answer.contains("tối đa hai bước liền nhau"))
    }
}
