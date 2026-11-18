package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutEffortVoiceCommandTest {
    private fun report(level: WorkoutEffortFeedback.Level, vararg phrases: String) {
        phrases.forEach { phrase ->
            assertEquals(
                phrase,
                WorkoutEffortVoiceCommand.Result.Report(level),
                WorkoutEffortVoiceCommand.parse(phrase)
            )
        }
    }

    @Test fun recognizesTheFourExplicitEffortChoices() {
        report(
            WorkoutEffortFeedback.Level.EASY,
            "Nhẹ",
            "Còn dư sức",
            "Rất nhẹ"
        )
        report(
            WorkoutEffortFeedback.Level.MODERATE,
            "Vừa sức",
            "Bình thường",
            "Ổn rồi"
        )
        report(
            WorkoutEffortFeedback.Level.HARD,
            "Khó quá",
            "Hơi khó",
            "Cần nghỉ thêm"
        )
        report(
            WorkoutEffortFeedback.Level.VERY_HARD,
            "Quá sức",
            "Hết sức rồi",
            "Tạm dừng"
        )
    }

    @Test fun acceptsUnaccentedSpeechRecognitionVariants() {
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.MODERATE),
            WorkoutEffortVoiceCommand.parse("vua suc")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.HARD),
            WorkoutEffortVoiceCommand.parse("kho qua")
        )
    }

    @Test fun understandsNaturalVietnameseAnswersWithShortPoliteIntroductions() {
        report(
            WorkoutEffortFeedback.Level.MODERATE,
            "Tôi thấy hơi mệt nhưng vẫn ổn",
            "Mình cảm thấy mệt nhưng vẫn ổn",
            "Hiệp này hơi đuối nhưng vẫn ổn"
        )
        report(
            WorkoutEffortFeedback.Level.HARD,
            "Tôi thấy hơi khó nhưng vẫn làm được",
            "Cảm giác khá khó nhưng vẫn làm được",
            "Hiệp vừa rồi khá mệt, cần nghỉ thêm",
            "Mình thấy khá nặng nhưng vẫn kiểm soát được"
        )
        report(
            WorkoutEffortFeedback.Level.VERY_HARD,
            "Tôi thấy quá sức rồi, không thể tiếp tục",
            "Mình cảm thấy mệt đến mức không thể tiếp tục"
        )
    }

    @Test fun understandsMoreNaturalContextualIntroductionsWithoutBroadFuzzyMatching() {
        report(
            WorkoutEffortFeedback.Level.HARD,
            "Em thấy khá nặng nhưng vẫn kiểm soát được",
            "Bài này tôi thấy hơi mệt nhưng vẫn làm được",
            "Mức độ của tôi là khó quá, cần nghỉ thêm"
        )
        report(
            WorkoutEffortFeedback.Level.MODERATE,
            "Hôm nay mình thấy hơi đuối nhưng vẫn ổn",
            "Mức độ của mình là vừa sức"
        )
    }

    @Test fun conversationalVariantsAlsoWorkWithoutVietnameseDiacritics() {
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.MODERATE),
            WorkoutEffortVoiceCommand.parse("toi thay hoi met nhung van on")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.HARD),
            WorkoutEffortVoiceCommand.parse("minh cam thay hoi kho nhung van lam duoc")
        )
    }

    @Test fun distinguishesCommonQuaKhoPhrasingFromVeryHardAndSafety() {
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.HARD),
            WorkoutEffortVoiceCommand.parse("Quá khó")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.HARD),
            WorkoutEffortVoiceCommand.parse("Quá khó nhưng vẫn làm được")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.HARD),
            WorkoutEffortVoiceCommand.parse("Khá vất vả nhưng vẫn kiểm soát được")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.VERY_HARD),
            WorkoutEffortVoiceCommand.parse("Không còn sức để tiếp tục")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.VERY_HARD),
            WorkoutEffortVoiceCommand.parse("Không thể làm tiếp nữa")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.SafetyStop,
            WorkoutEffortVoiceCommand.parse("Quá khó nhưng khó thở")
        )
        assertNull(WorkoutEffortVoiceCommand.parse("Không quá khó"))
        assertNull(WorkoutEffortVoiceCommand.parse("Quá khó nhưng vừa sức"))
    }

    @Test fun understandsSetSpecificNaturalIntroductions() {
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.HARD),
            WorkoutEffortVoiceCommand.parse("Hiệp vừa rồi tôi cảm thấy quá khó")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.HARD),
            WorkoutEffortVoiceCommand.parse("Hiệp này mình thấy khá vất vả nhưng vẫn kiểm soát được")
        )
        assertEquals(
            WorkoutEffortVoiceCommand.Result.Report(WorkoutEffortFeedback.Level.VERY_HARD),
            WorkoutEffortVoiceCommand.parse("Mức độ hiệp này là quá sức")
        )
    }

    @Test fun acceptsAnExplicitSecondTurnConfirmationOnly() {
        for (phrase in listOf("Đúng rồi", "Chính xác", "Xác nhận", "Chuẩn rồi", "OK")) {
            assertEquals(
                phrase,
                WorkoutEffortVoiceCommand.Result.Confirm,
                WorkoutEffortVoiceCommand.parse(phrase)
            )
        }
    }

    @Test fun correctionDiscardsThePendingRecognitionInsteadOfSavingIt() {
        for (phrase in listOf("Chưa đúng", "Sai rồi", "Nghe nhầm", "Chọn lại", "Để tôi nói lại")) {
            assertEquals(
                phrase,
                WorkoutEffortVoiceCommand.Result.Revise,
                WorkoutEffortVoiceCommand.parse(phrase)
            )
        }
    }

    @Test fun confirmationAndCorrectionNeverOverrideSafetySymptoms() {
        for (phrase in listOf("Đau gối, đúng rồi", "Khó thở, xác nhận", "Chóng mặt, chọn lại")) {
            assertEquals(
                phrase,
                WorkoutEffortVoiceCommand.Result.SafetyStop,
                WorkoutEffortVoiceCommand.parse(phrase)
            )
        }
    }

    @Test fun safetySymptomsOverrideNaturalEffortPhrases() {
        for (phrase in listOf(
            "Đau gối",
            "Đau đầu gối",
            "Khó thở nhưng vẫn vừa sức",
            "Chóng mặt, hơi khó",
            "Đau ngực",
            "Tôi thấy hơi khó nhưng đau gối",
            "Mình mệt nhưng khó thở"
        )) {
            assertEquals(
                phrase,
                WorkoutEffortVoiceCommand.Result.SafetyStop,
                WorkoutEffortVoiceCommand.parse(phrase)
            )
        }
    }

    @Test fun ambiguousNegatedMixedAndUnrelatedPhrasesAreRejected() {
        for (phrase in listOf(
            "",
            "không khó",
            "không mệt",
            "đầu gối",
            "vừa sức nhưng quá sức",
            "Tôi thấy vừa sức nhưng quá sức",
            "khó quá, nghỉ thêm 30 giây",
            "nghỉ thêm 30 giây",
            "tăng lên 10 lần",
            "xong hiệp",
            "x".repeat(161)
        )) {
            assertNull(phrase, WorkoutEffortVoiceCommand.parse(phrase))
        }
    }
}
