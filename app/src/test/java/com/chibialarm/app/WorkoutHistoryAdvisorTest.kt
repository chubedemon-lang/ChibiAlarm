package com.chibialarm.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryAdvisorTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun workout(
        date: String,
        minutes: Int = 30,
        name: String = "Đi bộ",
        note: String = "private free-text note"
    ) = Workout(date = date, name = name, minutes = minutes, note = note)

    @Test fun sevenDayWindowIncludesTodayAndSixDaysAgoButNotOlderOrFutureRecords() {
        val summary = WorkoutHistoryAdvisor.summarize(listOf(
            workout(today.toString()),
            workout(today.minusDays(6).toString()),
            workout(today.minusDays(7).toString()),
            workout(today.plusDays(1).toString())
        ), today)
        assertEquals(2, summary.sessions)
        assertEquals(2, summary.activeDays)
        assertEquals(60, summary.totalMinutes)
        assertEquals(today, summary.latestDate)
    }

    @Test fun malformedDatesAndInvalidDurationsAreIgnored() {
        val summary = WorkoutHistoryAdvisor.summarize(listOf(
            workout("not-a-date"),
            workout(today.toString(), 0),
            workout(today.toString(), 601),
            workout(today.toString(), 25)
        ), today)
        assertEquals(1, summary.sessions)
        assertEquals(1, summary.activeDays)
        assertEquals(25, summary.totalMinutes)
    }

    @Test fun multipleSessionsOnOneDateAddMinutesButCountAsOneActiveDay() {
        val summary = WorkoutHistoryAdvisor.summarize(listOf(
            workout(today.toString(), 40),
            workout(today.toString(), 35),
            workout(today.minusDays(1).toString(), 30)
        ), today)
        assertEquals(3, summary.sessions)
        assertEquals(2, summary.activeDays)
        assertEquals(105, summary.totalMinutes)
    }

    @Test fun lighterSessionHintRequiresFiveActiveDaysAndAtLeast180Minutes() {
        val fiveDays = (0..4).map { workout(today.minusDays(it.toLong()).toString(), 36) }
        assertTrue(WorkoutHistoryAdvisor.summarize(fiveDays, today).suggestLighterSession)

        val belowMinutes = fiveDays.map { it.copy(minutes = 35) }
        assertFalse(WorkoutHistoryAdvisor.summarize(belowMinutes, today).suggestLighterSession)

        val fourDays = (0..3).map { workout(today.minusDays(it.toLong()).toString(), 60) }
        assertFalse(WorkoutHistoryAdvisor.summarize(fourDays, today).suggestLighterSession)
    }

    @Test fun summaryDoesNotCopyFreeTextJournalNotes() {
        val summary = WorkoutHistoryAdvisor.summarize(listOf(
            workout(today.toString(), note = "private health detail")
        ), today)
        assertFalse(summary.toString().contains("private health detail"))
    }

    @Test fun suggestsEligibleSameCategoryAlternativeAfterSameGuidedProgramYesterday() {
        val recommended = TrainingCatalog.byId("full")
        val previous = workout(
            date = today.minusDays(1).toString(),
            name = recommended.name,
            note = "private note must not be inspected"
        )
        val alternatives = listOf(
            TrainingCatalog.byId("legs"),
            TrainingCatalog.byId("upper"),
            TrainingCatalog.byId("core")
        )

        val suggestion = WorkoutHistoryAdvisor.suggestVariety(
            recommended, listOf(previous), alternatives, today
        )

        assertNotNull(suggestion)
        assertNotEquals(recommended.id, suggestion!!.program.id)
        assertEquals(recommended.category, suggestion.program.category)
        assertTrue(alternatives.any { it.id == suggestion.program.id })
        assertTrue(suggestion.reason.contains(recommended.name))
        assertFalse(suggestion.toString().contains("private note"))
    }

    @Test fun doesNotSuggestVarietyWhenYesterdayWasDifferentProgram() {
        val recommended = TrainingCatalog.byId("full")
        val previous = workout(
            date = today.minusDays(1).toString(),
            name = TrainingCatalog.byId("legs").name
        )
        val suggestion = WorkoutHistoryAdvisor.suggestVariety(
            recommended,
            listOf(previous),
            listOf(TrainingCatalog.byId("upper"), TrainingCatalog.byId("core")),
            today
        )
        assertNull(suggestion)
    }

    @Test fun doesNotSuggestVarietyForSameDayOlderFutureOrManualEntries() {
        val recommended = TrainingCatalog.byId("full")
        val alternatives = listOf(TrainingCatalog.byId("legs"), TrainingCatalog.byId("upper"))
        val sameDay = workout(today.toString(), name = recommended.name)
        val older = workout(today.minusDays(2).toString(), name = recommended.name)
        val future = workout(today.plusDays(1).toString(), name = recommended.name)
        val manual = workout(today.minusDays(1).toString(), name = "🏋️ Tập tạ")

        listOf(sameDay, older, future, manual).forEach { entry ->
            assertNull(
                WorkoutHistoryAdvisor.suggestVariety(recommended, listOf(entry), alternatives, today)
            )
        }
    }

    @Test fun varietyHintNeverEscalatesABeginnerStarterPlan() {
        val recommended = TrainingCatalog.byId("starter")
        val previous = workout(today.minusDays(1).toString(), name = recommended.name)
        assertNull(
            WorkoutHistoryAdvisor.suggestVariety(
                recommended,
                listOf(previous),
                listOf(TrainingCatalog.byId("full")),
                today
            )
        )
    }

    @Test fun noSuggestionWhenNoEligibleSameCategoryAlternativeExists() {
        val recommended = TrainingCatalog.byId("full")
        val previous = workout(today.minusDays(1).toString(), name = recommended.name)
        assertNull(
            WorkoutHistoryAdvisor.suggestVariety(
                recommended,
                listOf(previous),
                listOf(TrainingCatalog.byId("lowcardio")),
                today
            )
        )
    }

    @Test fun malformedHistoryAndOutOfRangeDurationCannotTriggerVariety() {
        val recommended = TrainingCatalog.byId("full")
        val alternatives = listOf(TrainingCatalog.byId("legs"), TrainingCatalog.byId("upper"))
        val malformed = workout("yesterday", name = recommended.name)
        val invalidDuration = workout(today.minusDays(1).toString(), 601, name = recommended.name)
        listOf(malformed, invalidDuration).forEach { entry ->
            assertNull(
                WorkoutHistoryAdvisor.suggestVariety(recommended, listOf(entry), alternatives, today)
            )
        }
    }
}
