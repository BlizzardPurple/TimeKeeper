package com.office.timekeeper.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Property-based tests for [WeekBoundaryUtil].
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**
 */
@DisplayName("WeekBoundaryUtil — Property Tests")
class WeekBoundaryUtilPropertyTest {

    // ---- Custom Arb generators ----

    /**
     * Generates a random [ZonedDateTime] in IST across a wide date range (2020-01-01 to 2030-12-31).
     * Randomizes year, month, day-of-month, hour, and minute.
     */
    private fun arbZonedDateTimeInIST(): Arb<ZonedDateTime> {
        // Generate epoch days in range [2020-01-01, 2030-12-31]
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        return Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            val hour = (epochDay * 7 + 3).mod(24)  // deterministic but varied hour
            val minute = (epochDay * 13 + 5).mod(60) // deterministic but varied minute
            ZonedDateTime.of(date, LocalTime.of(hour, minute), WeekBoundaryUtil.IST)
        }
    }

    /**
     * Generates a random [ZonedDateTime] in IST with independent hour/minute randomization.
     */
    private fun arbZonedDateTimeFullRandom(): Arb<ZonedDateTime> {
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        return Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            // Use a hash-like derivation to get varied hours/minutes per epoch day
            val hash = epochDay.hashCode().toLong()
            val hour = hash.mod(24).let { if (it < 0) it + 24 else it }
            val minute = (hash * 31).mod(60).let { if (it < 0) it + 60 else it }
            ZonedDateTime.of(date, LocalTime.of(hour, minute), WeekBoundaryUtil.IST)
        }
    }

    /**
     * Generates a random [LocalDate] range (start, end) where start <= end, within the same
     * 4-week window, for testing getWeekdaysInRange.
     */
    private fun arbDateRange(): Arb<Pair<LocalDate, LocalDate>> {
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        return Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            val start = LocalDate.ofEpochDay(epochDay)
            val rangeLen = epochDay.mod(28).toLong() // 0 to 27 days range
            val end = LocalDate.ofEpochDay(minOf(epochDay + rangeLen, endEpochDay))
            start to end
        }
    }

    // ========================================================================
    // Property 12: Week Boundary Determinism
    // ========================================================================

    /**
     * **Property 12: Week Boundary Determinism** — for any two instants in the same
     * attendance week, `getWeekStartForInstant()` returns the same Monday.
     *
     * Strategy: generate a random ZonedDateTime, compute its week start Monday, then
     * generate a second instant within that same week (Mon 00:00 to Sat 23:59, or
     * Sunday after-noon period), and verify both produce the same Monday.
     *
     * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**
     */
    @Test
    fun `property 12 - two instants in the same week produce the same Monday`() = runTest {
        checkAll(500, arbZonedDateTimeInIST()) { zdt ->
            val weekStart = WeekBoundaryUtil.getWeekStartForInstant(zdt)

            // Generate a second instant within the same attendance week.
            // The attendance week spans Monday 00:00 to Saturday 23:59 and
            // Sunday-after-noon (12:00+) of the following day.
            // Pick a random weekday offset (0..5 = Mon..Sat) within the week.
            val offsetDays = zdt.toLocalDate().toEpochDay().mod(6).toLong() // 0 to 5
            val sameWeekDate = weekStart.plusDays(offsetDays) // Mon through Sat
            val sameWeekInstant = ZonedDateTime.of(
                sameWeekDate,
                LocalTime.of(zdt.toLocalTime().hour.mod(24), zdt.toLocalTime().minute),
                WeekBoundaryUtil.IST
            )

            val weekStart2 = WeekBoundaryUtil.getWeekStartForInstant(sameWeekInstant)
            assertEquals(
                weekStart, weekStart2,
                "Two instants in the same week should produce the same Monday. " +
                    "zdt1=$zdt (weekStart=$weekStart), zdt2=$sameWeekInstant (weekStart2=$weekStart2)"
            )
        }
    }

    /**
     * Extension of Property 12: Sunday-after-noon instant and the following Monday
     * belong to the same attendance week.
     *
     * **Validates: Requirements 6.3, 6.6**
     */
    @Test
    fun `property 12 - Sunday after noon and next Monday share the same week start`() = runTest {
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        val arbSunday = Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            // Advance to the next Sunday
            var d = date
            while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.plusDays(1)
            d
        }

        checkAll(500, arbSunday) { sunday ->
            if (sunday.plusDays(1).toEpochDay() <= endEpochDay) {
                // Sunday after noon
                val sundayAfterNoon = ZonedDateTime.of(
                    sunday, LocalTime.of(14, 0), WeekBoundaryUtil.IST
                )
                // Following Monday morning
                val monday = sunday.plusDays(1)
                val mondayMorning = ZonedDateTime.of(
                    monday, LocalTime.of(9, 0), WeekBoundaryUtil.IST
                )

                val startFromSunday = WeekBoundaryUtil.getWeekStartForInstant(sundayAfterNoon)
                val startFromMonday = WeekBoundaryUtil.getWeekStartForInstant(mondayMorning)

                assertEquals(
                    startFromSunday, startFromMonday,
                    "Sunday after noon and next Monday should share the same week start. " +
                        "Sunday=$sunday, startFromSunday=$startFromSunday, startFromMonday=$startFromMonday"
                )
            }
        }
    }

    // ========================================================================
    // Additional Properties validating Requirements 6.1–6.6
    // ========================================================================

    /**
     * Week start is always a Monday — for any random ZonedDateTime,
     * getWeekStartForInstant always returns a date whose day-of-week is Monday.
     *
     * **Validates: Requirements 6.2, 6.3, 6.4**
     */
    @Test
    fun `property - week start is always a Monday`() = runTest {
        checkAll(1000, arbZonedDateTimeFullRandom()) { zdt ->
            val weekStart = WeekBoundaryUtil.getWeekStartForInstant(zdt)
            assertEquals(
                DayOfWeek.MONDAY, weekStart.dayOfWeek,
                "Week start must be a Monday. Got ${weekStart.dayOfWeek} for input $zdt"
            )
        }
    }

    /**
     * Week end is always a Friday — weekStart + 4 is always a Friday.
     *
     * **Validates: Requirement 6.5**
     */
    @Test
    fun `property - week end is always a Friday`() = runTest {
        checkAll(1000, arbZonedDateTimeFullRandom()) { zdt ->
            val weekStart = WeekBoundaryUtil.getWeekStartForInstant(zdt)
            val weekEnd = weekStart.plusDays(4)
            assertEquals(
                DayOfWeek.FRIDAY, weekEnd.dayOfWeek,
                "Week end must be a Friday. Got ${weekEnd.dayOfWeek} for weekStart=$weekStart"
            )
        }
    }

    /**
     * Sunday noon boundary — Sunday at 11:59 and Sunday at 12:00 produce different Mondays.
     * This verifies the noon boundary as the week edge (Req 6.1).
     *
     * **Validates: Requirements 6.1, 6.2, 6.3**
     */
    @Test
    fun `property - Sunday 11-59 and 12-00 produce different Mondays`() = runTest {
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        val arbSunday = Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            var d = date
            while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.plusDays(1)
            d
        }

        checkAll(500, arbSunday) { sunday ->
            val beforeNoon = ZonedDateTime.of(
                sunday, LocalTime.of(11, 59), WeekBoundaryUtil.IST
            )
            val atNoon = ZonedDateTime.of(
                sunday, LocalTime.of(12, 0), WeekBoundaryUtil.IST
            )

            val mondayBefore = WeekBoundaryUtil.getWeekStartForInstant(beforeNoon)
            val mondayAfter = WeekBoundaryUtil.getWeekStartForInstant(atNoon)

            assertNotEquals(
                mondayBefore, mondayAfter,
                "Sunday 11:59 and 12:00 must produce different Mondays. " +
                    "Sunday=$sunday, before=$mondayBefore, after=$mondayAfter"
            )
            // The difference should be exactly 7 days
            assertEquals(
                7L, mondayAfter.toEpochDay() - mondayBefore.toEpochDay(),
                "The two Mondays should be exactly 7 days apart"
            )
        }
    }

    /**
     * Weekdays in range are always Mon–Fri — no Saturday or Sunday in getWeekdaysInRange output.
     *
     * **Validates: Requirement 6.5 (Mon–Fri attendance week)**
     */
    @Test
    fun `property - weekdays in range contain only Mon through Fri`() = runTest {
        checkAll(500, arbDateRange()) { (start, end) ->
            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(start, end)
            weekdays.forEach { date ->
                assertTrue(
                    date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY,
                    "getWeekdaysInRange should not contain weekends. Found ${date.dayOfWeek} ($date)"
                )
            }
        }
    }

    /**
     * Weekdays in range are sorted in ascending order.
     *
     * **Validates: Requirement 6.5**
     */
    @Test
    fun `property - weekdays in range are in ascending order`() = runTest {
        checkAll(500, arbDateRange()) { (start, end) ->
            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(start, end)
            for (i in 1 until weekdays.size) {
                assertTrue(
                    weekdays[i].isAfter(weekdays[i - 1]),
                    "Weekdays must be sorted ascending. Found ${weekdays[i - 1]} before ${weekdays[i]}"
                )
            }
        }
    }

    /**
     * For Monday through Saturday, getWeekStartForInstant returns the current ISO week's Monday.
     * This tests the non-Sunday path directly.
     *
     * **Validates: Requirement 6.4**
     */
    @Test
    fun `property - Mon through Sat returns current ISO Monday`() = runTest {
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        // Generate only Mon-Sat dates
        val arbMonToSat = Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            var date = LocalDate.ofEpochDay(epochDay)
            // Skip Sundays
            if (date.dayOfWeek == DayOfWeek.SUNDAY) date = date.plusDays(1)
            val hour = epochDay.mod(24).let { if (it < 0) it + 24 else it }
            ZonedDateTime.of(date, LocalTime.of(hour, 30), WeekBoundaryUtil.IST)
        }

        checkAll(500, arbMonToSat) { zdt ->
            val weekStart = WeekBoundaryUtil.getWeekStartForInstant(zdt)
            val expectedMonday = zdt.toLocalDate().with(DayOfWeek.MONDAY)
            assertEquals(
                expectedMonday, weekStart,
                "For ${zdt.toLocalDate().dayOfWeek} ($zdt), expected ISO Monday $expectedMonday but got $weekStart"
            )
        }
    }

    /**
     * Sunday before noon returns the previous week's Monday (6 days back).
     *
     * **Validates: Requirement 6.2**
     */
    @Test
    fun `property - Sunday before noon returns previous Monday`() = runTest {
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        val arbSundayBeforeNoon = Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            var date = LocalDate.ofEpochDay(epochDay)
            while (date.dayOfWeek != DayOfWeek.SUNDAY) date = date.plusDays(1)
            val hour = epochDay.mod(12).let { if (it < 0) it + 12 else it } // 0..11
            ZonedDateTime.of(date, LocalTime.of(hour, 0), WeekBoundaryUtil.IST)
        }

        checkAll(500, arbSundayBeforeNoon) { zdt ->
            val weekStart = WeekBoundaryUtil.getWeekStartForInstant(zdt)
            val expectedMonday = zdt.toLocalDate().minusDays(6)
            assertEquals(
                expectedMonday, weekStart,
                "Sunday before noon should return previous Monday. " +
                    "Sunday=${zdt.toLocalDate()}, expected=$expectedMonday, got=$weekStart"
            )
            assertEquals(DayOfWeek.MONDAY, weekStart.dayOfWeek)
        }
    }

    /**
     * Sunday at/after noon returns the next Monday (1 day forward).
     *
     * **Validates: Requirement 6.3**
     */
    @Test
    fun `property - Sunday at or after noon returns next Monday`() = runTest {
        val startEpochDay = LocalDate.of(2020, 1, 1).toEpochDay()
        val endEpochDay = LocalDate.of(2030, 12, 31).toEpochDay()

        val arbSundayAfterNoon = Arb.long(startEpochDay..endEpochDay).map { epochDay ->
            var date = LocalDate.ofEpochDay(epochDay)
            while (date.dayOfWeek != DayOfWeek.SUNDAY) date = date.plusDays(1)
            val hour = 12 + epochDay.mod(12).let { if (it < 0) it + 12 else it } // 12..23
            ZonedDateTime.of(date, LocalTime.of(hour, 0), WeekBoundaryUtil.IST)
        }

        checkAll(500, arbSundayAfterNoon) { zdt ->
            val weekStart = WeekBoundaryUtil.getWeekStartForInstant(zdt)
            val expectedMonday = zdt.toLocalDate().plusDays(1)
            assertEquals(
                expectedMonday, weekStart,
                "Sunday at/after noon should return next Monday. " +
                    "Sunday=${zdt.toLocalDate()}, expected=$expectedMonday, got=$weekStart"
            )
            assertEquals(DayOfWeek.MONDAY, weekStart.dayOfWeek)
        }
    }
}
