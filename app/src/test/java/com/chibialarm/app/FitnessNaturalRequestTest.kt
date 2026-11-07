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
}
