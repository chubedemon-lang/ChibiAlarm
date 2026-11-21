package com.chibialarm.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a conservative, deterministic date preview for a proposed training frequency.
 *
 * The preview is derived from observed active days, not a fitness diagnosis.
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

    fun format(date: LocalDate): String {
        val dayName = dayNames[date.dayOfWeek.value - 1]
        return "$dayName ${date.format(dateFormatter)}"
    }
}
