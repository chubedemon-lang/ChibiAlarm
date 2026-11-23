package com.chibialarm.app

import com.chibialarm.app.fitness.WorkoutMidSessionPlanner
import com.chibialarm.app.fitness.WorkoutMidSessionRequest
import org.junit.Assert.*
import org.junit.Test

class WorkoutConstraintFilterTest {
    private val wristOnly = WorkoutConstraintProfile(avoidWristLoad = true)

    @Test fun noRestrictionPreservesTheOriginalProgram() {
        val original = TrainingCatalog.byId("full")
        val result = WorkoutConstraintFilter.apply(original, WorkoutConstraintProfile())
        assertSame(original, result.program)
        assertTrue(result.substitutions.isEmpty())
        assertTrue(result.removedMoves.isEmpty())
    }

    @Test fun fullBodyPlanReplacesPalmSupportedMovesAndKeepsTheSourceImmutable() {
        val original = TrainingCatalog.byId("full")
        val result = WorkoutConstraintFilter.apply(original, wristOnly)

        assertTrue(result.canStart)
        assertTrue(result.program.moves.any { it.name == "Ép ngực tường bằng cẳng tay" })
        assertTrue(result.program.moves.any { it.name == "Dead bug cơ bụng" })
        assertTrue(result.program.moves.all { WorkoutConstraintFilter.isAllowed(it, wristOnly) })
        assertEquals(listOf("Khởi động tại chỗ", "Squat không tạ", "Chống đẩy tường",
            "Nâng hông trên sàn", "Bird-dog", "Plank khuỷu hoặc plank gối", "Thả lỏng"),
            original.moves.map { it.name })
        assertTrue(result.notice.contains("không tương đương hoàn toàn"))
    }

    @Test fun weightedPlanDoesNotKeepGripLoadedCatalogMoves() {
        val result = WorkoutConstraintFilter.apply(TrainingCatalog.byId("weighted"), wristOnly)
        assertTrue(result.program.moves.none { it.requiresExternalLoad })
        assertTrue(result.program.moves.any { it.name == "Siết bả vai đứng" })
        assertTrue(result.program.moves.any { it.name == "Gập hông không dụng cụ" })
        assertTrue(result.program.moves.any { it.name == "Mở vai và ngực" })
        assertTrue(result.program.moves.all { WorkoutConstraintFilter.isAllowed(it, wristOnly) })
    }

    @Test fun everyCuratedProgramRemainsBuildableAfterFiltering() {
        TrainingCatalog.programs.forEach { program ->
            val filtered = WorkoutConstraintFilter.apply(program, wristOnly)
            assertTrue("No main movement remains for ${program.id}", filtered.canStart)
            val plan = WorkoutDurationPlanner.build(filtered.program, level = 0, targetMinutes = 20)
            assertTrue(plan.moves.isNotEmpty())
            assertTrue(plan.moves.all { WorkoutConstraintFilter.isAllowed(it, wristOnly) })
        }
    }

    @Test fun substitutionsUseCanonicalCatalogInstructions() {
        val result = WorkoutConstraintFilter.apply(TrainingCatalog.byId("upper"), wristOnly)
        val press = result.program.moves.first { it.name == "Ép ngực tường bằng cẳng tay" }
        assertTrue(press.steps.contains("cẳng tay"))
        assertTrue(press.steps.contains("cổ tay thả lỏng"))
        assertFalse(press.steps.contains("instructions supplied by caller"))
    }

