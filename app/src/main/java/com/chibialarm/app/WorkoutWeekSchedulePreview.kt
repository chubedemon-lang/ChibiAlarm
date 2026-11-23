package com.chibialarm.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a conservative, deterministic date preview for a proposed training frequency.
 *
 * The preview uses a fixed spacing pattern, not a fitness diagnosis or a mandatory target.
 * It does not save calendar events, change a plan, or imply that every suggested day is mandatory.
 */
internal object WorkoutWeekSchedulePreview {
    private val offsetsByDayCount = mapOf(
        1 to listOf(2),           // Wednesday
        2 to listOf(1, 4),        // Tuesday, Friday
        3 to listOf(0, 2, 5),     // Monday, Wednesday, Saturday
        4 to listOf(0, 2, 4, 6), // Monday, Wednesday, Friday, Sunday
        5 to listOf(0, 1, 3, 4, 6) // Two rest days remain in the draft
    )
    private val dayNames = listOf(
        "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm",
        "Thứ Sáu", "Thứ Bảy", "Chủ nhật"
    )
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)

    /** Returns dates in the strictly upcoming Monday–Sunday calendar week. */
    fun nextWeekDates(today: LocalDate, trainingDays: Int): List<LocalDate> {
        require(trainingDays in 1..5) { "A draft schedule supports one to five structured days" }
        val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return offsetsByDayCount.getValue(trainingDays).map { offset ->
            nextMonday.plusDays(offset.toLong())
        }
    }

    data class Draft(
        val trainingDates: List<LocalDate>,
        val restDates: List<LocalDate>,
        val repeatMask: Int
    )

    /** Builds a seven-day preview and a Calendar-compatible recurring reminder mask. */
    fun build(today: LocalDate, trainingDays: Int): Draft {
        val training = nextWeekDates(today, trainingDays)
        val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val allDays = (0L..6L).map(monday::plusDays)
        val trainingSet = training.toSet()
        val rest = allDays.filterNot(trainingSet::contains)
        val mask = training.fold(0) { value, date ->
            // java.util.Calendar uses Sunday=1; RoutineReminder stores Sunday at bit 0.
            value or (1 shl (date.dayOfWeek.value % 7))
        }
        require(mask in 1..127)
        return Draft(training, rest, mask)
    }

    fun format(date: LocalDate): String {
        val dayName = dayNames[date.dayOfWeek.value - 1]
        return "$dayName ${date.format(dateFormatter)}"
    }
}
