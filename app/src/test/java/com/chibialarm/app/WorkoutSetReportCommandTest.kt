package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSetReportCommandTest {
    @Test fun acceptsExplicitNumericSetReports() {
        assertEquals(
            WorkoutSetReportCommand.Report(10, "lần"),
            WorkoutSetReportCommand.parse("Tôi đã làm được 10 lần")
        )
        assertEquals(
            WorkoutSetReportCommand.Report(8, "lần"),
            WorkoutSetReportCommand.parse("Hiệp này 8 lần")
        )
        assertEquals(
            WorkoutSetReportCommand.Report(12, "lần"),
            WorkoutSetReportCommand.parse("Đã hoàn thành 12 reps rồi")
        )
    }

    @Test fun acceptsCommonVietnameseSpokenNumbers() {
        assertEquals(
            WorkoutSetReportCommand.Report(15, "lần"),
            WorkoutSetReportCommand.parse("Tôi thực hiện mười lăm lần")
        )
        assertEquals(
            WorkoutSetReportCommand.Report(25, "lần"),
            WorkoutSetReportCommand.parse("Tôi làm được hai mươi lăm lần")
        )
        assertEquals(
            WorkoutSetReportCommand.Report(102, "lần"),
            WorkoutSetReportCommand.parse("Tôi đã làm một trăm linh hai lần")
        )
    }

    @Test fun supportsTimedHoldsAndRhythmBasedMoves() {
        assertEquals(
            WorkoutSetReportCommand.Report(20, "giây"),
            WorkoutSetReportCommand.parse("Tôi giữ được hai mươi giây")
        )
        assertEquals(
            WorkoutSetReportCommand.Report(15, "nhịp"),
            WorkoutSetReportCommand.parse("Tôi đã thực hiện 15 nhịp")
        )
        assertEquals(
            WorkoutSetReportCommand.Report(30, "giây"),
            WorkoutSetReportCommand.parse("Đã giữ 30 s")
        )
    }

    @Test fun targetChangesAreNotMisreadAsCompletedPerformance() {
        assertNull(WorkoutSetReportCommand.parse("Giảm còn 10 lần"))
        assertNull(WorkoutSetReportCommand.parse("Tăng lên 12 lần"))
        assertNull(WorkoutSetReportCommand.parse("Đổi thành 8 lần"))
        assertNull(WorkoutSetReportCommand.parse("10 lần"))
    }

    @Test fun mixedCommandsAndSafetyReportsAreRejected() {
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 10 lần và nghỉ thêm 30 giây"))
        assertNull(WorkoutSetReportCommand.parse("Đau gối, tôi đã làm 10 lần"))
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 10 lần rồi đau gối"))
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 10 lần rồi chuyển bài"))
    }

    @Test fun unitAndValueBoundsAreStrict() {
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 10 lần rồi nghỉ"))
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 0 lần"))
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 201 lần"))
        assertNull(WorkoutSetReportCommand.parse("Tôi giữ 601 giây"))
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 10 phút"))
        assertNull(WorkoutSetReportCommand.parse("Tôi đã làm 10 lần".repeat(20)))
    }
}
