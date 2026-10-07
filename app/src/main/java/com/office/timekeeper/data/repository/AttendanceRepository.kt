package com.office.timekeeper.data.repository

import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.ArchivedTimeSession
import com.office.timekeeper.data.db.ArchivedTimeSessionDao
import com.office.timekeeper.data.db.TimeSession
import com.office.timekeeper.data.db.TimeSessionDao
import com.office.timekeeper.data.db.WeeklySummary
import com.office.timekeeper.data.db.WeeklySummaryDao
import com.office.timekeeper.domain.CsvExporter
import com.office.timekeeper.domain.WeekBoundaryUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Central coordinator for attendance data operations.
 *
 * This class provides check-in / check-out functionality (Req 1, 2),
 * with daily/weekly query and archival methods to be added in subsequent tasks.
 */
class AttendanceRepository(
    private val timeSessionDao: TimeSessionDao,
    private val weeklySummaryDao: WeeklySummaryDao,
    private val archivedTimeSessionDao: ArchivedTimeSessionDao,
    private val settingsDataStore: SettingsDataStore
) {

    // --- Check-in / Check-out ---

    /**
     * Creates a new check-in session for the current weekday.
     *
     * Validates:
     * - Today is a weekday Mon–Fri (Req 1.2)
     * - No open session exists (Req 1.4)
     *
     * On success:
     * - Inserts a new TimeSession with checkOutTime = null (Req 1.1)
     * - Stores the session ID as activeSessionId (Req 1.2 / AC 1.2)
     * - Multiple sessions per day are allowed via re-sign-in (Req 1.3)
     *
     * @return the created [TimeSession] with its generated ID
     * @throws IllegalArgumentException if today is a weekend or an open session exists
     */
    suspend fun checkIn(): TimeSession {
        val today = LocalDate.now(WeekBoundaryUtil.IST)
        val dayOfWeek = today.dayOfWeek

        require(dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY) {
            "Check-in is only allowed on weekdays (Mon-Fri). Today is $dayOfWeek."
        }

        val openSession = timeSessionDao.getOpenSessionSnapshot()
        require(openSession == null) {
            "Cannot check in while another session is open (session id: ${openSession?.id})."
        }

        val nowMs = System.currentTimeMillis()
        val session = TimeSession(
            date = today.toString(),
            checkInTime = nowMs,
            checkOutTime = null,
            durationMs = 0
        )
        val id = timeSessionDao.insert(session)
        settingsDataStore.setActiveSessionId(id)
        return session.copy(id = id)
    }

    /**
     * Closes an open session, computing its duration.
     *
     * Validates:
     * - Session exists (Req 2.3 / 2.4)
     * - Session is still open (Req 2.4)
     * - Check-out time ≥ check-in time (data integrity)
     *
     * On success:
     * - Sets checkOutTime and computes durationMs (Req 2.1, 2.2)
     * - Clears activeSessionId in DataStore (Req 2.2 / AC 2.2)
     *
     * @param sessionId the ID of the session to close
     * @return the updated [TimeSession]
     * @throws IllegalArgumentException if session not found, already closed, or time invalid
     */
    suspend fun checkOut(sessionId: Long): TimeSession {
        val session = timeSessionDao.getSessionById(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        require(session.checkOutTime == null) {
            "Session $sessionId is already closed."
        }

        val nowMs = System.currentTimeMillis()
        require(nowMs >= session.checkInTime) {
            "Check-out time cannot be before check-in time."
        }

        val duration = nowMs - session.checkInTime
        val updatedSession = session.copy(
            checkOutTime = nowMs,
            durationMs = duration
        )
        timeSessionDao.update(updatedSession)
        settingsDataStore.setActiveSessionId(null)
        return updatedSession
    }

    /**
     * Emits `true` whenever an open session exists, `false` otherwise.
     */
    fun isCurrentlyCheckedIn(): Flow<Boolean> =
        timeSessionDao.getOpenSession().map { it != null }

    /**
     * Emits the currently open [TimeSession], or `null` if none.
     */
    fun getActiveSession(): Flow<TimeSession?> =
        timeSessionDao.getOpenSession()

    // --- Daily Queries (Req 3.1, 3.2, 4.1, 4.2) ---

    /**
     * Emits all sessions for [date], ordered by check-in time ascending.
     *
     * Req 3.1 — display the list of sessions recorded for a given date.
     */
    fun getSessionsForDate(date: LocalDate): Flow<List<TimeSession>> =
        timeSessionDao.getSessionsByDate(date.toString())

    /**
     * Emits the sum of completed (closed) session durations for [date] in milliseconds.
     *
     * Req 3.2 — daily total = sum of all session durationMs for that date.
     */
    fun getTotalDurationForDate(date: LocalDate): Flow<Long> =
        timeSessionDao.getTotalDurationForDate(date.toString())

    /**
     * Emits whether [date] qualifies as an attendance day.
     *
     * A day qualifies when:
     *  - It is a weekday (Mon–Fri)  (Req 4.4)
     *  - Total duration (completed sessions + projected open session for today)
     *    is ≥ [MIN_MS_FOR_ATTENDANCE] (4 hours)  (Req 4.1, 4.2, 4.3)
     *
     * For today's date an open session's elapsed time
     * (`now - checkInTime`) is added to the closed-session total before the
     * comparison (Req 4.2).
     */
    fun doesDayQualifyForAttendance(date: LocalDate): Flow<Boolean> {
        // Weekends never qualify (Req 4.4)
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return kotlinx.coroutines.flow.flowOf(false)
        }

        val today = LocalDate.now(WeekBoundaryUtil.IST)

        return combine(
            timeSessionDao.getTotalDurationForDate(date.toString()),
            timeSessionDao.getOpenSession()
        ) { closedTotal, openSession ->
            val projected = if (date == today && openSession != null) {
                System.currentTimeMillis() - openSession.checkInTime
            } else {
                0L
            }
            (closedTotal + projected) >= MIN_MS_FOR_ATTENDANCE
        }
    }

    // --- Weekly Queries (Req 5.1, 5.2, 5.3, 4.3, 4.4) ---

    /**
     * Emits all sessions in the current attendance week (Mon–Fri),
     * ordered by date then check-in time.
     */
    fun getSessionsForCurrentWeek(): Flow<List<TimeSession>> {
        val start = WeekBoundaryUtil.getCurrentWeekStart()
        val end = WeekBoundaryUtil.getCurrentWeekEnd()
        return timeSessionDao.getSessionsInRange(start.toString(), end.toString())
    }

    /**
     * Emits the total duration for the current week in milliseconds.
     *
     * Includes the elapsed time of any open session projected to "now"
     * (Req 4.2, 5.1).
     */
    fun getCurrentWeekTotalDuration(): Flow<Long> {
        val start = WeekBoundaryUtil.getCurrentWeekStart()
        val end = WeekBoundaryUtil.getCurrentWeekEnd()

        return combine(
            timeSessionDao.getSessionsInRange(start.toString(), end.toString()),
            timeSessionDao.getOpenSession()
        ) { sessions, openSession ->
            val closedTotal = sessions
                .filter { it.checkOutTime != null }
                .sumOf { it.durationMs }

            val openElapsed = if (openSession != null) {
                System.currentTimeMillis() - openSession.checkInTime
            } else {
                0L
            }

            closedTotal + openElapsed
        }
    }

    /**
     * Emits the number of weekdays in the current week that qualify
     * for attendance (total ≥ 4 h, including today's open-session projection).
     *
     * Req 4.3 — weekly qualifying days count.
     */
    fun getCurrentWeekQualifyingDays(): Flow<Int> {
        val start = WeekBoundaryUtil.getCurrentWeekStart()
        val end = WeekBoundaryUtil.getCurrentWeekEnd()
        val today = LocalDate.now(WeekBoundaryUtil.IST)

        return combine(
            timeSessionDao.getSessionsInRange(start.toString(), end.toString()),
            timeSessionDao.getOpenSession()
        ) { sessions, openSession ->
            // Group closed-session totals by date
            val dailyTotals = mutableMapOf<String, Long>()
            for (session in sessions) {
                if (session.checkOutTime != null) {
                    dailyTotals[session.date] =
                        (dailyTotals[session.date] ?: 0L) + session.durationMs
                }
            }

            // Project open session elapsed time onto today
            if (openSession != null) {
                val todayStr = today.toString()
                val elapsed = System.currentTimeMillis() - openSession.checkInTime
                dailyTotals[todayStr] = (dailyTotals[todayStr] ?: 0L) + elapsed
            }

            // Count qualifying weekdays
            dailyTotals.count { (dateStr, totalMs) ->
                val date = LocalDate.parse(dateStr)
                date.dayOfWeek != DayOfWeek.SATURDAY &&
                    date.dayOfWeek != DayOfWeek.SUNDAY &&
                    totalMs >= MIN_MS_FOR_ATTENDANCE
            }
        }
    }

    /**
     * Emits `true` when the weekly attendance target is met.
     *
     * Target is met when BOTH:
     *  - qualifying days ≥ targetDaysPerWeek
     *  - total week duration ≥ targetDays × targetHoursPerDay × 3_600_000
     *
     * Req 4.4, 5.1, 5.2 — weekly target evaluation.
     */
    fun isWeeklyTargetMet(): Flow<Boolean> {
        return combine(
            getCurrentWeekQualifyingDays(),
            getCurrentWeekTotalDuration(),
            settingsDataStore.targetDaysPerWeek,
            settingsDataStore.targetHoursPerDay,
            settingsDataStore.absencesThisWeek
        ) { qualifyingDays, totalMs, targetDays, targetHoursPerDay, absences ->
            val adjustedDays = (targetDays - absences).coerceAtLeast(0)
            val targetWeeklyMs = adjustedDays.toLong() * targetHoursPerDay * MS_PER_HOUR
            qualifyingDays >= adjustedDays && totalMs >= targetWeeklyMs
        }
    }

    // --- Archive (Req 7.2, 7.3, 7.4, 7.5) ---

    /**
     * Archives the current attendance week.
     *
     * Steps:
     * 1. Auto-close any open session (Req 7.2)
     * 2. Load all sessions for the current week
     * 3. Compute totalMs and daysAttended (4h minimum per day) (Req 7.3)
     * 4. Evaluate targetMet against configured targets
     * 5. Create a [WeeklySummary] record
     * 6. Copy sessions to archived_time_sessions (Req 7.4)
     * 7. Delete originals from time_sessions and clear activeSessionId (Req 7.5)
     *
     * @return the created [WeeklySummary] with its generated ID
     */
    suspend fun archiveCurrentWeek(): WeeklySummary {
        val weekStart = WeekBoundaryUtil.getCurrentWeekStart()
        val weekEnd = WeekBoundaryUtil.getCurrentWeekEnd()
        val startStr = weekStart.toString()
        val endStr = weekEnd.toString()

        // Auto-close any open session (Req 7.2)
        val openSession = timeSessionDao.getOpenSessionSnapshot()
        if (openSession != null) {
            checkOut(openSession.id)
        }

        // Load all sessions in the week
        val sessions = timeSessionDao.getSessionsInRangeSnapshot(startStr, endStr)

        // Compute daily totals and qualifying days (Req 7.3)
        val dailyTotals: Map<String, Long> = sessions
            .groupBy { it.date }
            .mapValues { (_, daySessions) -> daySessions.sumOf { it.durationMs } }

        val daysAttended = dailyTotals.count { (_, totalMs) -> totalMs >= MIN_MS_FOR_ATTENDANCE }
        val totalMs = sessions.sumOf { it.durationMs }

        // Check target
        val targetDays = settingsDataStore.getTargetDaysPerWeek()
        val targetHoursPerDay = settingsDataStore.getTargetHoursPerDay()
        val targetWeeklyMs = targetDays.toLong() * targetHoursPerDay * MS_PER_HOUR
        val targetMet = daysAttended >= targetDays && totalMs >= targetWeeklyMs

        // Create summary
        val summary = WeeklySummary(
            weekStartDate = startStr,
            weekEndDate = endStr,
            totalMs = totalMs,
            daysAttended = daysAttended,
            targetMet = targetMet
        )
        val summaryId = weeklySummaryDao.insert(summary)

        // Archive sessions (Req 7.4)
        val archivedSessions = sessions.map { session ->
            ArchivedTimeSession(
                weeklySummaryId = summaryId,
                date = session.date,
                checkInTime = session.checkInTime,
                checkOutTime = session.checkOutTime!!,
                durationMs = session.durationMs
            )
        }
        archivedTimeSessionDao.insertAll(archivedSessions)

        // Clear active sessions (Req 7.5)
        timeSessionDao.deleteSessionsInRange(startStr, endStr)
        settingsDataStore.setActiveSessionId(null)

        return summary.copy(id = summaryId)
    }

    /**
     * Returns all archived [WeeklySummary] records, most recent first.
     */
    fun getArchivedWeeks(): Flow<List<WeeklySummary>> =
        weeklySummaryDao.getAllSummaries()

    /**
     * Returns all [ArchivedTimeSession] records for a given weekly summary.
     *
     * @param weeklySummaryId the ID of the [WeeklySummary] to load sessions for
     * @return list of archived sessions ordered by date and check-in time
     */
    suspend fun getArchivedWeekSessions(weeklySummaryId: Long): List<ArchivedTimeSession> =
        archivedTimeSessionDao.getSessionsByWeeklySummaryIdSnapshot(weeklySummaryId)

    // --- Export (Req 14.1) ---

    /**
     * Generates a CSV string for the given archived week.
     *
     * Loads the [WeeklySummary] and its [ArchivedTimeSession] records, converts
     * them to [TimeSession] format, and delegates to [CsvExporter.generateCsv].
     *
     * @param weeklySummaryId the ID of the [WeeklySummary] to export
     * @return the CSV-formatted string
     * @throws IllegalArgumentException if the weekly summary is not found
     */
    suspend fun exportWeekAsCsv(weeklySummaryId: Long): String {
        val summary = weeklySummaryDao.getSummaryById(weeklySummaryId)
            ?: throw IllegalArgumentException("Weekly summary not found: $weeklySummaryId")

        val archivedSessions = archivedTimeSessionDao.getSessionsByWeeklySummaryIdSnapshot(weeklySummaryId)

        // Convert ArchivedTimeSessions to TimeSessions for the CsvExporter
        val timeSessions = archivedSessions.map { archived ->
            TimeSession(
                id = archived.id,
                date = archived.date,
                checkInTime = archived.checkInTime,
                checkOutTime = archived.checkOutTime,
                durationMs = archived.durationMs
            )
        }

        return CsvExporter.generateCsv(summary, timeSessions)
    }

    companion object {
        /** 4 hours in milliseconds — minimum for a day to qualify as attended. */
        const val MIN_MS_FOR_ATTENDANCE: Long = 4 * 3_600_000L  // 14,400,000

        /** 12 hours in milliseconds — daily cap for attendance tracking. */
        const val MAX_MS_PER_DAY: Long = 12 * 3_600_000L

        /** Milliseconds per hour, used for target calculations. */
        private const val MS_PER_HOUR: Long = 3_600_000L
    }
}
