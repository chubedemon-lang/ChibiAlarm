package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutMoveSubstitutionTest {
    private fun catalogMove(name: String): TrainingMove =
        TrainingCatalog.programs.flatMap { it.moves }.first { it.name == name }

    @Test fun offersOnlyCuratedLowerImpactAlternatives() {
        val squat = catalogMove("Squat không tạ")
        val options = WorkoutMoveSubstitution.alternativesFor(squat)
        assertEquals(listOf("Đứng lên – ngồi xuống"), options.map { it.name })

        val push = catalogMove("Chống đẩy sàn")
        assertTrue(TrainingCatalog.allMoves.any { it.name == "Chống đẩy gối" })
        assertEquals(
            listOf("Chống đẩy gối", "Chống đẩy tường"),
            WorkoutMoveSubstitution.alternativesFor(push).map { it.name }
        )
        assertTrue(WorkoutMoveSubstitution.alternativesFor(catalogMove("Khởi động tại chỗ")).isEmpty())
    }

    @Test fun replacementChangesOnlyTheSelectedStep() {
        val base = listOf(catalogMove("Squat không tạ"), catalogMove("Chống đẩy sàn"))
        val replacement = catalogMove("Đứng lên – ngồi xuống")
        val updated = WorkoutMoveSubstitution.replace(base, 0, replacement)
        assertNotNull(updated)
        assertEquals(replacement, updated!![0])
        assertEquals(base[1], updated[1])
        assertEquals("Squat không tạ", base[0].name) // source plan remains immutable
    }

    @Test fun rejectsUnlistedAlternativesAndInvalidIndexes() {
        val base = listOf(catalogMove("Squat không tạ"))
        assertNull(WorkoutMoveSubstitution.replace(base, 0, catalogMove("Chống đẩy tường")))
        assertNull(WorkoutMoveSubstitution.replace(base, -1, catalogMove("Đứng lên – ngồi xuống")))
        assertNull(WorkoutMoveSubstitution.replace(base, 4, catalogMove("Đứng lên – ngồi xuống")))
        val canonical = catalogMove("Đứng lên – ngồi xuống")
        val spoofed = canonical.copy(steps = "instructions supplied by caller")
        val safe = WorkoutMoveSubstitution.replace(base, 0, spoofed)!!
        assertEquals(canonical.steps, safe[0].steps)
    }

    @Test fun checkpointRoundTripRestoresOnlyApprovedSubstitutions() {
        val base = listOf(
            catalogMove("Squat không tạ"),
            catalogMove("Chống đẩy sàn"),
            catalogMove("Khởi động tại chỗ")
        )
        val substitutions = mapOf(
            0 to catalogMove("Đứng lên – ngồi xuống"),
            1 to catalogMove("Chống đẩy tường"),
            2 to catalogMove("Chống đẩy tường")
        )
        val encoded = WorkoutMoveSubstitution.encode(substitutions, base)
        val decoded = WorkoutMoveSubstitution.decode(encoded, base)
        assertEquals(2, decoded.size)
        assertEquals("Đứng lên – ngồi xuống", decoded[0]?.name)
        assertEquals("Chống đẩy tường", decoded[1]?.name)
        val restored = WorkoutMoveSubstitution.apply(base, encoded)
        assertEquals("Đứng lên – ngồi xuống", restored[0].name)
        assertEquals("Chống đẩy tường", restored[1].name)
        assertEquals("Khởi động tại chỗ", restored[2].name)
    }

    @Test fun malformedDuplicateAndOversizedCheckpointEntriesAreIgnored() {
        val base = listOf(catalogMove("Squat không tạ"))
        val valid = WorkoutMoveSubstitution.encode(
            mapOf(0 to catalogMove("Đứng lên – ngồi xuống")), base
        )
        assertEquals(
            mapOf(0 to catalogMove("Đứng lên – ngồi xuống")),
            WorkoutMoveSubstitution.decode("0:unknown;$valid;9:$valid", base)
        )
        assertTrue(WorkoutMoveSubstitution.decode("x:bad;0:%ZZ", base).isEmpty())
        assertTrue(WorkoutMoveSubstitution.decode("a".repeat(4_097), base).isEmpty())
    }

    @Test fun wristAvoidanceDoesNotOfferOrRestoreHandLoadingEasierAlternatives() {
        val profile = WorkoutConstraintProfile(avoidWristLoad = true)
        val push = catalogMove("Chống đẩy sàn")
        assertTrue(WorkoutMoveSubstitution.alternativesFor(push, profile).isEmpty())

        val plank = catalogMove("Plank khuỷu hoặc plank gối")
        val birdDog = catalogMove("Bird-dog")
        val base = listOf(plank)
        assertNull(WorkoutMoveSubstitution.replace(base, 0, birdDog, profile))
        val unsafeCheckpoint = "0:Bird-dog"
        assertTrue(WorkoutMoveSubstitution.decode(unsafeCheckpoint, base, profile).isEmpty())
    }


}
