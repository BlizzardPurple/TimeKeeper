package com.office.timekeeper.domain

import com.office.timekeeper.data.db.TimeSession
import com.office.timekeeper.data.db.WeeklySummary
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Property-based tests for CsvExporter.generateCsv().
 *
 * **Validates: Requirements 14.1, 14.3**
 *
 * Property 19: CSV Generation Correctness — CSV contains correct summary header
 * and one row per session with decimal-hour durations.
 */
class CsvExporterPropertyTest {

    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    // ── Custom Arb Generators ──────────────────────────────────────────────

    /**
     * Generates a random Monday between 2024-01-01 and 2025-12-29.
     * We pick a random epoch day in the range and adjust to the nearest Monday.
     */
    private fun arbMonday(): Arb<LocalDate> =
        Arb.long(min = LocalDate.of(2024, 1, 1).toEpochDay(), max = LocalDate.of(2025, 12, 29).toEpochDay())
            .map { epochDay ->
                val date = LocalDate.ofEpochDay(epochDay)
                date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }

    /**
     * Generates a random WeeklySummary with valid fields:
     * - weekStartDate is a Monday
     * - weekEndDate is the following Friday (start + 4)
     * - totalMs in [0, 200h in ms]
     * - daysAttended in [0, 5]
     * - targetMet is random boolean
     */
    private fun arbWeeklySummary(): Arb<WeeklySummary> =
        Arb.bind(
            arbMonday(),
            Arb.long(min = 0L, max = 200L * 3_600_000L),
            Arb.int(min = 0, max = 5),
            Arb.boolean()
        ) { monday: LocalDate, totalMs: Long, daysAttended: Int, targetMet: Boolean ->
            WeeklySummary(
                weekStartDate = monday.toString(),
                weekEndDate = monday.plusDays(4).toString(),
                totalMs = totalMs,
                daysAttended = daysAttended,
                targetMet = targetMet
            )
        }

    /**
     * Generates a random closed TimeSession for the given date string.
     * - checkIn between 09:00 and 16:00 IST
     * - checkOut between checkIn+1min and 22:00 IST
     * - durationMs = checkOut - checkIn
     */
    private fun arbTimeSession(date: String): Arb<TimeSession> {
        val ld = LocalDate.parse(date)
        // Use bind with two int generators; constrain checkOut > checkIn
        return Arb.bind(
            Arb.int(min = 540, max = 960),    // checkIn minute-of-day: 09:00–16:00
            Arb.int(min = 541, max = 1320)     // checkOut minute-of-day: 09:01–22:00
        ) { checkInMinuteOfDay: Int, rawCheckOutMinute: Int ->
            // Ensure checkOut > checkIn
            val checkOutMinuteOfDay = maxOf(rawCheckOutMinute, checkInMinuteOfDay + 1)
                .coerceAtMost(1320)
            val checkInZdt = ZonedDateTime.of(
                ld,
                LocalTime.of(checkInMinuteOfDay / 60, checkInMinuteOfDay % 60),
                IST
            )
            val checkOutZdt = ZonedDateTime.of(
                ld,
                LocalTime.of(checkOutMinuteOfDay / 60, checkOutMinuteOfDay % 60),
                IST
            )
            val checkInMs = checkInZdt.toInstant().toEpochMilli()
            val checkOutMs = checkOutZdt.toInstant().toEpochMilli()
            TimeSession(
                date = date,
                checkInTime = checkInMs,
                checkOutTime = checkOutMs,
                durationMs = checkOutMs - checkInMs
            )
        }
    }

