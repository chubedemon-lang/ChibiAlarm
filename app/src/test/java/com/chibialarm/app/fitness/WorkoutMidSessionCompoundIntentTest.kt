package com.chibialarm.app.fitness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMidSessionCompoundIntentTest {
    @Test
    fun isRepTargetOnlyAllowsCountdownDriftButNotCompoundChanges() {
        val repOnly = WorkoutMidSessionRequest.parse("Giảm còn 10 lần mỗi hiệp")
        assertTrue(repOnly is WorkoutMidSessionRequest.Result.Proposed)
        val repRequest = (repOnly as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(repRequest.isRepTargetOnly)
        assertFalse(repRequest.isRestOnly)

        val repAndRest = WorkoutMidSessionRequest.parse(
            "Giảm còn 10 lần mỗi hiệp và nghỉ thêm 30 giây"
        )
        assertTrue(repAndRest is WorkoutMidSessionRequest.Result.Proposed)
        val combinedRequest = (repAndRest as WorkoutMidSessionRequest.Result.Proposed).request
        assertFalse(combinedRequest.isRepTargetOnly)
        assertFalse(combinedRequest.isRestOnly)

        assertFalse(
            WorkoutMidSessionRequest.Request(
                currentRepLimit = 10,
                remainingMinutes = 15
            ).isRepTargetOnly
        )
    }

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
    @Test
    fun explicitSetReductionAcceptsVietnameseNumberWordsAndCanIncludeRest() {
        val parsed = WorkoutMidSessionRequest.parse("Giảm còn hai hiệp và nghỉ thêm 30 giây")

        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(2, request.currentSetLimit)
        assertEquals(30, request.restSeconds)
        assertTrue(!request.isRestOnly)
    }

    @Test
    fun progressQuestionAndNegatedOrOutOfRangeSetCommandsDoNotBecomeChanges() {
        assertTrue(
            WorkoutMidSessionRequest.parse("Còn 2 hiệp nữa?") is
                WorkoutMidSessionRequest.Result.Unsupported
        )
        assertTrue(
            WorkoutMidSessionRequest.parse("Đừng giảm còn 2 hiệp") is
                WorkoutMidSessionRequest.Result.Unsupported
        )
        assertTrue(
            WorkoutMidSessionRequest.parse("Giảm còn 21 hiệp") is
                WorkoutMidSessionRequest.Result.Unsupported
        )
    }


    @Test
    fun optionalSetSkipUsesTheRequiredMinimumAndCanBeCombinedWithRest() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Bỏ hiệp tùy chọn và nghỉ thêm 30 giây"
        )

        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.removeOptionalSets)
        assertEquals(null, request.currentSetLimit)
        assertEquals(30, request.restSeconds)
        assertTrue(!request.isRestOnly)
    }

    @Test
    fun negatedOptionalSetSkipDoesNotBecomeAPlanChange() {
        listOf(
            "Không bỏ hiệp tùy chọn",
            "Đừng bỏ hiệp phụ",
            "Không cần bỏ hiệp tùy chọn",
            "Giữ hiệp tùy chọn"
        ).forEach { phrase ->
            assertTrue(
                phrase,
                WorkoutMidSessionRequest.parse(phrase) is WorkoutMidSessionRequest.Result.Unsupported
            )
        }
    }

    @Test
    fun vaguePlanChangesAskForClarificationInsteadOfGuessing() {
        listOf(
            "Giảm bài này đi",
            "Tập ít lại một chút",
            "Đổi bài khác đi",
            "Bài này không hợp"
        ).forEach { phrase ->
            val clarification = WorkoutMidSessionRequest.clarificationFor(phrase)
            assertTrue(phrase, clarification != null)
            assertEquals(4, clarification!!.actions.size)
            assertTrue(clarification.prompt.contains("chưa thay đổi giáo án"))
        }
    }

    @Test
    fun clarificationDoesNotInterceptExplicitCommandsNegationOrPain() {
        assertTrue(WorkoutMidSessionRequest.clarificationFor("Giảm còn 10 lần mỗi hiệp") == null)
        assertTrue(WorkoutMidSessionRequest.clarificationFor("Không giảm bài này") == null)
        assertTrue(WorkoutMidSessionRequest.clarificationFor("Đau đầu gối, giảm bài này đi") == null)
    }

}
