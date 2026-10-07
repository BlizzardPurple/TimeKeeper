package com.office.timekeeper.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

@DisplayName("WeekBoundaryUtil")
class WeekBoundaryUtilTest {

    // ---- helpers ----

    /** Build a ZonedDateTime in IST for a given date and time. */
    private fun istAt(date: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), WeekBoundaryUtil.IST)

    /** Find the next occurrence of a specific day-of-week on or after a date. */
    private fun nextOrSame(date: LocalDate, dow: DayOfWeek): LocalDate {
        var d = date
        while (d.dayOfWeek != dow) d = d.plusDays(1)
        return d
    }

    // ---------- getCurrentWeekStart / getWeekStartForInstant ----------

    @Nested
    @DisplayName("getWeekStartForInstant — Sunday before noon (Req 6.2)")
    inner class SundayBeforeNoon {

        @Test
        fun `Sunday 11-59 AM returns previous Monday`() {
            // Sunday 2025-07-06 at 11:59 IST
            val sunday = LocalDate.of(2025, 7, 6) // Sunday
            val now = istAt(sunday, 11, 59)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            // Previous Monday is June 30
            assertEquals(LocalDate.of(2025, 6, 30), result)
            assertEquals(DayOfWeek.MONDAY, result.dayOfWeek)
        }

        @Test
        fun `Sunday midnight returns previous Monday`() {
            val sunday = LocalDate.of(2025, 7, 6) // Sunday
            val now = istAt(sunday, 0, 0)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 6, 30), result)
        }

        @Test
        fun `Sunday 6-00 AM returns previous Monday`() {
            val sunday = LocalDate.of(2025, 7, 6) // Sunday
            val now = istAt(sunday, 6, 0)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 6, 30), result)
        }
    }

    @Nested
    @DisplayName("getWeekStartForInstant — Sunday at/after noon (Req 6.3)")
    inner class SundayAtOrAfterNoon {

        @Test
        fun `Sunday exactly 12-00 PM returns next Monday`() {
            val sunday = LocalDate.of(2025, 7, 6) // Sunday
            val now = istAt(sunday, 12, 0)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            // Next Monday is July 7
            assertEquals(LocalDate.of(2025, 7, 7), result)
            assertEquals(DayOfWeek.MONDAY, result.dayOfWeek)
        }

        @Test
        fun `Sunday 12-01 PM returns next Monday`() {
            val sunday = LocalDate.of(2025, 7, 6)
            val now = istAt(sunday, 12, 1)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 7, 7), result)
        }

        @Test
        fun `Sunday 11-59 PM returns next Monday`() {
            val sunday = LocalDate.of(2025, 7, 6)
            val now = istAt(sunday, 23, 59)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 7, 7), result)
        }
    }

    @Nested
    @DisplayName("getWeekStartForInstant — Monday through Saturday (Req 6.4)")
    inner class WeekdaysAndSaturday {

        @Test
        fun `Monday returns same Monday`() {
            val monday = LocalDate.of(2025, 7, 7) // Monday
            val now = istAt(monday, 9, 0)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(monday, result)
            assertEquals(DayOfWeek.MONDAY, result.dayOfWeek)
        }

        @Test
        fun `Wednesday returns Monday of same week`() {
            val wednesday = LocalDate.of(2025, 7, 9) // Wednesday
            val now = istAt(wednesday, 14, 30)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 7, 7), result)
        }

        @Test
        fun `Friday returns Monday of same week`() {
            val friday = LocalDate.of(2025, 7, 11) // Friday
            val now = istAt(friday, 17, 0)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 7, 7), result)
        }

        @Test
        fun `Saturday returns Monday of same week`() {
            val saturday = LocalDate.of(2025, 7, 12) // Saturday
            val now = istAt(saturday, 10, 0)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 7, 7), result)
        }

        @Test
        fun `Tuesday morning returns Monday of same week`() {
            val tuesday = LocalDate.of(2025, 7, 8) // Tuesday
            val now = istAt(tuesday, 8, 0)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 7, 7), result)
        }

        @Test
        fun `Thursday late night returns Monday of same week`() {
            val thursday = LocalDate.of(2025, 7, 10)
            val now = istAt(thursday, 23, 59)
            val result = WeekBoundaryUtil.getWeekStartForInstant(now)

            assertEquals(LocalDate.of(2025, 7, 7), result)
        }
    }

    // ---------- getCurrentWeekEnd (Req 6.5) ----------

    @Nested
    @DisplayName("Week end = Friday (Req 6.5)")
    inner class WeekEnd {

        @Test
        fun `week end is always 4 days after week start`() {
            val monday = LocalDate.of(2025, 7, 7)
            val now = istAt(monday, 10, 0)
            val start = WeekBoundaryUtil.getWeekStartForInstant(now)
            val end = start.plusDays(4)

            assertEquals(DayOfWeek.FRIDAY, end.dayOfWeek)
            assertEquals(LocalDate.of(2025, 7, 11), end)
        }

        @Test
        fun `Sunday before noon — week end is previous Friday`() {
            val sunday = LocalDate.of(2025, 7, 6) // Sunday
            val now = istAt(sunday, 11, 0)
            val start = WeekBoundaryUtil.getWeekStartForInstant(now)
            val end = start.plusDays(4)

            // Start = June 30 (Monday), End = July 4 (Friday)
            assertEquals(LocalDate.of(2025, 7, 4), end)
            assertEquals(DayOfWeek.FRIDAY, end.dayOfWeek)
        }

        @Test
        fun `Sunday after noon — week end is next Friday`() {
            val sunday = LocalDate.of(2025, 7, 6)
            val now = istAt(sunday, 13, 0)
            val start = WeekBoundaryUtil.getWeekStartForInstant(now)
            val end = start.plusDays(4)

            // Start = July 7 (Monday), End = July 11 (Friday)
            assertEquals(LocalDate.of(2025, 7, 11), end)
        }
    }

    // ---------- getWeekStartForDate / getWeekEndForDate ----------

    @Nested
    @DisplayName("getWeekStartForDate and getWeekEndForDate")
    inner class ForDate {

        @Test
        fun `Monday returns itself`() {
            val monday = LocalDate.of(2025, 7, 7)
            assertEquals(monday, WeekBoundaryUtil.getWeekStartForDate(monday))
        }

        @Test
        fun `Wednesday returns Monday of same week`() {
            val wednesday = LocalDate.of(2025, 7, 9)
            assertEquals(LocalDate.of(2025, 7, 7), WeekBoundaryUtil.getWeekStartForDate(wednesday))
        }

        @Test
        fun `Saturday returns Monday of same ISO week`() {
            val saturday = LocalDate.of(2025, 7, 12)
            assertEquals(LocalDate.of(2025, 7, 7), WeekBoundaryUtil.getWeekStartForDate(saturday))
        }

        @Test
        fun `Sunday returns next Monday`() {
            val sunday = LocalDate.of(2025, 7, 6)
            assertEquals(LocalDate.of(2025, 7, 7), WeekBoundaryUtil.getWeekStartForDate(sunday))
        }

        @Test
        fun `getWeekEndForDate returns Friday`() {
            val wednesday = LocalDate.of(2025, 7, 9)
            val end = WeekBoundaryUtil.getWeekEndForDate(wednesday)
            assertEquals(LocalDate.of(2025, 7, 11), end)
            assertEquals(DayOfWeek.FRIDAY, end.dayOfWeek)
        }
    }

    // ---------- isWithinCurrentWeek ----------

    @Nested
    @DisplayName("isWithinCurrentWeek")
    inner class WithinCurrentWeek {

        @Test
        fun `today is within current week`() {
            val today = LocalDate.now(WeekBoundaryUtil.IST)
            // Only weekdays Mon-Fri can be "within" the attendance week
            val start = WeekBoundaryUtil.getCurrentWeekStart()
            val end = WeekBoundaryUtil.getCurrentWeekEnd()
            val expected = !today.isBefore(start) && !today.isAfter(end)
            assertEquals(expected, WeekBoundaryUtil.isWithinCurrentWeek(today))
        }

        @Test
        fun `a date far in the past is not within current week`() {
            assertFalse(WeekBoundaryUtil.isWithinCurrentWeek(LocalDate.of(2020, 1, 1)))
        }

        @Test
        fun `a date far in the future is not within current week`() {
            assertFalse(WeekBoundaryUtil.isWithinCurrentWeek(LocalDate.of(2030, 12, 31)))
        }
    }

    // ---------- getNextResetInstant ----------

    @Nested
    @DisplayName("getNextResetInstant")
    inner class NextResetInstant {

        @Test
        fun `reset instant is always in the future`() {
            val nextReset = WeekBoundaryUtil.getNextResetInstant()
            assertTrue(nextReset.isAfter(Instant.now()))
        }

        @Test
        fun `reset instant falls on a Sunday`() {
            val nextReset = WeekBoundaryUtil.getNextResetInstant()
            val resetDate = nextReset.atZone(WeekBoundaryUtil.IST).toLocalDate()
            assertEquals(DayOfWeek.SUNDAY, resetDate.dayOfWeek)
        }

        @Test
        fun `reset instant is at noon IST`() {
            val nextReset = WeekBoundaryUtil.getNextResetInstant()
            val resetTime = nextReset.atZone(WeekBoundaryUtil.IST).toLocalTime()
            assertEquals(LocalTime.of(WeekBoundaryUtil.RESET_HOUR, 0), resetTime)
        }
    }

    // ---------- getWeekdaysInRange ----------

    @Nested
    @DisplayName("getWeekdaysInRange")
    inner class WeekdaysInRange {

        @Test
        fun `Mon to Fri returns 5 weekdays`() {
            val start = LocalDate.of(2025, 7, 7) // Monday
            val end = LocalDate.of(2025, 7, 11)  // Friday

            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(start, end)

            assertEquals(5, weekdays.size)
            assertEquals(start, weekdays.first())
            assertEquals(end, weekdays.last())
            weekdays.forEach { date ->
                assertTrue(date.dayOfWeek != DayOfWeek.SATURDAY)
                assertTrue(date.dayOfWeek != DayOfWeek.SUNDAY)
            }
        }

        @Test
        fun `range spanning a weekend skips Sat and Sun`() {
            val start = LocalDate.of(2025, 7, 4)  // Friday
            val end = LocalDate.of(2025, 7, 8)    // Tuesday

            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(start, end)

            // Friday, Monday, Tuesday — skips Sat + Sun
            assertEquals(3, weekdays.size)
            assertEquals(LocalDate.of(2025, 7, 4), weekdays[0])
            assertEquals(LocalDate.of(2025, 7, 7), weekdays[1])
            assertEquals(LocalDate.of(2025, 7, 8), weekdays[2])
        }

        @Test
        fun `range containing only a weekend returns empty list`() {
            val saturday = LocalDate.of(2025, 7, 5)
            val sunday = LocalDate.of(2025, 7, 6)

            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(saturday, sunday)

            assertTrue(weekdays.isEmpty())
        }

        @Test
        fun `single weekday returns list of one`() {
            val wednesday = LocalDate.of(2025, 7, 9)

            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(wednesday, wednesday)

            assertEquals(1, weekdays.size)
            assertEquals(wednesday, weekdays[0])
        }

        @Test
        fun `start after end returns empty list`() {
            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(
                LocalDate.of(2025, 7, 11),
                LocalDate.of(2025, 7, 7)
            )
            assertTrue(weekdays.isEmpty())
        }

        @Test
        fun `two-week range returns 10 weekdays`() {
            val start = LocalDate.of(2025, 7, 7)  // Monday
            val end = LocalDate.of(2025, 7, 18)   // Friday of next week

            val weekdays = WeekBoundaryUtil.getWeekdaysInRange(start, end)

            assertEquals(10, weekdays.size)
        }
    }

    // ---------- Consistency: same week → same Monday (Req 6.6) ----------

    @Nested
    @DisplayName("Consistency — same week yields same Monday (Req 6.6)")
    inner class Consistency {

        @Test
        fun `all weekdays in the same week return same Monday`() {
            val monday = LocalDate.of(2025, 7, 7)

            for (offset in 0L..4L) {
                val date = monday.plusDays(offset)
                val now = istAt(date, 10, 0)
                val result = WeekBoundaryUtil.getWeekStartForInstant(now)
                assertEquals(monday, result, "Failed for ${date.dayOfWeek}")
            }
        }

        @Test
        fun `Saturday and the preceding weekdays return same Monday`() {
            val saturday = LocalDate.of(2025, 7, 12)
            val now = istAt(saturday, 15, 0)
            val satResult = WeekBoundaryUtil.getWeekStartForInstant(now)

            val friday = LocalDate.of(2025, 7, 11)
            val friNow = istAt(friday, 15, 0)
            val friResult = WeekBoundaryUtil.getWeekStartForInstant(friNow)

            assertEquals(satResult, friResult)
            assertEquals(LocalDate.of(2025, 7, 7), satResult)
        }

        @Test
        fun `Sunday before noon matches previous week Friday`() {
            val sunday = LocalDate.of(2025, 7, 6)
            val sunNow = istAt(sunday, 11, 0) // before noon
            val sunStart = WeekBoundaryUtil.getWeekStartForInstant(sunNow)

            val friday = LocalDate.of(2025, 7, 4) // Friday of that same week
            val friNow = istAt(friday, 17, 0)
            val friStart = WeekBoundaryUtil.getWeekStartForInstant(friNow)

            assertEquals(friStart, sunStart, "Sunday before noon and its week's Friday should share Monday")
        }

        @Test
        fun `Sunday after noon matches next week Monday`() {
            val sunday = LocalDate.of(2025, 7, 6)
            val sunNow = istAt(sunday, 13, 0)
            val sunStart = WeekBoundaryUtil.getWeekStartForInstant(sunNow)

            val monday = LocalDate.of(2025, 7, 7)
            val monNow = istAt(monday, 9, 0)
            val monStart = WeekBoundaryUtil.getWeekStartForInstant(monNow)

            assertEquals(monStart, sunStart, "Sunday after noon and next Monday should share same week start")
        }
    }

    // ---------- Public constants ----------

    @Nested
    @DisplayName("Public constants")
    inner class PublicConstants {

        @Test
        fun `IST is Asia-Kolkata`() {
            assertEquals("Asia/Kolkata", WeekBoundaryUtil.IST.id)
        }

        @Test
        fun `RESET_HOUR is 12`() {
            assertEquals(12, WeekBoundaryUtil.RESET_HOUR)
        }
    }
}