    /**
     * Generates a list of 0–10 closed sessions with dates falling within
     * the given summary's Mon–Fri week range.
     */
    private fun arbSessionList(summary: WeeklySummary): Arb<List<TimeSession>> {
        val weekStart = LocalDate.parse(summary.weekStartDate)
        val weekdays = (0L..4L).map { weekStart.plusDays(it).toString() }

        // Pick a random weekday and generate a session for it
        val singleSession: Arb<TimeSession> =
            Arb.int(min = 0, max = weekdays.size - 1).map { dayIdx ->
                weekdays[dayIdx]
            }.let { arbDate ->
                Arb.bind(
                    arbDate,
                    Arb.int(min = 540, max = 960),
                    Arb.int(min = 541, max = 1320)
                ) { date: String, checkInMin: Int, rawCheckOutMin: Int ->
                    val ld = LocalDate.parse(date)
                    val checkOutMin = maxOf(rawCheckOutMin, checkInMin + 1).coerceAtMost(1320)
                    val checkInZdt = ZonedDateTime.of(
                        ld, LocalTime.of(checkInMin / 60, checkInMin % 60), IST
                    )
                    val checkOutZdt = ZonedDateTime.of(
                        ld, LocalTime.of(checkOutMin / 60, checkOutMin % 60), IST
                    )
                    val checkInMs = checkInZdt.toInstant().toEpochMilli()
                    val checkOutMs = checkOutZdt.toInstant().toEpochMilli()
                    TimeSession(
                        date = date,
                        checkInTime = checkInMs,
                        checkOutTime = checkOutMs,
                        durationMs = checkOutMs - checkInMs
                    )
                }
            }

        return Arb.list(singleSession, range = 0..10)
    }

    /**
     * Generates a (WeeklySummary, List<TimeSession>) pair for property tests.
     */
    private fun arbWeeklySummaryAndSessions(): Arb<Pair<WeeklySummary, List<TimeSession>>> =
        arbWeeklySummary().let { arbSummary ->
            Arb.bind(arbSummary, Arb.long(0L, 1L)) { summary: WeeklySummary, _ ->
                summary
            }
        }.map { summary ->
            val weekStart = LocalDate.parse(summary.weekStartDate)
            val weekdays = (0L..4L).map { weekStart.plusDays(it).toString() }
            // Generate a deterministic-like set of sessions using the summary data
            summary to emptyList<TimeSession>()
        }.let { arbPairNoSessions ->
            // Use bind to generate the pair with actual sessions
            arbWeeklySummary().map { summary ->
                val weekStart = LocalDate.parse(summary.weekStartDate)
                val weekdays = (0L..4L).map { weekStart.plusDays(it).toString() }
                summary
            }.let { _ ->
                // Simpler approach: generate summary, then use it to bound session generation
                Arb.bind(
                    arbWeeklySummary(),
                    Arb.int(min = 0, max = 10)
                ) { summary: WeeklySummary, sessionCount: Int ->
                    val weekStart = LocalDate.parse(summary.weekStartDate)
                    val weekdays = (0L..4L).map { weekStart.plusDays(it).toString() }
                    val random = kotlin.random.Random(summary.totalMs xor sessionCount.toLong())
                    val sessions = (0 until sessionCount).map { i ->
                        val date = weekdays[random.nextInt(weekdays.size)]
                        val ld = LocalDate.parse(date)
                        val checkInMin = random.nextInt(540, 961)
                        val checkOutMin = random.nextInt(checkInMin + 1, 1321)
                        val checkInZdt = ZonedDateTime.of(
                            ld, LocalTime.of(checkInMin / 60, checkInMin % 60), IST
                        )
                        val checkOutZdt = ZonedDateTime.of(
                            ld, LocalTime.of(checkOutMin / 60, checkOutMin % 60), IST
                        )
                        val checkInMs = checkInZdt.toInstant().toEpochMilli()
                        val checkOutMs = checkOutZdt.toInstant().toEpochMilli()
                        TimeSession(
                            date = date,
                            checkInTime = checkInMs,
                            checkOutTime = checkOutMs,
                            durationMs = checkOutMs - checkInMs
                        )
                    }
                    summary to sessions
                }
            }
        }

    // ── Property Tests ────────────────────────────────────────────────────

    /**
     * Property 19.1: Summary header is always present.
     * First line always equals "Week,Start Date,End Date,Total Hours,Days Attended,Target Met".
     *
     * **Validates: Requirements 14.1**
     */
    @Test
    fun `property - summary header is always present`() = runTest {
        checkAll(arbWeeklySummaryAndSessions()) { (summary, sessions) ->
            val csv = CsvExporter.generateCsv(summary, sessions)
            val firstLine = csv.lines().first()
            assertEquals(
                "Week,Start Date,End Date,Total Hours,Days Attended,Target Met",
                firstLine
            )
        }
    }

