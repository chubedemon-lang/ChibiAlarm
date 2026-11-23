package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class FitnessCoachProfileReviewTest {
    @Test fun recognizesVietnameseAndUnaccentedProfileReviewRequests() {
        listOf(
            "Tóm tắt hồ sơ tập",
            "Đọc lại lựa chọn",
            "Nhắc lại hồ sơ hiện tại",
            "tom tat ho so tap cua tao",
            "doc lai cac lua chon"
        ).forEach { phrase ->
            assertTrue("Not recognized: $phrase", FitnessCoachProfileReview.isReviewRequest(phrase))
        }
    }

    @Test fun unrelatedWorkoutQuestionsAreNotCapturedAsProfileReview() {
        listOf(
            "Tóm tắt lịch sử tập",
            "Bài này cần dụng cụ gì?",
            "Hồ sơ này có tốt không?",
            "Tôi muốn tập 20 phút"
        ).forEach { phrase ->
            assertFalse("Unexpected profile review: $phrase", FitnessCoachProfileReview.isReviewRequest(phrase))
            assertNull(FitnessCoachProfileReview.answer(phrase, null))
        }
    }

    @Test fun reviewWithoutAProfileExplainsHowToStartWithoutInventingChoices() {
        val answer = FitnessCoachProfileReview.answer("Tóm tắt hồ sơ", null)!!
        assertTrue(answer.contains("chưa có lựa chọn"))
        assertTrue(answer.contains("Chưa có giáo án nào được tạo"))
    }

    @Test fun summaryShowsStructuredGoalLevelEnergyDurationEquipmentAndRestrictions() {
        val profile = FitnessNaturalRequest.Result(
            goal = 0,
            level = 1,
            energy = 0,
            minutes = 25,
            equipment = 4,
            impact = 1,
            reply = "PRIVATE_RAW_UTTERANCE",
            avoidWristLoad = true,
            avoidFloorPositions = false,
            avoidOverheadReach = true,
            additionalEquipment = setOf(TrainingEquipment.MAT, TrainingEquipment.STABLE_CHAIR),
            avoidKneeling = true
        )

        val answer = FitnessCoachProfileReview.answer("Đọc lại lựa chọn", profile)!!

        assertTrue(answer.contains("Tăng sức mạnh"))
        assertTrue(answer.contains("Đang tập đều"))
        assertTrue(answer.contains("Hơi mệt"))
        assertTrue(answer.contains("25 phút"))
        assertTrue(answer.contains("Tạ đơn"))
        assertTrue(answer.contains("Thảm tập"))
        assertTrue(answer.contains("Ghế vững chắc"))
        assertTrue(answer.contains("Ưu tiên ít tác động"))
        assertTrue(answer.contains("Tránh chống tay"))
        assertTrue(answer.contains("Tránh quỳ gối"))
    }

    @Test fun incompleteProfileNamesMissingChoicesAndTheNextQuestion() {
        val profile = FitnessNaturalRequest.Result(
            goal = 2,
            reply = "private text",
            minutes = 15
        )

        val answer = FitnessCoachProfileReview.answer("Xem lại hồ sơ", profile)!!

        assertTrue(answer.contains("Trình độ: Chưa chọn"))
        assertTrue(answer.contains("Dụng cụ chính: Chưa chọn"))
        assertTrue(answer.contains("Còn cần làm rõ"))
        assertTrue(answer.contains("trình độ"))
        assertTrue(answer.contains("Câu hỏi tiếp theo"))
    }

    @Test fun painOrInjurySummaryStopsPlanCreationWithoutSuggestingAWorkout() {
        val profile = FitnessNaturalRequest.Result(
            goal = 0,
            level = 1,
            energy = 1,
            minutes = 20,
            equipment = 0,
            impact = 2,
            reply = "private text",
            avoidWristLoad = true,
            avoidFloorPositions = true,
            avoidOverheadReach = false,
            additionalEquipment = emptySet()
        )

        val answer = FitnessCoachProfileReview.answer("Tóm tắt hồ sơ", profile)!!

        assertTrue(answer.contains("không tạo hoặc khởi chạy giáo án"))
        assertTrue(answer.contains("đã báo đau hoặc chấn thương"))
        assertFalse(answer.contains("xác nhận trước khi bắt đầu"))
    }

    @Test fun summaryNeverEchoesRawUtteranceOrFreeTextReply() {
        val privateText = "PRIVATE_RAW_UTTERANCE_do_not_echo"
        val profile = FitnessNaturalRequest.Result(
            goal = 1,
            reply = privateText
        )

        val answer = FitnessCoachProfileReview.answer(
            "Tóm tắt hồ sơ",
            profile.copy(reply = privateText)
        )!!

        assertFalse(answer.contains(privateText))
        assertTrue(answer.contains("Mục tiêu: Tăng vận động tim mạch"))
    }
}
