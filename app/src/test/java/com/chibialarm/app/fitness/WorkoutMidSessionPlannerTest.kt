package com.chibialarm.app.fitness

import com.chibialarm.app.TrainingCatalog
import com.chibialarm.app.TrainingEquipment
import com.chibialarm.app.TrainingMove
import com.chibialarm.app.WorkoutConstraintFilter
import com.chibialarm.app.WorkoutConstraintProfile
import com.chibialarm.app.WorkoutExtensionPreferences
import com.chibialarm.app.WorkoutSetTracker
import org.junit.Assert.*
import org.junit.Test

class WorkoutMidSessionPlannerTest {
    private fun move(name: String): TrainingMove =
        TrainingCatalog.allMoves.first { it.name == name }

    @Test fun compoundVietnameseRequestCapturesTimeEnergyAndWristPreference() {
        val result = WorkoutMidSessionRequest.parse(
            "Còn 10 phút, tao mệt rồi, đổi sang bài nhẹ hơn và đừng chống tay"
        )
        assertTrue(result is WorkoutMidSessionRequest.Result.Proposed)
        val request = (result as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(10, request.remainingMinutes)
        assertTrue(request.lowEnergy)
        assertTrue(request.preferEasier)
        assertTrue(request.avoidWristLoad)
        assertEquals(60, request.restSeconds)
    }

    @Test fun approximateRemainingTimePhrasesAreRecognizedInMidSession() {
        val phrases = listOf(
            "Tao còn khoảng mười phút, đổi bài nhẹ hơn",
            "Tao chỉ còn lại tầm 10 phút, đổi bài nhẹ hơn"
        )
        phrases.forEach { phrase ->
            val parsed = WorkoutMidSessionRequest.parse(phrase)
            assertTrue("$phrase should be understood", parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertEquals(10, request.remainingMinutes)
            assertTrue(request.preferEasier)
        }
    }

    @Test fun negatedRemainingTimeIsNotAppliedAsASessionDeadline() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Tao không còn khoảng năm phút, nghỉ thêm 30 giây"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertNull(request.remainingMinutes)
        assertEquals(30, request.restSeconds)
        assertTrue(request.isRestOnly)
    }

    @Test fun spokenOverheadRestrictionUsesCuratedAlternativeOnlyForUnstartedMove() {
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Đẩy vai với tạ đơn hoặc dây"),
            move("Kéo tạ đơn hoặc dây kháng lực"),
            move("Thả lỏng")
        )
        val parsed = WorkoutMidSessionRequest.parse("đừng đưa tay quá đầu")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.avoidOverheadReach)
        assertFalse(request.allowOverheadReach)

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 60,
            extensionMoveNames = emptySet(),
            level = 1
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves.first(), preview.after.first())
        assertFalse(preview.after.any { it.name == "Đẩy vai với tạ đơn hoặc dây" })
        assertTrue(preview.after.any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
        assertTrue(preview.replacements.contains("Đẩy vai với tạ đơn hoặc dây → Mở vai và ngực"))
        assertTrue(preview.after.any { it.name == "Mở vai và ngực" })
        assertFalse(preview.removedMoves.contains("Đẩy vai với tạ đơn hoặc dây"))
        assertTrue(preview.after.all {
            WorkoutConstraintFilter.isAllowed(it, WorkoutConstraintProfile(avoidOverheadReach = true))
        })
    }

    @Test fun overheadRestrictionCannotRewriteAPressAfterASetWasConfirmed() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Đẩy vai với tạ đơn hoặc dây"),
                move("Kéo tạ đơn hoặc dây kháng lực"),
                move("Thả lỏng")
            ),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(avoidOverheadReach = true),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 1
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun explicitOverheadPermissionRelaxesOnlyTheCurrentSessionPreference() {
        val parsed = WorkoutMidSessionRequest.parse("có thể đưa tay qua đầu")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.allowOverheadReach)
        assertFalse(request.avoidOverheadReach)

        val current = WorkoutConstraintProfile(avoidOverheadReach = true)
        assertEquals(
            WorkoutConstraintProfile(),
            WorkoutMidSessionPlanner.effectiveConstraints(current, request)
        )
    }

    @Test fun explicitRestIsBoundedAndDoesNotInventAnIntensityChange() {
        val result = WorkoutMidSessionRequest.parse("Nghỉ thêm 30 giây")
        assertTrue(result is WorkoutMidSessionRequest.Result.Proposed)
        val request = (result as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(30, request.restSeconds)
        assertFalse(request.preferEasier)
        assertFalse(request.avoidWristLoad)
    }

    @Test fun localizedPainStopsButBodyPartAloneDoesNot() {
        assertSame(
            WorkoutMidSessionRequest.Result.SafetyStop,
            WorkoutMidSessionRequest.parse("Đau đầu gối, đổi bài nhẹ hơn")
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Đầu gối")
        )
    }

    @Test fun negatedTirednessDoesNotTriggerAnAutomaticRest() {
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Hôm nay tao không mệt")
        )
    }

    @Test fun multiConstraintPreviewPreservesCompletedPrefixAndFiltersRemainingMoves() {
        val moves = listOf(
            move("Squat không tạ"),
            move("Chống đẩy sàn"),
            move("Bird-dog"),
            move("Thả lỏng")
        )
        val request = WorkoutMidSessionRequest.Request(
            remainingMinutes = 10,
            lowEnergy = true,
            avoidWristLoad = true,
            preferEasier = true,
            restSeconds = 60
        )

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves.first(), preview.after.first())
        assertEquals("Ép ngực tường bằng cẳng tay", preview.after[1].name)
        assertEquals("Dead bug cơ bụng", preview.after[2].name)
        assertEquals("Thả lỏng", preview.after.last().name)
        assertTrue(preview.after.drop(1).all {
            com.chibialarm.app.WorkoutConstraintFilter.isAllowed(
                it, WorkoutConstraintProfile(avoidWristLoad = true)
            )
        })
        assertEquals(60, preview.restSeconds)
        assertEquals(10, preview.requestedMinutes)
    }

    @Test fun lowEnergyMidSessionCanReplaceWeightedMovesWithoutFloorOrWristLoading() {
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Kéo tạ đơn hoặc dây kháng lực"),
            move("Gập hông với tạ đơn hoặc dây"),
            move("Đẩy vai với tạ đơn hoặc dây"),
            move("Thả lỏng")
        )
        val parsed = WorkoutMidSessionRequest.parse(
            "Tao mệt rồi, đừng chống tay và không nằm sàn"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.lowEnergy)
        assertTrue(request.preferEasier)
        assertTrue(request.avoidWristLoad)
        assertTrue(request.avoidFloorPositions)

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(
            listOf(
                "Khởi động tại chỗ",
                "Siết bả vai đứng",
                "Gập hông không dụng cụ",
                "Mở vai và ngực",
                "Thả lỏng"
            ),
            preview.after.map { it.name }
        )
        val effectiveProfile = WorkoutConstraintProfile(
            avoidWristLoad = true,
            avoidFloorPositions = true
        )
        assertTrue(preview.after.all { WorkoutConstraintFilter.isAllowed(it, effectiveProfile) })
        assertEquals(3, preview.replacements.size)
    }

    @Test fun wristConstraintCannotRewriteAnAlreadyStartedUnsafeExercise() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Chống đẩy sàn"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(avoidWristLoad = true),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun shorterTargetRemovesOptionalExtensionBeforeRequiredBaseWork() {
        val current = TrainingMove(
            "Squat không tạ", "2 hiệp × 8 lần", "Thực hiện có kiểm soát",
            "Giảm biên độ", "Dừng nếu đau", 120, 45
        )
        val extension = TrainingMove(
            "Khối bổ sung", "5 phút", "Vận động nhẹ",
            "Giảm tốc", "Dừng nếu khó chịu", 300
        )
        val cooldown = TrainingMove(
            "Thả lỏng", "2 phút", "Thở chậm",
            "Ngồi nghỉ", "Không kéo đến đau", 120
        )

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(current, extension, cooldown),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(remainingMinutes = 5),
            remainingCurrentSeconds = 300,
            extensionMoveNames = setOf(extension.name),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(listOf(current, cooldown), preview.after)
        assertTrue(preview.removedMoves.contains(extension.name))
        assertTrue(preview.targetExceeded)
        assertEquals(current, preview.after.first())
    }

    @Test fun replacingCurrentMoveUsesTheShorterTimerInPreviewEstimate() {
        val moves = listOf(move("Cat-cow nhẹ"), move("Thả lỏng"))
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(preferEasier = true),
            remainingCurrentSeconds = 70,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertTrue(preview.currentMoveChanged)
        assertEquals("Mở vai và ngực", preview.after.first().name)
        // 60s replacement timer + 180s minimum cool-down + 20s transition.
        // The previous implementation incorrectly retained the old 70s timer in its preview.
        assertEquals(260, preview.estimatedRemainingSeconds)
    }

    @Test fun checkpointPlanRoundTripsOnlyCanonicalCatalogExercises() {
        val moves = listOf(move("Squat không tạ"), move("Dead bug cơ bụng"), move("Thả lỏng"))
        val encoded = WorkoutMidSessionPlanner.encodePlan(moves)
        assertEquals(moves, WorkoutMidSessionPlanner.decodePlan(encoded))
        assertNull(WorkoutMidSessionPlanner.decodePlan("not-a-catalog-exercise"))
        assertNull(WorkoutMidSessionPlanner.decodePlan("x".repeat(8_193)))
    }

    @Test fun spokenFloorPreferenceIsRecognizedWithoutTreatingBodyPositionAsPain() {
        val result = WorkoutMidSessionRequest.parse("Còn 10 phút, đừng nằm hoặc quỳ sàn")
        assertTrue(result is WorkoutMidSessionRequest.Result.Proposed)
        val request = (result as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(10, request.remainingMinutes)
        assertTrue(request.avoidFloorPositions)
        assertFalse(request.lowEnergy)
    }

    @Test fun midSessionFloorPreferenceRemovesUnstartedFloorMovesAndPreservesTheCurrentStep() {
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Bird-dog"),
            move("Squat không tạ"),
            move("Plank khuỷu hoặc plank gối"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(avoidFloorPositions = true),
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves.first(), preview.after.first())
        assertTrue(preview.after.any { it.name == "Squat không tạ" })
        assertFalse(preview.after.any { it.name == "Bird-dog" })
        assertFalse(preview.after.any { it.name == "Plank khuỷu hoặc plank gối" })
        assertTrue(preview.removedMoves.contains("Bird-dog"))
    }

    @Test fun newFloorRestrictionCannotRewriteAnAlreadyStartedFloorExercise() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Bird-dog"), move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(avoidFloorPositions = true),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }


    @Test fun explicitNoEquipmentRequestIsNotMistakenForLowEnergyOrAnEasierRequest() {
        val parsed = WorkoutMidSessionRequest.parse("Tao không dùng tạ hoặc dây kháng lực nữa")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.avoidExternalLoad)
        assertEquals(
            setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND),
            request.unavailableEquipment
        )
        assertFalse(request.lowEnergy)
        assertFalse(request.preferEasier)
        assertNull(request.restSeconds)
    }

    @Test fun positiveEquipmentMentionUpdatesSessionToolsWithoutAddingTrainingVolume() {
        listOf(
            "Tao có tạ và dây kháng lực",
            "Tao có cả tạ lẫn dây kháng lực",
            "Hôm nay có tạ đơn và dây kháng lực"
        ).forEach { utterance ->
            val parsed = WorkoutMidSessionRequest.parse(utterance)
            assertTrue(utterance, parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertEquals(
                utterance,
                setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND),
                request.availableEquipment
            )
            assertTrue(utterance, request.unavailableEquipment.isEmpty())
            assertFalse(utterance, request.addOptionalExercise)
        }
    }

    @Test fun negatedCompoundEquipmentListDoesNotGrantEitherTool() {
        val parsed = WorkoutMidSessionRequest.parse("Tao không có tạ và dây kháng lực")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(
            setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND),
            request.unavailableEquipment
        )
        assertTrue(request.availableEquipment.isEmpty())
        assertTrue(request.avoidExternalLoad)
        assertFalse(request.addOptionalExercise)
    }

    @Test fun colloquialEquipmentRequestsOnlyMarkTheMissingTool() {
        val allTools = TrainingEquipment.values().toSet()
        val cases = listOf(
            "Hôm nay tao không mang tạ" to setOf(TrainingEquipment.DUMBBELL),
            "Tao để tạ ở nhà rồi" to setOf(TrainingEquipment.DUMBBELL),
            "Tao không có dây kháng lực" to setOf(TrainingEquipment.RESISTANCE_BAND),
            "Không có thiết bị tập hôm nay" to allTools,
            "Đổi bài tập tay không" to allTools
        )
        cases.forEach { (utterance, expectedMissing) ->
            val parsed = WorkoutMidSessionRequest.parse(utterance)
            assertTrue(utterance, parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertEquals(utterance, expectedMissing, request.unavailableEquipment)
            assertEquals(
                utterance,
                expectedMissing.containsAll(
                    setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND)
                ),
                request.avoidExternalLoad
            )
            assertFalse(utterance, request.lowEnergy)
            assertFalse(utterance, request.preferEasier)
        }
    }

    @Test fun aMissingDumbbellDoesNotDiscardAnExerciseThatAlsoSupportsABand() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Tao không có tạ nhưng vẫn có dây kháng lực"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(setOf(TrainingEquipment.DUMBBELL), request.unavailableEquipment)
        assertFalse(request.avoidExternalLoad)

        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Kéo tạ đơn hoặc dây kháng lực"),
            move("Gập hông với tạ đơn hoặc dây"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 60,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves, preview.after)
        assertTrue(preview.replacements.isEmpty())
        assertTrue(preview.removedMoves.isEmpty())
    }

    @Test fun aMoveWithOnlyUnavailableEquipmentIsRemovedOnlyWhenUnstarted() {
        val dumbbellOnly = TrainingMove(
            name = "Dumbbell row",
            dose = "2 hiệp × 8 lần",
            steps = "Kéo có kiểm soát",
            easier = "Giảm tải",
            avoid = "Dừng nếu đau",
            equipmentOptions = setOf(TrainingEquipment.DUMBBELL)
        )
        val request = (WorkoutMidSessionRequest.parse("Tao không có tạ") as
            WorkoutMidSessionRequest.Result.Proposed).request
        val moves = listOf(
            move("Khởi động tại chỗ"),
            dumbbellOnly,
            move("Squat không tạ"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 60,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertTrue(preview.removedMoves.contains("Dumbbell row"))
        assertFalse(preview.after.any { it.name == "Dumbbell row" })
        assertTrue(preview.after.any { it.name == "Squat không tạ" })
    }

    @Test fun noEquipmentMidSessionReplacesOnlyRemainingWeightedCatalogMoves() {
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Kéo tạ đơn hoặc dây kháng lực"),
            move("Gập hông với tạ đơn hoặc dây"),
            move("Đẩy vai với tạ đơn hoặc dây"),
            move("Thả lỏng")
        )
        val parsed = WorkoutMidSessionRequest.parse("Không có dụng cụ, còn 10 phút")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.avoidExternalLoad)
        assertEquals(10, request.remainingMinutes)
        assertFalse(request.preferEasier)

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = setOf("Gập hông với tạ đơn hoặc dây"),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(
            listOf(
                "Khởi động tại chỗ",
                "Siết bả vai đứng",
                "Gập hông không dụng cụ",
                "Mở vai và ngực",
                "Thả lỏng"
            ),
            preview.after.map { it.name }
        )
        assertEquals(3, preview.replacements.size)
        assertEquals(setOf("Gập hông không dụng cụ"), preview.extensionMoveNames)
        assertFalse(preview.after.any { it.requiresExternalLoad })
    }

    @Test fun equipmentMetadataIsAuthoritativeEvenWhenTheDisplayNameChanges() {
        val weighted = TrainingMove(
            name = "Dumbbell row", dose = "2 hiệp × 8 lần",
            steps = "Kéo có kiểm soát", easier = "Giảm tải", avoid = "Dừng nếu đau",
            seconds = 60, restSeconds = 45,
            equipmentOptions = setOf(TrainingEquipment.DUMBBELL)
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(weighted, move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(avoidExternalLoad = true),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)

        val untagged = weighted.copy(name = "Bài tạ trong mô tả cũ", equipmentOptions = emptySet())
        assertFalse(untagged.requiresExternalLoad)
    }

    @Test fun noEquipmentPreferenceCannotRewriteAWeightedMoveAfterASetWasConfirmed() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Kéo tạ đơn hoặc dây kháng lực"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(avoidExternalLoad = true),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }


    @Test fun explicitMidSessionPermissionsCanRelaxEarlierMovementPreferences() {
        val parsed = WorkoutMidSessionRequest.parse("có thể chống tay và nằm sàn")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertFalse(request.avoidWristLoad)
        assertFalse(request.avoidFloorPositions)
        assertTrue(request.allowWristLoad)
        assertTrue(request.allowFloorPositions)

        val current = WorkoutConstraintProfile(
            avoidWristLoad = true,
            avoidFloorPositions = true
        )
        assertEquals(
            WorkoutConstraintProfile(),
            WorkoutMidSessionPlanner.effectiveConstraints(current, request)
        )
    }

    @Test fun midSessionPermissionPreviewKeepsCatalogMovesWhenConstraintsAreRelaxed() {
        val moves = listOf(
            move("Chống đẩy sàn"),
            move("Bird-dog"),
            move("Thả lỏng")
        )
        val parsed = WorkoutMidSessionRequest.parse("có thể chống tay và nằm sàn")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(
                avoidWristLoad = true,
                avoidFloorPositions = true
            ),
            request = request,
            remainingCurrentSeconds = 60,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves, preview.after)
        assertTrue(preview.replacements.isEmpty())
        assertTrue(preview.removedMoves.isEmpty())
    }

    @Test fun unrelatedMidSessionRequestDoesNotClearExistingMovementConstraints() {
        val parsed = WorkoutMidSessionRequest.parse("Nghỉ thêm 30 giây")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        val current = WorkoutConstraintProfile(
            avoidWristLoad = true,
            avoidFloorPositions = true
        )

        assertEquals(
            current,
            WorkoutMidSessionPlanner.effectiveConstraints(current, request)
        )
    }

    @Test fun explicitRestrictionWinsOverPermissionInOneMidSessionUtterance() {
        val parsed = WorkoutMidSessionRequest.parse(
            "có thể chống tay nhưng không chống tay; có thể nằm sàn nhưng không quỳ sàn"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertTrue(request.avoidWristLoad)
        assertFalse(request.allowWristLoad)
        assertTrue(request.avoidFloorPositions)
        assertFalse(request.allowFloorPositions)
    }

    @Test fun pureRestRequestCanExtendAnActiveRestWithoutChangingThePlan() {
        val parsed = WorkoutMidSessionRequest.parse("Nghỉ thêm 30 giây")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertTrue(request.isRestOnly)
        assertEquals(30, request.restSeconds)
        assertNull(request.remainingMinutes)
        assertFalse(request.avoidWristLoad)
        assertFalse(request.avoidExternalLoad)
    }

    @Test fun combinedRestAndPlanChangeIsNotMistakenForRestOnly() {
        val parsed = WorkoutMidSessionRequest.parse("Nghỉ thêm 30 giây và đừng chống tay")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertFalse(request.isRestOnly)
        assertEquals(30, request.restSeconds)
        assertTrue(request.avoidWristLoad)
    }

    @Test fun explicitChairRequestAddsOneCuratedOptionalMoveBeforeCooldown() {
        val parsed = WorkoutMidSessionRequest.parse("Tao có ghế vững chắc, thêm một bài")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.addOptionalExercise)
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), request.availableEquipment)
        assertTrue(request.unavailableEquipment.isEmpty())

        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Squat không tạ"),
            move("Chống đẩy tường"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 1,
            extensionPreferences = WorkoutExtensionPreferences(goal = 1)
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(listOf("Nâng gối ngồi ghế"), preview.addedMoves)
        assertEquals("Thả lỏng", preview.after.last().name)
        assertTrue(preview.after.any { it.name == "Nâng gối ngồi ghế" })
        assertEquals(moves.first(), preview.after.first())
        assertEquals(setOf(TrainingEquipment.STABLE_CHAIR), preview.availableEquipment)
        assertTrue(preview.extensionMoveNames.contains("Nâng gối ngồi ghế"))
        assertEquals(preview.after.size, preview.after.map { it.name }.distinct().size)
    }

    @Test fun unavailableChairIsNeverUsedForAnAddedMove() {
        val parsed = WorkoutMidSessionRequest.parse("Không có ghế, thêm một bài")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.unavailableEquipment.contains(TrainingEquipment.STABLE_CHAIR))
        assertFalse(request.availableEquipment.contains(TrainingEquipment.STABLE_CHAIR))

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Chống đẩy tường"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 1,
            extensionPreferences = WorkoutExtensionPreferences(goal = 1, equipment = 3)
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(1, preview.addedMoves.size)
        assertFalse(preview.after.any {
            TrainingEquipment.STABLE_CHAIR in it.equipmentOptions
        })
        assertFalse(preview.availableEquipment.contains(TrainingEquipment.STABLE_CHAIR))
    }

    @Test fun lowEnergyRequestCannotIncreaseTrainingVolume() {
        val parsed = WorkoutMidSessionRequest.parse("Tao mệt rồi, thêm một bài với ghế")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.lowEnergy)
        assertTrue(request.addOptionalExercise)
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Chống đẩy tường"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 1,
            extensionPreferences = WorkoutExtensionPreferences(goal = 1)
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun addedMoveMustFitExplicitRemainingTimeBudget() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Chống đẩy tường"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(
                remainingMinutes = 5,
                addOptionalExercise = true,
                availableEquipment = setOf(TrainingEquipment.STABLE_CHAIR)
            ),
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 1,
            extensionPreferences = WorkoutExtensionPreferences(goal = 1)
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun removingOptionalExercisesIsAPlanChangeNotAProposedAddition() {
        val parsed = WorkoutMidSessionRequest.parse("Bỏ bài bổ sung")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.removeOptionalExercises)
        assertFalse(request.addOptionalExercise)
        assertFalse(request.isRestOnly)
    }

    @Test fun colloquialOptionalRemovalCommandsAreRecognizedWithoutBroadlyMatchingExerciseRemoval() {
        val phrases = listOf(
            "Bỏ hết bài phụ",
            "Bỏ bớt bài phụ",
            "Bỏ mấy bài phụ",
            "Không cần tập phần bổ sung nữa",
            "Skip bài phụ",
            "Cắt bài phụ",
            "Bỏ bài tùy chọn"
        )
        phrases.forEach { phrase ->
            val parsed = WorkoutMidSessionRequest.parse(phrase)
            assertTrue("$phrase should be understood", parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertTrue("$phrase should remove optional moves", request.removeOptionalExercises)
            assertFalse("$phrase must not add a move", request.addOptionalExercise)
            assertFalse("$phrase is a plan change, not a rest-only request", request.isRestOnly)
        }
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Bỏ bài Squat")
        )
    }

    @Test fun removingOptionalExercisesKeepsCompletedPrefixAndActiveRequiredWork() {
        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Squat không tạ"),
            move("Nâng gối ngồi ghế"),
            move("Chống đẩy tường"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(removeOptionalExercises = true),
            remainingCurrentSeconds = 45,
            extensionMoveNames = setOf("Nâng gối ngồi ghế"),
            level = 1
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(moves.first(), preview.after.first())
        assertEquals(
            listOf("Khởi động tại chỗ", "Squat không tạ", "Chống đẩy tường", "Thả lỏng"),
            preview.after.map { it.name }
        )
        assertEquals(listOf("Nâng gối ngồi ghế"), preview.removedMoves)
        assertFalse(preview.extensionMoveNames.contains("Nâng gối ngồi ghế"))
        assertTrue(preview.changed)
    }

    @Test fun cannotRemoveTheOptionalMoveThatIsAlreadyActive() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Khởi động tại chỗ"),
                move("Nâng gối ngồi ghế"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(removeOptionalExercises = true),
            remainingCurrentSeconds = 30,
            extensionMoveNames = setOf("Nâng gối ngồi ghế"),
            level = 1
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
        assertTrue((result as WorkoutMidSessionPlanner.Result.Blocked).reason.contains("đang tập"))
    }

    @Test fun exclusiveSingleToolStatementsExcludeEveryUnmentionedTool() {
        val allTools = TrainingEquipment.values().toSet()
        val cases = listOf(
            "Tao chỉ có tạ đơn" to TrainingEquipment.DUMBBELL,
            "Tao chỉ có dây kháng lực" to TrainingEquipment.RESISTANCE_BAND,
            "Tao chỉ có ghế vững chắc" to TrainingEquipment.STABLE_CHAIR,
            "Tao chỉ có thảm tập" to TrainingEquipment.MAT,
            "Tao chỉ dùng thảm tập" to TrainingEquipment.MAT
        )

        cases.forEach { (utterance, onlyTool) ->
            val parsed = WorkoutMidSessionRequest.parse(utterance)
            assertTrue("$utterance should be understood", parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertEquals(utterance, setOf(onlyTool), request.availableEquipment)
            assertEquals(utterance, allTools - setOf(onlyTool), request.unavailableEquipment)
        }
    }

    @Test fun colloquialExclusiveInventoryPhrasesAreUnderstoodAsOnlyAvailableTools() {
        val allTools = TrainingEquipment.values().toSet()
        val cases = listOf(
            "Tao có mỗi thảm tập" to TrainingEquipment.MAT,
            "Tao mang theo mỗi tạ đơn" to TrainingEquipment.DUMBBELL,
            "Tao chỉ mang theo ghế vững chắc" to TrainingEquipment.STABLE_CHAIR,
            "Tao chỉ có mỗi dây kháng lực" to TrainingEquipment.RESISTANCE_BAND
        )

        cases.forEach { (utterance, onlyTool) ->
            val parsed = WorkoutMidSessionRequest.parse(utterance)
            assertTrue("$utterance should be understood", parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertEquals(utterance, setOf(onlyTool), request.availableEquipment)
            assertEquals(utterance, allTools - setOf(onlyTool), request.unavailableEquipment)
        }
    }

    @Test fun carryingOneToolAddsItWithoutExcludingOtherEquipment() {
        val parsed = WorkoutMidSessionRequest.parse("Tao mang theo thảm tập")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(setOf(TrainingEquipment.MAT), request.availableEquipment)
        assertTrue(request.unavailableEquipment.isEmpty())
    }

    @Test fun sayingOnlyBringOneToolExcludesEveryUnmentionedTool() {
        val parsed = WorkoutMidSessionRequest.parse("Tao chỉ đem theo thảm tập")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(setOf(TrainingEquipment.MAT), request.availableEquipment)
        assertEquals(
            TrainingEquipment.values().toSet() - setOf(TrainingEquipment.MAT),
            request.unavailableEquipment
        )
    }

    @Test fun negatedExclusiveInventoryDoesNotDiscardToolsNamedAfterTheContrast() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Tao không chỉ mang theo thảm tập, tao còn có tạ đơn"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertEquals(
            setOf(TrainingEquipment.MAT, TrainingEquipment.DUMBBELL),
            request.availableEquipment
        )
        assertTrue(request.unavailableEquipment.isEmpty())
    }

    @Test fun colloquialMissingEquipmentRemainsUnavailableRatherThanBeingGranted() {
        val parsed = WorkoutMidSessionRequest.parse("Tao không mang theo dây kháng lực")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertTrue(request.availableEquipment.isEmpty())
        assertEquals(
            setOf(TrainingEquipment.RESISTANCE_BAND),
            request.unavailableEquipment
        )
    }

    @Test fun exclusiveCompoundToolListKeepsEveryNamedToolAndExcludesTheRest() {
        val parsed = WorkoutMidSessionRequest.parse("Tao chỉ có tạ và dây kháng lực")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertEquals(
            setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.RESISTANCE_BAND),
            request.availableEquipment
        )
        assertEquals(
            setOf(TrainingEquipment.STABLE_CHAIR, TrainingEquipment.MAT),
            request.unavailableEquipment
        )
    }

    @Test fun exclusiveMatOnlyRequestReplacesAnUnstartedWeightedMove() {
        val parsed = WorkoutMidSessionRequest.parse("Tao chỉ có thảm tập")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        val weightedMove = move("Kéo tạ đơn hoặc dây kháng lực")

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Khởi động tại chỗ"),
                weightedMove,
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertFalse(preview.after.any { it.name == weightedMove.name })
        assertTrue(preview.after.any { it.name == "Siết bả vai đứng" })
        assertEquals(setOf(TrainingEquipment.MAT), preview.availableEquipment)
    }

    @Test fun notOnlyEquipmentPhraseDoesNotExcludeOtherNamedTools() {
        val parsed = WorkoutMidSessionRequest.parse("Tao không chỉ có tạ mà còn có thảm")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertEquals(
            setOf(TrainingEquipment.DUMBBELL, TrainingEquipment.MAT),
            request.availableEquipment
        )
        assertTrue(request.unavailableEquipment.isEmpty())
    }

    @Test fun addRequestAndNewEquipmentAreNotTreatedAsRestOnly() {
        assertTrue(WorkoutMidSessionRequest.Request(restSeconds = 30).isRestOnly)
        assertFalse(
            WorkoutMidSessionRequest.Request(restSeconds = 30, addOptionalExercise = true).isRestOnly
        )
        assertFalse(
            WorkoutMidSessionRequest.Request(
                restSeconds = 30,
                availableEquipment = setOf(TrainingEquipment.STABLE_CHAIR)
            ).isRestOnly
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Đừng thêm bài")
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Không thêm động tác")
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Không cần thêm động tác")
        )
    }

    @Test fun exclusiveMatOnlyInventoryAlsoConstrainsOptionalMidSessionAdditions() {
        val parsed = WorkoutMidSessionRequest.parse("Tao chỉ có thảm tập, thêm một bài")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        assertTrue(request.addOptionalExercise)
        assertEquals(setOf(TrainingEquipment.MAT), request.availableEquipment)
        assertEquals(
            TrainingEquipment.values().toSet() - setOf(TrainingEquipment.MAT),
            request.unavailableEquipment
        )

        // The saved profile still says dumbbells/bands. The explicit "only a mat"
        // statement must override that older profile when choosing a new optional move.
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Chống đẩy tường"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 90,
            extensionMoveNames = emptySet(),
            level = 1,
            extensionPreferences = WorkoutExtensionPreferences(goal = 0, equipment = 2)
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(setOf(TrainingEquipment.MAT), preview.availableEquipment)
        assertEquals(1, preview.addedMoves.size)
        assertFalse(preview.addedMoves.any {
            it.contains("tạ", ignoreCase = true) || it.contains("dây kháng lực", ignoreCase = true)
        })
        val added = preview.after.filter { it.name in preview.addedMoves }
        assertTrue(added.all {
            it.equipmentOptions.isEmpty() || it.equipmentOptions.any { equipment ->
                equipment == TrainingEquipment.MAT
            }
        })
    }
    @Test fun explicitCatalogReplacementIsPreviewedWithoutChangingCompletedPrefixOrFutureMoves() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Đổi bài hiện tại thành chống đẩy tường"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals("Chống đẩy tường", request.replaceCurrentWith)
        assertFalse(request.isRestOnly)

        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Chống đẩy sàn"),
            move("Squat không tạ"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(
            listOf("Khởi động tại chỗ", "Chống đẩy tường", "Squat không tạ", "Thả lỏng"),
            preview.after.map { it.name }
        )
        assertTrue(preview.currentMoveChanged)
        assertEquals(listOf("Chống đẩy sàn → Chống đẩy tường"), preview.replacements)
    }

    @Test fun spokenShorthandCatalogReplacementIsPreviewedForTheCurrentMove() {
        val parsed = WorkoutMidSessionRequest.parse("Đổi sang bài chống đẩy tường")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals("Chống đẩy tường", request.replaceCurrentWith)

        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Chống đẩy sàn"),
            move("Squat không tạ"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals(
            listOf("Khởi động tại chỗ", "Chống đẩy tường", "Squat không tạ", "Thả lỏng"),
            preview.after.map { it.name }
        )
        assertEquals(listOf("Chống đẩy sàn → Chống đẩy tường"), preview.replacements)
    }

    @Test fun genericEasierShorthandIsNotMistakenForAnUnknownCatalogMove() {
        val parsed = WorkoutMidSessionRequest.parse("Đổi sang bài nhẹ hơn")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertNull(request.replaceCurrentWith)
        assertTrue(request.preferEasier)
    }

    @Test fun unknownShorthandCatalogReplacementFailsClosed() {
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Đổi sang bài burpee")
        )
    }

    @Test fun explicitCatalogReplacementCannotRewriteAConfirmedSet() {
        val parsed = WorkoutMidSessionRequest.parse(
            "Thay động tác hiện tại bằng chống đẩy tường"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Chống đẩy sàn"), move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 20,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun explicitReplacementRejectsUnknownAndUnapprovedExercises() {
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Đổi bài hiện tại thành burpee")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Chống đẩy sàn"), move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(replaceCurrentWith = "Squat không tạ"),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun spokenNoJumpRequestReplacesOnlyAnUnstartedJumpingMove() {
        val parsed = WorkoutMidSessionRequest.parse("Không nhảy, đổi bài giúp tao")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.avoidJumping)
        assertFalse(request.allowJumping)

        val moves = listOf(
            move("Khởi động tại chỗ"),
            move("Bật nhảy dang tay chân"),
            move("Squat không tạ"),
            move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 2
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        assertEquals("Bật nhảy dang tay chân → Bước ngang nhẹ", preview.replacements.single())
        assertEquals("Khởi động tại chỗ", preview.after.first().name)
        assertEquals("Bước ngang nhẹ", preview.after[1].name)
        assertTrue(preview.after.none { it.requiresJumping })
    }

    @Test fun noJumpRequestCannotRewriteAJumpingMoveAfterASetWasConfirmed() {
        val parsed = WorkoutMidSessionRequest.parse("Không nhảy")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Bật nhảy dang tay chân"), move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request,
            remainingCurrentSeconds = 20,
            extensionMoveNames = emptySet(),
            level = 2
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun lowImpactPreferencePersistsAcrossFollowUpsAndCanBeExplicitlyRelaxed() {
        val selected = WorkoutConstraintProfile(avoidJumping = true)
        val followUp = WorkoutMidSessionRequest.parse("Đổi bài nhẹ hơn")
        assertTrue(followUp is WorkoutMidSessionRequest.Result.Proposed)
        val retained = WorkoutMidSessionPlanner.effectiveConstraints(
            selected, (followUp as WorkoutMidSessionRequest.Result.Proposed).request
        )
        assertTrue(retained.avoidJumping)

        val permission = WorkoutMidSessionRequest.parse("Nhảy được")
        assertTrue(permission is WorkoutMidSessionRequest.Result.Proposed)
        val permissionRequest = (permission as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(permissionRequest.allowJumping)
        assertFalse(WorkoutMidSessionPlanner.effectiveConstraints(selected, permissionRequest).avoidJumping)
    }

    @Test fun explicitLowImpactRestrictionWinsOverJumpingPermissionInTheSameUtterance() {
        val parsed = WorkoutMidSessionRequest.parse("Không nhảy, nhảy được")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertTrue(request.avoidJumping)
        assertFalse(request.allowJumping)
    }
 
    @Test fun negativeJumpPermissionNeverRelaxesTheNoJumpPreference() {
        for (phrase in listOf("Không nhảy được", "Không được nhảy", "Không thể nhảy")) {
            val parsed = WorkoutMidSessionRequest.parse(phrase)
            assertTrue("Failed for $phrase", parsed is WorkoutMidSessionRequest.Result.Proposed)
            val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
            assertTrue("Failed for $phrase", request.avoidJumping)
            assertFalse("Failed for $phrase", request.allowJumping)
        }
    }

    @Test fun completedSetCountCannotExceedTheCurrentCatalogPrescription() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 4,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(restSeconds = 30),
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertSame(WorkoutMidSessionPlanner.Result.Unsupported, result)
    }

    @Test fun reducingTheActiveSetLimitDropsOnlyOptionalSetsAndPreservesRewardMinimum() {
        val parsed = WorkoutMidSessionRequest.parse("Giảm còn 2 hiệp")
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request

        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(
                move("Khởi động tại chỗ"),
                move("Squat không tạ"),
                move("Thả lỏng")
            ),
            currentIndex = 1,
            completedCurrentSets = 1,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = request,
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        val adjusted = WorkoutSetTracker.parse(preview.after[1].dose, preview.after[1].restSeconds)!!
        assertEquals(2, preview.currentSetLimit)
        assertEquals(1, preview.completedCurrentSets)
        assertEquals(2, adjusted.requiredSets)
        assertEquals(2, adjusted.maximumSets)
        assertEquals(1, WorkoutSetTracker.requiredSetsForReward(mapOf(1 to 1), preview.after))
        assertTrue(preview.after[1].dose.contains("2 hiệp × 8–12 lần"))
    }

    @Test fun optionalSetLimitCanFinishOnlyWhenTheRequiredMinimumWasAlreadyConfirmed() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 2,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(currentSetLimit = 2),
            remainingCurrentSeconds = 20,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        val adjusted = WorkoutSetTracker.parse(preview.after.first().dose)!!
        assertTrue(WorkoutSetTracker.isComplete(2, adjusted))
    }

    @Test fun setLimitCannotLowerRequiredWorkOrRewriteConfirmedSets() {
        fun preview(completed: Int, limit: Int) =
            WorkoutMidSessionPlanner.preview(
                currentMoves = listOf(move("Squat không tạ"), move("Thả lỏng")),
                currentIndex = 0,
                completedCurrentSets = completed,
                currentStepLocked = completed > 0,
                constraints = WorkoutConstraintProfile(),
                request = WorkoutMidSessionRequest.Request(currentSetLimit = limit),
                remainingCurrentSeconds = 30,
                extensionMoveNames = emptySet(),
                level = 0
            )

        assertTrue(preview(completed = 0, limit = 1) is WorkoutMidSessionPlanner.Result.Blocked)
        assertTrue(preview(completed = 3, limit = 2) is WorkoutMidSessionPlanner.Result.Blocked)
        assertTrue(preview(completed = 0, limit = 4) is WorkoutMidSessionPlanner.Result.Blocked)
    }

    @Test fun setLimitIsBlockedForTimedMovesAndWhenNoOptionalSetExists() {
        val timed = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Đi bộ nhanh tại chỗ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(currentSetLimit = 1),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        val alreadyAtLimit = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(currentSetLimit = 3),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )

        assertTrue(timed is WorkoutMidSessionPlanner.Result.Blocked)
        assertTrue(alreadyAtLimit is WorkoutMidSessionPlanner.Result.Blocked)
    }


    @Test fun explicitPerSetRepLimitIsRecognizedButQuestionsAndNegationsAreNotCommands() {
        val reduced = WorkoutMidSessionRequest.parse("Giảm còn 10 lần mỗi hiệp")
        assertTrue(reduced is WorkoutMidSessionRequest.Result.Proposed)
        assertEquals(
            10,
            (reduced as WorkoutMidSessionRequest.Result.Proposed).request.currentRepLimit
        )
        val colloquial = WorkoutMidSessionRequest.parse("Mỗi hiệp 10 lần thôi")
        assertTrue(colloquial is WorkoutMidSessionRequest.Result.Proposed)
        assertEquals(
            10,
            (colloquial as WorkoutMidSessionRequest.Result.Proposed).request.currentRepLimit
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Còn 10 lần nữa?")
        )
        assertSame(
            WorkoutMidSessionRequest.Result.Unsupported,
            WorkoutMidSessionRequest.parse("Đừng giảm còn 10 lần mỗi hiệp")
        )
    }

    @Test fun repLimitChangesOnlyTheFutureDoseAndKeepsConfirmedSetCount() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 1,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(currentRepLimit = 10),
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Proposed)
        val preview = (result as WorkoutMidSessionPlanner.Result.Proposed).preview
        val adjusted = WorkoutSetTracker.parse(preview.after.first().dose)!!
        assertEquals(10, preview.currentRepLimit)
        assertEquals(1, preview.completedCurrentSets)
        assertEquals(8, adjusted.targetMin)
        assertEquals(10, adjusted.targetMax)
        assertEquals(2, adjusted.requiredSets)
        assertEquals(3, adjusted.maximumSets)
        assertTrue(preview.after.first().dose.contains("2–3 hiệp × 8–10 lần"))
    }

    @Test fun repLimitCannotLowerTheMinimumOrIncreaseAnExistingTarget() {
        fun result(limit: Int) = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(currentRepLimit = limit),
            remainingCurrentSeconds = 45,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result(6) is WorkoutMidSessionPlanner.Result.Blocked)
        assertTrue(result(13) is WorkoutMidSessionPlanner.Result.Blocked)
        assertTrue(result(12) is WorkoutMidSessionPlanner.Result.Blocked)
    }


    @Test fun repLimitIsBlockedWhenEverySetHasAlreadyBeenConfirmed() {
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Squat không tạ"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 3,
            currentStepLocked = true,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(currentRepLimit = 10),
            remainingCurrentSeconds = 0,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(result is WorkoutMidSessionPlanner.Result.Blocked)
    }


    @Test fun repLimitBlocksTimedMovesAndCanBeCombinedWithExplicitRest() {
        val timed = WorkoutMidSessionPlanner.preview(
            currentMoves = listOf(move("Plank khuỷu hoặc plank gối"), move("Thả lỏng")),
            currentIndex = 0,
            completedCurrentSets = 0,
            currentStepLocked = false,
            constraints = WorkoutConstraintProfile(),
            request = WorkoutMidSessionRequest.Request(currentRepLimit = 10),
            remainingCurrentSeconds = 30,
            extensionMoveNames = emptySet(),
            level = 0
        )
        assertTrue(timed is WorkoutMidSessionPlanner.Result.Blocked)

        val parsed = WorkoutMidSessionRequest.parse(
            "Giảm còn 10 lần mỗi hiệp và nghỉ 90 giây"
        )
        assertTrue(parsed is WorkoutMidSessionRequest.Result.Proposed)
        val request = (parsed as WorkoutMidSessionRequest.Result.Proposed).request
        assertEquals(10, request.currentRepLimit)
        assertEquals(90, request.restSeconds)
        assertFalse(request.isRestOnly)
    }

}