    /**
     * Property 19.2: Session count matches.
     * Number of session data rows equals number of sessions provided.
     *
     * **Validates: Requirements 14.1**
     */
    @Test
    fun `property - session count matches input`() = runTest {
        checkAll(arbWeeklySummaryAndSessions()) { (summary, sessions) ->
            val csv = CsvExporter.generateCsv(summary, sessions)
            val lines = csv.trimEnd().lines()
            // Lines: summary header(0), summary row(1), blank(2), detail header(3), then session rows
            val sessionLines = lines.drop(4)
            assertEquals(sessions.size, sessionLines.size)
        }
    }

    /**
     * Property 19.3: Duration values are valid decimal hours.
     * Every duration in session rows can be parsed as a non-negative Double.
     *
     * **Validates: Requirements 14.3**
     */
    @Test
    fun `property - duration values are valid non-negative decimal hours`() = runTest {
        checkAll(arbWeeklySummaryAndSessions()) { (summary, sessions) ->
            val csv = CsvExporter.generateCsv(summary, sessions)
            val lines = csv.trimEnd().lines()
            val sessionLines = lines.drop(4)
            for (line in sessionLines) {
                val parts = line.split(",")
                // Format: Date,Check-In,Check-Out,Duration (hours)
                assertTrue(parts.size == 4, "Session line should have 4 columns: $line")
                val duration = parts[3].toDoubleOrNull()
                assertTrue(duration != null, "Duration should be parseable as Double: ${parts[3]}")
                assertTrue(duration!! >= 0.0, "Duration should be non-negative: $duration")
            }
        }
    }

    /**
     * Property 19.4: Sessions are sorted in CSV.
     * Session rows appear sorted by date, then check-in time.
     *
     * **Validates: Requirements 14.1**
     */
    @Test
    fun `property - sessions are sorted by date then check-in time`() = runTest {
        checkAll(arbWeeklySummaryAndSessions()) { (summary, sessions) ->
            val csv = CsvExporter.generateCsv(summary, sessions)
            val lines = csv.trimEnd().lines()
            val sessionLines = lines.drop(4)
            if (sessionLines.size >= 2) {
                for (i in 0 until sessionLines.size - 1) {
                    val current = sessionLines[i].split(",")
                    val next = sessionLines[i + 1].split(",")
                    // current[0]=date, current[1]=check-in time
                    val dateCompare = current[0].compareTo(next[0])
                    if (dateCompare == 0) {
                        // Same date → check-in time should be non-decreasing
                        assertTrue(
                            current[1] <= next[1],
                            "Sessions on same date should be sorted by check-in: ${current[1]} <= ${next[1]}"
                        )
                    } else {
                        assertTrue(
                            dateCompare < 0,
                            "Sessions should be sorted by date: ${current[0]} < ${next[0]}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Property 19.5: Total hours matches summary.
     * The Total Hours in the summary row matches msToDecimalHours(weeklySummary.totalMs).
     *
     * **Validates: Requirements 14.3**
     */
    @Test
    fun `property - total hours in summary matches msToDecimalHours`() = runTest {
        checkAll(arbWeeklySummaryAndSessions()) { (summary, sessions) ->
            val csv = CsvExporter.generateCsv(summary, sessions)
            val summaryRow = csv.lines()[1]
            val parts = summaryRow.split(",")
            // Format: Week,Start Date,End Date,Total Hours,Days Attended,Target Met
            val totalHoursInCsv = parts[3]
            val expectedTotalHours = CsvExporter.msToDecimalHours(summary.totalMs)
            assertEquals(expectedTotalHours, totalHoursInCsv)
        }
    }

    /**
     * Property 19.6: Target met formatting.
     * Summary row ends with "Yes" or "No".
     *
     * **Validates: Requirements 14.1**
     */
    @Test
    fun `property - target met formatted as Yes or No`() = runTest {
        checkAll(arbWeeklySummaryAndSessions()) { (summary, sessions) ->
            val csv = CsvExporter.generateCsv(summary, sessions)
            val summaryRow = csv.lines()[1]
            assertTrue(
                summaryRow.endsWith("Yes") || summaryRow.endsWith("No"),
                "Summary row should end with Yes or No: $summaryRow"
            )
            // Also verify consistency with the input
            if (summary.targetMet) {
                assertTrue(summaryRow.endsWith("Yes"))
            } else {
                assertTrue(summaryRow.endsWith("No"))
            }
        }
    }
}