    @Test fun planFingerprintIsStableAndChangesWhenTheConstraintChanges() {
        val original = TrainingCatalog.byId("full").moves
        val filtered = WorkoutConstraintFilter.apply(
            TrainingCatalog.byId("full"),
            WorkoutConstraintProfile(avoidWristLoad = true)
        ).program.moves
        assertEquals(
            WorkoutConstraintFilter.fingerprint(original),
            WorkoutConstraintFilter.fingerprint(original.toList())
        )
        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(original),
            WorkoutConstraintFilter.fingerprint(filtered)
        )
    }

    @Test fun fingerprintChangesWhenEquipmentRequirementsChange() {
        val original = TrainingCatalog.byId("weighted").moves
        val changed = original.mapIndexed { index, move ->
            if (index == 1) move.copy(equipmentOptions = emptySet()) else move
        }
        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(original),
            WorkoutConstraintFilter.fingerprint(changed)
        )
    }

    @Test fun fingerprintChangesWhenOnlyTheRestPrescriptionChanges() {
        val original = TrainingCatalog.byId("full").moves
        val changedRest = original.mapIndexed { index, move ->
            if (index == 1) move.copy(restSeconds = move.restSeconds + 15) else move
        }

        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(original),
            WorkoutConstraintFilter.fingerprint(changedRest)
        )
    }



    @Test fun overheadReachPreferenceUsesTheReviewedNonOverheadShoulderAlternative() {
        val profile = WorkoutConstraintProfile(avoidOverheadReach = true)
        val original = TrainingCatalog.byId("weighted")
        val result = WorkoutConstraintFilter.apply(original, profile)

        assertTrue(result.canStart)
        assertTrue(result.removedMoves.isEmpty())
        assertTrue(result.substitutions.any {
            it.originalName == "Đẩy vai với tạ đơn hoặc dây" && it.replacementName == "Mở vai và ngực"
        })
        assertTrue(result.program.moves.none { it.requiresOverheadReach })
        assertTrue(result.program.moves.any { it.name == "Kéo tạ đơn hoặc dây kháng lực" })
        assertTrue(result.program.moves.any { it.name == "Gập hông với tạ đơn hoặc dây" })
        assertTrue(result.notice.contains("không tương đương hoàn toàn"))
        assertTrue(original.moves.any { it.requiresOverheadReach })
    }

    @Test fun overheadReachPreferenceChangesTheCheckpointFingerprint() {
        val moves = TrainingCatalog.byId("weighted").moves
        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(moves),
            WorkoutConstraintFilter.fingerprint(
                moves, WorkoutConstraintProfile(avoidOverheadReach = true)
            )
        )
    }

    @Test fun floorPositionPreferenceUsesReviewedStandingAlternativesAndKeepsTheSourceImmutable() {
        val profile = WorkoutConstraintProfile(avoidFloorPositions = true)
        val original = TrainingCatalog.byId("full")
        val result = WorkoutConstraintFilter.apply(original, profile)

        assertTrue(result.canStart)
        assertTrue(result.substitutions.any {
            it.originalName == "Nâng hông trên sàn" && it.replacementName == "Gập hông không dụng cụ"
        })
        assertTrue(result.substitutions.any {
            it.originalName == "Bird-dog" && it.replacementName == "Đứng thăng bằng có hỗ trợ"
        })
        assertTrue(result.substitutions.any {
            it.originalName == "Plank khuỷu hoặc plank gối" && it.replacementName == "Gồng cơ bụng đứng"
        })
        assertTrue(result.removedMoves.isEmpty())
        assertTrue(result.program.moves.all { WorkoutConstraintFilter.isAllowed(it, profile) })
        assertEquals(result.program.moves.size, result.program.moves.map { it.name }.distinct().size)
        assertTrue(original.moves.any { it.name == "Bird-dog" })
    }

    @Test fun floorBasedCorePlanOffersDistinctReviewedStandingOptions() {
        val profile = WorkoutConstraintProfile(avoidFloorPositions = true)
        val original = TrainingCatalog.byId("core")
        val result = WorkoutConstraintFilter.apply(original, profile)

        assertTrue(result.canStart)
        assertTrue(result.program.moves.any { it.name == "Đứng thăng bằng có hỗ trợ" })
        assertTrue(result.program.moves.any { it.name == "Gồng cơ bụng đứng" })
        assertTrue(result.program.moves.any { it.name == "Dang chân sang bên có điểm tựa" })
        assertTrue(result.program.moves.all { WorkoutConstraintFilter.isAllowed(it, profile) })
        assertEquals(result.program.moves.size, result.program.moves.map { it.name }.distinct().size)
        assertTrue(result.removedMoves.contains("Plank khuỷu hoặc plank gối"))
        assertEquals(original.moves, TrainingCatalog.byId("core").moves)
        assertTrue(result.notice.contains("không tương đương hoàn toàn"))
    }

    @Test fun floorPreferenceMidSessionReplacesOnlyUnstartedMovesAndPreservesCompletedPrefix() {
        val move = { name: String -> TrainingCatalog.allMoves.single { it.name == name } }
        val warmup = move("Khởi động tại chỗ")
        val moves = listOf(
            warmup, move("Bird-dog"), move("Dead bug cơ bụng"),
            move("Plank khuỷu hoặc plank gối"), move("Side plank gối"), move("Thả lỏng")
        )
        val result = WorkoutMidSessionPlanner.preview(
            currentMoves = moves,
            currentIndex = 1,
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
        assertEquals(warmup, preview.after.first())
        assertEquals(
            listOf("Khởi động tại chỗ", "Đứng thăng bằng có hỗ trợ", "Gồng cơ bụng đứng",
                "Dang chân sang bên có điểm tựa", "Thả lỏng"),
            preview.after.map { it.name }
        )
        assertTrue(preview.after.all {
            WorkoutConstraintFilter.isAllowed(it, WorkoutConstraintProfile(avoidFloorPositions = true))
        })
    }

    @Test fun standingCoreBraceHasCuratedCatalogAndIllustrationMetadata() {
        val move = TrainingCatalog.allMoves.single { it.name == "Gồng cơ bụng đứng" }
        assertTrue(move.dose.contains("hiệp"))
        assertTrue(move.steps.contains("vẫn thở bình thường"))
        assertTrue(move.easier.isNotBlank())
        assertTrue(move.avoid.contains("Không nín thở"))
        assertTrue(move.equipmentOptions.isEmpty())
        assertEquals(listOf(TrainingMuscleGroup.CORE), move.muscleFocus.primary)
        val pose = WorkoutPoseGuide.classify(move.name)
        assertEquals(WorkoutPoseGuide.Pose.STANDING_CORE_BRACE, pose)
        assertFalse(WorkoutPoseMotion.hasMotion(pose))
        assertTrue(WorkoutPoseGuide.label(pose).contains("vẫn thở đều"))
        assertTrue(WorkoutPoseMotion.spokenWalkthrough(pose).contains("vẫn thở bình thường"))
    }

    @Test fun fingerprintChangesWhenTheSameMovesHaveDifferentActiveConstraints() {
        val moves = TrainingCatalog.byId("full").moves
        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(moves),
            WorkoutConstraintFilter.fingerprint(
                moves, WorkoutConstraintProfile(avoidFloorPositions = true)
            )
        )
    }

    @Test fun lowImpactProfileReplacesOnlyTheReviewedJumpingMove() {
        val profile = WorkoutConstraintProfile(avoidJumping = true)
        val original = TrainingCatalog.byId("advanced")
        val result = WorkoutConstraintFilter.apply(original, profile)

        assertTrue(result.canStart)
        assertTrue(result.substitutions.any {
            it.originalName == "Bật nhảy dang tay chân" && it.replacementName == "Bước ngang nhẹ"
        })
        assertFalse(result.program.moves.any { it.requiresJumping })
        assertTrue(result.program.moves.all { WorkoutConstraintFilter.isAllowed(it, profile) })
        assertTrue(original.moves.any { it.requiresJumping })
    }

    @Test fun jumpingPreferenceChangesTheCheckpointFingerprint() {
        val moves = TrainingCatalog.byId("advanced").moves
        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(moves),
            WorkoutConstraintFilter.fingerprint(
                moves, WorkoutConstraintProfile(avoidJumping = true)
            )
        )
    }


    @Test fun lowImpactSelectionFiltersJumpingBeforePlanAndHistoryCandidatesArePreviewed() {
        val profile = WorkoutConstraintProfile.fromSessionSelections(impact = 1)

        assertTrue(profile.avoidJumping)
        val filteredCandidates = TrainingCatalog.programs.map { candidate ->
            WorkoutConstraintFilter.apply(candidate, profile)
        }
        assertTrue(
            "No candidate shown under low-impact selection may retain a jumping move",
            filteredCandidates.flatMap { it.program.moves }.none { it.requiresJumping }
        )

        val advanced = filteredCandidates.single { it.program.id == "advanced" }
        assertTrue(advanced.canStart)
        assertTrue(advanced.substitutions.any {
            it.originalName == "Bật nhảy dang tay chân" && it.replacementName == "Bước ngang nhẹ"
        })
    }

    @Test fun ordinaryImpactSelectionDoesNotSilentlyApplyNoJumpRestriction() {
        val profile = WorkoutConstraintProfile.fromSessionSelections(impact = 0)
        assertFalse(profile.avoidJumping)
        assertTrue(
            WorkoutConstraintFilter.apply(TrainingCatalog.byId("advanced"), profile)
                .program.moves.any { it.requiresJumping }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidImpactCannotCreateSessionConstraintProfile() {
        WorkoutConstraintProfile.fromSessionSelections(impact = 3)
    }



    @Test fun kneelingPreferenceUsesReviewedAlternativesButKeepsSupineAndStandingMoves() {
        val bird = TrainingCatalog.allMoves.first { it.name == "Bird-dog" }
        val catCow = TrainingCatalog.allMoves.first { it.name == "Cat-cow nhẹ" }
        val kneePush = TrainingCatalog.allMoves.first { it.name == "Chống đẩy gối" }
        val bridge = TrainingCatalog.allMoves.first { it.name == "Nâng hông trên sàn" }
        val program = TrainingProgram(
            "kneeling-test", "Kneeling test", "test", "test",
            listOf(bird, catCow, kneePush, bridge)
        )

        val profile = WorkoutConstraintProfile(avoidKneeling = true)
        val result = WorkoutConstraintFilter.apply(program, profile)

        assertTrue(result.canStart)
        assertTrue(result.program.moves.none { it.requiresKneeling })
        assertTrue(result.substitutions.any {
            it.originalName == "Bird-dog" && it.replacementName == "Dead bug cơ bụng"
        })
        assertTrue(result.substitutions.any {
            it.originalName == "Cat-cow nhẹ" && it.replacementName == "Mở vai và ngực"
        })
        assertTrue(result.substitutions.any {
            it.originalName == "Chống đẩy gối" && it.replacementName == "Chống đẩy tường"
        })
        assertTrue(result.program.moves.any { it.name == "Nâng hông trên sàn" })
        assertTrue(result.program.moves.any { it.name == "Dead bug cơ bụng" })
    }

    @Test fun kneelingPreferenceChangesTheCheckpointFingerprint() {
        val moves = TrainingCatalog.byId("full").moves
        assertNotEquals(
            WorkoutConstraintFilter.fingerprint(moves),
            WorkoutConstraintFilter.fingerprint(
                moves, WorkoutConstraintProfile(avoidKneeling = true)
            )
        )
    }

    @Test fun floorAvoidanceAlternativesHavePlainLanguageExplanationsAndRespectTheConstraint() {
        val profile = WorkoutConstraintProfile(avoidFloorPositions = true)
        val names = listOf("Bird-dog", "Dead bug cơ bụng", "Plank khuỷu hoặc plank gối",
            "Side plank gối", "Nâng hông trên sàn", "Cat-cow nhẹ")
        names.forEach { name ->
            val source = TrainingCatalog.allMoves.single { it.name == name }
            val alternatives = WorkoutMoveSubstitution.alternativesFor(source, profile)
            assertTrue(name + " should have a curated standing alternative", alternatives.isNotEmpty())
            alternatives.forEach { alternative ->
                assertTrue(WorkoutConstraintFilter.isAllowed(alternative, profile))
                assertNotNull(WorkoutMoveSubstitution.explanationFor(source, alternative))
            }
        }
    }

}
