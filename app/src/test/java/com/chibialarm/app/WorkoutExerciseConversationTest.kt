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
        assertFalse(answer.contains("Squat không tạ"))
        assertFalse(answer.contains("Plank"))
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
        assertTrue(technique!!.contains("đẩy hông nhẹ về sau"))
        assertTrue(technique.contains("Hình minh họa không đánh giá tư thế"))
        assertNotNull(rest)
        assertTrue(rest!!.contains("Squat không tạ"))
        assertTrue(rest.contains("60 giây"))
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

    @Test fun contextualEquipmentQuestionListsAlternativesWithoutTreatingThemAsCumulative() {
        val row = TrainingCatalog.allMoves.single {
            it.name == "Kéo tạ đơn hoặc dây kháng lực"
        }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Bài này cần dụng cụ gì?",
            row
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("một trong các lựa chọn"))
        assertTrue(answer.contains("tạ đơn nhẹ"))
        assertTrue(answer.contains("dây kháng lực"))
        assertTrue(answer.contains("Không cần có tất cả"))
    }

    @Test fun bodyweightMoveDoesNotInventAnEquipmentRequirement() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Có cần tạ không?",
            squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("không yêu cầu dụng cụ riêng"))
        assertFalse(answer.contains("tạ đơn nhẹ"))
    }

    @Test fun unknownMoveDoesNotClaimThatNoEquipmentIsNeeded() {
        val unknown = TrainingMove(
            name = "Động tác thử nghiệm",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện chậm.",
            easier = "Giảm biên độ.",
            avoid = "Dừng nếu đau."
        )
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Cần dụng cụ gì?",
            unknown
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa ghi rõ dụng cụ"))
    }

    @Test fun explicitExerciseBreathingQuestionUsesTheCatalogMovementPattern() {
        val answer = WorkoutExerciseConversation.answer("Squat không tạ thở thế nào?")
        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("thở ra nhẹ ở pha dùng lực", ignoreCase = true))
        assertTrue(answer.contains("hít vào khi trở về hoặc hạ xuống", ignoreCase = true))
        assertTrue(answer.contains("không nín thở", ignoreCase = true))
    }

    @Test fun contextualBreathingFollowUpNeedsNoRepeatedExerciseName() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Khi nào hít vào, thở ra?", squat
        )
        assertNotNull(answer)
        assertTrue(answer!!.contains("Squat không tạ"))
        assertTrue(answer.contains("hít vào khi trở về hoặc hạ xuống"))
    }

    @Test fun cardioBreathingAdviceEncouragesAComfortableConversationalPace() {
        val walk = TrainingCatalog.allMoves.single { it.name == "Đi bộ nhanh tại chỗ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove("Cách thở khi tập?", walk)
        assertNotNull(answer)
        assertTrue(answer!!.contains("Thở tự nhiên"))
        assertTrue(answer.contains("nói chuyện được"))
    }

    @Test fun unknownMoveDoesNotReceiveAnInventedExerciseSpecificBreathingPattern() {
        val unknown = TrainingMove(
            name = "Động tác thử nghiệm",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện chậm.",
            easier = "Giảm biên độ.",
            avoid = "Dừng nếu đau."
        )
        val answer = WorkoutExerciseConversation.answerForCurrentMove("Thở thế nào?", unknown)
        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa có gợi ý nhịp thở riêng"))
        assertFalse(answer.contains("Squat"))
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
    @Test fun contextualFocusQuestionUsesOnlyTheCuratedExerciseNote() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Bài này tác động nhóm cơ nào?",
            squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("đùi trước"))
        assertTrue(answer.contains("cơ mông"))
        assertTrue(answer.contains("không phải đánh giá tư thế"))
    }

    @Test fun generalCatalogAnswerIncludesFocusWhenAvailable() {
        val answer = WorkoutExerciseConversation.answer("Cách tập Squat không tạ?")

        assertNotNull(answer)
        assertTrue(answer!!.contains("Nhóm cơ chính"))
        assertTrue(answer.contains("đùi trước"))
    }

    @Test fun unknownMoveDoesNotInventExerciseFocus() {
        val unknown = TrainingMove(
            name = "Động tác thử nghiệm",
            dose = "2 hiệp × 8 lần",
            steps = "Thực hiện chậm.",
            easier = "Giảm biên độ.",
            avoid = "Dừng nếu đau."
        )
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Nhóm cơ nào?",
            unknown
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa có mô tả nhóm cơ"))
    }

    @Test fun explicitNamedMoveQuestionReturnsOnlyTheRequestedStep() {
        val answer = WorkoutExerciseConversation.answer("Bước 3 của Squat không tạ là gì?")

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 3/5 của Squat không tạ"))
        assertTrue(answer.contains("hạ xuống theo khả năng"))
        assertFalse(answer.contains("đứng lên"))
    }

    @Test fun contextualOrdinalQuestionUsesTheOpenExerciseWithoutRepeatingItsName() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Bước thứ hai của bài này là gì?",
            squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 2/5 của Squat không tạ"))
        assertTrue(answer.contains("đẩy hông nhẹ về sau"))
        assertFalse(answer.contains("Chân rộng bằng vai"))
    }

    @Test fun firstAndLastAliasesResolveTheCatalogBoundaries() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val first = WorkoutExerciseConversation.answerForCurrentMove("Bước đầu tiên của bài này", squat)
        val last = WorkoutExerciseConversation.answerForCurrentMove("Đọc bước cuối của bài này", squat)

        assertTrue(first!!.contains("Bước 1/5"))
        assertTrue(first.contains("Chân rộng bằng vai"))
        assertTrue(last!!.contains("Bước 5/5"))
        assertTrue(last.contains("giữ gối cùng hướng bàn chân"))
    }

    @Test fun outOfRangeStepIsNotClampedToAValidButDifferentInstruction() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove("Bước 9 của bài này là gì?", squat)

        assertNotNull(answer)
        assertTrue(answer!!.contains("có 5 bước"))
        assertTrue(answer.contains("không có bước 9"))
        assertFalse(answer.contains("Bước 5/5"))
    }

    @Test fun multipleAdjacentRequestedStepsReturnBothCatalogInstructions() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove(
            "Bước 2 và bước 3 của bài này là gì?",
            squat
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 2/5 của Squat không tạ:"))
        assertTrue(answer.contains("Bước 3/5 của Squat không tạ:"))
        assertFalse(answer.contains("Bước 1/5"))
    }

    @Test fun vagueNextStepDoesNotInventAnInstructionIndex() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        assertFalse(WorkoutExerciseStepQuestion.hasExplicitStepReference("Bước tiếp theo là gì?"))
        assertNull(WorkoutExerciseStepQuestion.answer("Bước tiếp theo là gì?", squat.name, squat.steps))
    }


    @Test fun unknownNamedExerciseDoesNotBecomeAContextualFollowUp() {
        assertTrue(
            WorkoutExerciseConversation.hasUnknownExplicitExerciseName(
                "Cách tập Burpee biến thể lạ?"
            )
        )
        assertFalse(
            WorkoutExerciseConversation.isContextualQuestion(
                "Cách tập Burpee biến thể lạ?"
            )
        )
        assertFalse(
            WorkoutExerciseConversation.hasUnknownExplicitExerciseName(
                "Cách tập bài này?"
            )
        )
        assertFalse(
            WorkoutExerciseConversation.hasUnknownExplicitExerciseName(
                "Cách tập Squat không tạ?"
            )
        )
    }

    @Test fun unknownVariationSharingCatalogPrefixIsNotReportedAsAmbiguous() {
        val answer = WorkoutExerciseConversation.answer("Cách tập Burpee biến thể lạ?")
        assertNotNull(answer)
        assertTrue(answer!!.contains("chưa nhận ra tên động tác"))
        assertFalse(answer.contains("vài động tác gần giống"))
    }

    @Test fun namedStepQuestionAllowsNaturalVietnamesePreposition() {
        val answer = WorkoutExerciseConversation.answer("Bước 3 trong Squat không tạ là gì?")
        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 3/5 của Squat không tạ"))
        assertTrue(answer.contains("hạ xuống theo khả năng"))
        assertFalse(answer.contains("đứng lên"))
    }

    @Test fun bareStepNumberIsNotAnsweredAsAQuestionForTheOpenExercise() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }

        assertFalse(WorkoutExerciseStepQuestion.isQuestion("Bước 2"))
        assertNull(WorkoutExerciseStepQuestion.answer("Bước 2", squat.name, squat.steps))
        assertNull(WorkoutExerciseConversation.answerForCurrentMove("Bước 2", squat))
    }

    @Test fun contextualStepReferenceWithoutQuestionSuffixStillGetsAnswered() {
        val squat = TrainingCatalog.allMoves.single { it.name == "Squat không tạ" }
        val answer = WorkoutExerciseConversation.answerForCurrentMove("Bước 2 của bài này", squat)

        assertNotNull(answer)
        assertTrue(answer!!.contains("Bước 2/5 của Squat không tạ"))
        assertTrue(answer.contains("đẩy hông nhẹ về sau"))
    }

}
