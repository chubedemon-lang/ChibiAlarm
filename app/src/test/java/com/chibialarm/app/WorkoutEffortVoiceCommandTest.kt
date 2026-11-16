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
