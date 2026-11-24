package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoutineWorkoutLaunchTest {
    private val bodyweightProgram = TrainingCatalog.programs.first { program ->
        program.moves.isNotEmpty() && program.moves.all { it.equipmentOptions.isEmpty() }
    }

    private fun routine(title: String, category: String = "🏃 Sức khỏe") =
        Routine(981731, title, category, 18, 0, 30, repeatMask = 1 shl 1)

    @Test fun resolvesWeeklyTitleToTheExactCatalogProgram() {
        val resolved = RoutineWorkoutLaunch.resolve(
            routine("Thứ Hai · ${bodyweightProgram.name}")
        )

        assertNotNull(resolved)
        assertEquals(bodyweightProgram.id, resolved?.id)
    }

    @Test fun keepsLegacyExactCatalogTitlesLaunchable() {
        val resolved = RoutineWorkoutLaunch.resolve(routine(bodyweightProgram.name))
        assertEquals(bodyweightProgram.id, resolved?.id)
    }

    @Test fun rejectsUnknownTitlesAndUnrecognizedWeekdayPrefixes() {
        assertNull(RoutineWorkoutLaunch.resolve(routine("Tập ${bodyweightProgram.name}")))
        assertNull(RoutineWorkoutLaunch.resolve(routine("Ngày tập · ${bodyweightProgram.name}")))
    }

    @Test fun rejectsNonFitnessRoutinesEvenWhenTheirTitleMatches() {
        assertNull(
            RoutineWorkoutLaunch.resolve(
                routine(bodyweightProgram.name, category = "📚 Học tập")
            )
        )
    }

    @Test fun doesNotGuessEquipmentForEquipmentDependentPrograms() {
        val weighted = TrainingCatalog.programs.first { program ->
            program.moves.any { it.equipmentOptions.isNotEmpty() }
        }
        assertNull(RoutineWorkoutLaunch.resolve(routine(weighted.name)))
    }

    @Test fun advancedProgramUsesTheAdvancedGuidanceLevel() {
        assertEquals(2, RoutineWorkoutLaunch.levelFor(TrainingCatalog.byId("advanced")))
        assertEquals(0, RoutineWorkoutLaunch.levelFor(bodyweightProgram))
    }
}
