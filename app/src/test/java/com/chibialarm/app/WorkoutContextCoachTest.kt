package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutContextCoachTest {
    private val move = TrainingMove(
        name = "Chống đẩy tường",
        dose = "2 hiệp × 8 lần",
        steps = "Chống hai tay lên tường rồi đẩy ra.",
        easier = "Đứng gần tường hơn.",
        avoid = "Không ép cổ tay.",
        seconds = 90
    )

    @Test fun normalCueUsesCatalogTechniqueAndDose() {
        val text = WorkoutContextCoach.script(move)
        assertTrue(text.contains("2 hiệp × 8 lần"))
        assertTrue(text.contains("Chống hai tay lên tường"))
        assertTrue(text.contains("Không ép cổ tay"))
        assertFalse(text.contains("Cách tập nhẹ hơn"))
        assertTrue(text.contains("không phải đánh giá tư thế"))
    }

    @Test fun easierCueGivesSpecificAlternativeInsteadOfFullIntensity() {
        val text = WorkoutContextCoach.script(move, preferEasier = true)
        assertTrue(text.contains("Đứng gần tường hơn"))
        assertFalse(text.contains("Chống hai tay lên tường"))
        assertTrue(text.contains("Hãy dừng nếu đau"))
    }

    @Test fun emptyAndOversizedCatalogFieldsAreBounded() {
        val text = WorkoutContextCoach.script(
            TrainingMove("", "", "x".repeat(2000), "", "", 60)
        )
        assertTrue(text.startsWith("Động tác hiện tại"))
        assertTrue(text.length < 800)
        assertTrue(text.contains("không phải đánh giá tư thế"))
    }
}
