package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepPlanHandoffTest {
    @Test fun sendsExplicitFatigueAndEasierRequestsToTheExistingPreviewFlow() {
        assertEquals(
            WorkoutStepPlanHandoff.Decision.PREVIEW_CHANGE,
            WorkoutStepPlanHandoff.decide("Tao mệt rồi, đổi sang bài nhẹ hơn")
        )
        assertEquals(
            WorkoutStepPlanHandoff.Decision.PREVIEW_CHANGE,
            WorkoutStepPlanHandoff.decide("Còn khoảng 10 phút, đổi bài nhẹ hơn")
        )
    }

    @Test fun sendsNewMovementConstraintsToTheExistingPreviewFlow() {
        assertEquals(
            WorkoutStepPlanHandoff.Decision.PREVIEW_CHANGE,
            WorkoutStepPlanHandoff.decide("Đừng chống tay")
        )
        assertEquals(
            WorkoutStepPlanHandoff.Decision.PREVIEW_CHANGE,
            WorkoutStepPlanHandoff.decide("Không nằm sàn")
        )
    }

    @Test fun restOnlyRequestsStayWithTheDedicatedRestCommand() {
        assertEquals(
            WorkoutStepPlanHandoff.Decision.NOT_A_CHANGE,
            WorkoutStepPlanHandoff.decide("Nghỉ thêm 30 giây")
        )
    }

    @Test fun symptomReportsAlwaysChooseTheSafetyPath() {
        assertEquals(
            WorkoutStepPlanHandoff.Decision.SAFETY_STOP,
            WorkoutStepPlanHandoff.decide("Đau cổ tay")
        )
        assertEquals(
            WorkoutStepPlanHandoff.Decision.SAFETY_STOP,
            WorkoutStepPlanHandoff.decide("Tôi chóng mặt")
        )
    }

    @Test fun unrelatedNegatedAndEmptyPhrasesDoNotOpenThePlanner() {
        for (phrase in listOf("Không đổi bài", "Tôi muốn hỏi cách tập bài này", "", "   ")) {
            assertEquals(
                phrase,
                WorkoutStepPlanHandoff.Decision.NOT_A_CHANGE,
                WorkoutStepPlanHandoff.decide(phrase)
            )
        }
        assertEquals(
            WorkoutStepPlanHandoff.Decision.NOT_A_CHANGE,
            WorkoutStepPlanHandoff.decide(null)
        )
    }
}
