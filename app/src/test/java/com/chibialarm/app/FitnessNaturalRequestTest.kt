package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class FitnessNaturalRequestTest {
    @Test fun parsesVietnamesePreferencesWithoutNetwork() {
        val result = FitnessNaturalRequest.parse("Hôm nay hơi mệt, muốn giãn cơ 15 phút, không dụng cụ")
        assertEquals(2, result.goal)
        assertEquals(0, result.energy)
        assertEquals(15, result.minutes)
        assertEquals(0, result.equipment)
        assertNull(result.impact)
        assertNull(result.level)
    }

    @Test fun parsesTrainingExperienceLevels() {
        assertEquals(0, FitnessNaturalRequest.parse("Mới bắt đầu, muốn tập nhẹ").level)
        assertEquals(1, FitnessNaturalRequest.parse("Tôi đang tập đều mỗi tuần").level)
        assertEquals(2, FitnessNaturalRequest.parse("Tôi có kinh nghiệm, muốn nâng cao").level)
        assertEquals(2, FitnessNaturalRequest.parse("advanced").level)
    }

    @Test fun injuryTakesPriorityOverExerciseGoals() {
        val result = FitnessNaturalRequest.parse("Tôi bị đau khớp nhưng muốn tập sức mạnh 30 phút")
        assertEquals(2, result.impact)
        assertTrue(result.reply.contains("Không bắt đầu tập"))
    }

    @Test fun unknownInputDoesNotOverrideSelectedPreferences() {
        val result = FitnessNaturalRequest.parse("Xin chào Chibi")
        assertNull(result.goal)
        assertNull(result.minutes)
        assertNull(result.equipment)
        assertNull(result.impact)
    }

    @Test fun ignoresOutOfRangeDuration() {
        assertNull(FitnessNaturalRequest.parse("tập 999 phút").minutes)
        assertEquals(10, FitnessNaturalRequest.parse("tap 10 phut").minutes)
    }

    @Test fun handlesLowImpactWithoutInjury() {
        assertEquals(1, FitnessNaturalRequest.parse("Không nhảy, tập nhẹ nhàng").impact)
    }

    @Test fun understandsWeightLossAndEnduranceGoals() {
        assertEquals(1, FitnessNaturalRequest.parse("Tao muốn giảm cân 20 phút").goal)
        assertEquals(1, FitnessNaturalRequest.parse("Tập tăng sức bền").goal)
        assertEquals(1, FitnessNaturalRequest.parse("Giảm mỡ không dụng cụ").goal)
    }

    @Test fun weightLossRequestStillHonorsInjurySafety() {
        val result = FitnessNaturalRequest.parse("Muốn giảm cân nhưng đang đau khớp")
        assertEquals(1, result.goal)
        assertEquals(2, result.impact)
    }

    @Test fun safetySymptomsOverrideRequestedWorkout() {
        for (symptom in listOf("đau gối", "đau lưng", "buồn nôn", "muốn ngất", "tê tay", "tê chân")) {
            val result = FitnessNaturalRequest.parse("Tôi $symptom nhưng muốn tập sức mạnh 20 phút")
            assertEquals("Failed for $symptom", 2, result.impact)
            assertTrue(result.reply.contains("Không bắt đầu tập"))
        }
    }

    @Test fun ordinaryStartRequestDoesNotTriggerPainWarning() {
        assertNull(FitnessNaturalRequest.parse("Bắt đầu tập 15 phút").impact)
    }
}
