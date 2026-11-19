package com.chibialarm.app.fitness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMidSessionCompoundIntentTest {
    @Test
    fun easierReplacementCanBeCombinedWithTimeEnergyAndMovementConstraints() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Còn 10 phút, tao mệt rồi, đổi sang bài nhẹ hơn và đừng chống tay"
        )

        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(10, request.remainingMinutes)
        assertTrue(request.lowEnergy)
        assertTrue(request.preferEasier)
        assertTrue(request.avoidWristLoad)
        assertEquals(60, request.restSeconds)
    }

    @Test
    fun catalogReplacementStopsBeforeASecondSpokenIntent() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Đổi sang bài chống đẩy tường và nghỉ thêm 30 giây"
        )

        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals("Chống đẩy tường", request.replaceCurrentWith)
        assertEquals(30, request.restSeconds)
    }

    @Test
    fun unknownCatalogReplacementRemainsRejectedWithTrailingIntent() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Đổi sang bài Burpee và nghỉ thêm 30 giây"
        )

        assertTrue(parsed is WorkoutMidSessionRequest.Result.Unsupported)
    }
}
