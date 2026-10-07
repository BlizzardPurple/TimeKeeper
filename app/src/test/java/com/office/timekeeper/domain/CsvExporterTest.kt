package com.office.timekeeper.domain

import com.office.timekeeper.data.db.TimeSession
import com.office.timekeeper.data.db.WeeklySummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CsvExporterTest {

    private val IST = ZoneId.of("Asia/Kolkata")

    /** Helper: create epoch millis for a given date and time in IST. */
    private fun istEpochMs(date: String, hour: Int, minute: Int): Long {
        val ld = LocalDate.parse(date)
        val zdt = ZonedDateTime.of(ld, LocalTime.of(hour, minute), IST)
        return zdt.toInstant().toEpochMilli()
    }

    /** Helper: create a closed TimeSession. */
    private fun session(
        date: String,
        checkInHour: Int,
        checkInMinute: Int,
        checkOutHour: Int,
        checkOutMinute: Int
    ): TimeSession {
        val checkIn = istEpochMs(date, checkInHour, checkInMinute)
        val checkOut = istEpochMs(date, checkOutHour, checkOutMinute)
        return TimeSession(
            date = date,
            checkInTime = checkIn,
            checkOutTime = checkOut,
            durationMs = checkOut - checkIn
        )
    }

    @Test
    fun `generateCsv produces correct summary header row`() {
        val summary = WeeklySummary(
            weekStartDate = "2025-01-13",
            weekEndDate = "2025-01-17",
            totalMs = 24 * 3_600_000L + 30 * 60_000L, // 24.5 hours
            daysAttended = 3,
            targetMet = true
        )

        val csv = CsvExporter.generateCsv(summary, emptyList())
        val lines = csv.lines()

        assertEquals("Week,Start Date,End Date,Total Hours,Days Attended,Target Met", lines[0])
        assertEquals("2025-W03,2025-01-13,2025-01-17,24.50,3,Yes", lines[1])
    }

    @Test
    fun `generateCsv includes session detail rows with decimal hours`() {
        val summary = WeeklySummary(
            weekStartDate = "2025-01-13",
            weekEndDate = "2025-01-17",
            totalMs = 8 * 3_600_000L + 15 * 60_000L, // 8.25 hours
            daysAttended = 1,
            targetMet = false
        )

        val sessions = listOf(
            session("2025-01-13", 9, 15, 13, 30),  // 4h 15m = 4.25h
            session("2025-01-13", 14, 0, 18, 0)     // 4h 0m = 4.00h
        )

        val csv = CsvExporter.generateCsv(summary, sessions)
        val lines = csv.lines()

        // Line 3 (index 3) is session detail header (after summary header, summary row, blank line)
        assertEquals("Date,Check-In,Check-Out,Duration (hours)", lines[3])

        // Session rows
        assertEquals("2025-01-13,09:15,13:30,4.25", lines[4])
        assertEquals("2025-01-13,14:00,18:00,4.00", lines[5])
    }

    @Test
    fun `generateCsv sorts sessions by date then check-in time`() {
        val summary = WeeklySummary(
            weekStartDate = "2025-01-13",
            weekEndDate = "2025-01-17",
            totalMs = 16 * 3_600_000L,
            daysAttended = 2,
            targetMet = false
        )

        // Provide sessions out of order
        val sessions = listOf(
            session("2025-01-15", 10, 0, 14, 0),
            session("2025-01-13", 14, 0, 18, 0),
            session("2025-01-13", 9, 0, 13, 0)
        )

        val csv = CsvExporter.generateCsv(summary, sessions)
        val lines = csv.lines()

        // After header(0), summary(1), blank(2), detail header(3): sessions at 4, 5, 6
        assertTrue(lines[4].startsWith("2025-01-13,09:00"))
        assertTrue(lines[5].startsWith("2025-01-13,14:00"))
        assertTrue(lines[6].startsWith("2025-01-15,10:00"))
    }

    @Test
    fun `generateCsv formats target met as Yes or No`() {
        val summaryMet = WeeklySummary(
            weekStartDate = "2025-01-13",
            weekEndDate = "2025-01-17",
            totalMs = 24 * 3_600_000L,
            daysAttended = 3,
            targetMet = true
        )
        val csvMet = CsvExporter.generateCsv(summaryMet, emptyList())
        assertTrue(csvMet.lines()[1].endsWith("Yes"))

        val summaryNotMet = summaryMet.copy(targetMet = false)
        val csvNotMet = CsvExporter.generateCsv(summaryNotMet, emptyList())
        assertTrue(csvNotMet.lines()[1].endsWith("No"))
    }

    @Test
    fun `generateCsv handles open session with null checkOutTime`() {
        val summary = WeeklySummary(
            weekStartDate = "2025-01-13",
            weekEndDate = "2025-01-17",
            totalMs = 0,
            daysAttended = 0,
            targetMet = false
        )

        val openSession = TimeSession(
            date = "2025-01-13",
            checkInTime = istEpochMs("2025-01-13", 9, 0),
            checkOutTime = null,
            durationMs = 0
        )

        val csv = CsvExporter.generateCsv(summary, listOf(openSession))
        val lines = csv.lines()

        // Check-out should be empty, duration 0.00
        assertEquals("2025-01-13,09:00,,0.00", lines[4])
    }

    @Test
    fun `computeWeekLabel returns correct ISO week`() {
        assertEquals("2025-W03", CsvExporter.computeWeekLabel("2025-01-13"))
        assertEquals("2025-W01", CsvExporter.computeWeekLabel("2024-12-30"))
        assertEquals("2025-W22", CsvExporter.computeWeekLabel("2025-05-26"))
    }

    @Test
    fun `msToDecimalHours converts correctly`() {
        // 4h 15m = 4.25
        assertEquals("4.25", CsvExporter.msToDecimalHours(4 * 3_600_000L + 15 * 60_000L))
        // 0 ms = 0.00
        assertEquals("0.00", CsvExporter.msToDecimalHours(0))
        // 8h exactly = 8.00
        assertEquals("8.00", CsvExporter.msToDecimalHours(8 * 3_600_000L))
        // 1h 30m = 1.50
        assertEquals("1.50", CsvExporter.msToDecimalHours(1 * 3_600_000L + 30 * 60_000L))
    }

    @Test
    fun `generateCsv with no sessions produces only headers`() {
        val summary = WeeklySummary(
            weekStartDate = "2025-01-13",
            weekEndDate = "2025-01-17",
            totalMs = 0,
            daysAttended = 0,
            targetMet = false
        )

        val csv = CsvExporter.generateCsv(summary, emptyList())
        val lines = csv.trimEnd().lines()

        assertEquals(4, lines.size) // summary header, summary row, blank, detail header
        assertEquals("Week,Start Date,End Date,Total Hours,Days Attended,Target Met", lines[0])
        assertEquals("2025-W03,2025-01-13,2025-01-17,0.00,0,No", lines[1])
        assertEquals("", lines[2])
        assertEquals("Date,Check-In,Check-Out,Duration (hours)", lines[3])
    }
}
