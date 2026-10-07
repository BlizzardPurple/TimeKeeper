package com.office.timekeeper.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.glance.state.GlanceStateDefinition
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.domain.WeekBoundaryUtil
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [GlanceStateDefinition] that builds a [WidgetState] snapshot from
 * Room + DataStore each time the widget refreshes.
 *
 * Runs on a background thread provided by Glance.
 */
object TimeKeeperWidgetStateDefinition : GlanceStateDefinition<WidgetState> {

    /**
     * Required by the interface. Returns a placeholder location —
     * actual state comes from [getDataStore].
     */
    override fun getLocation(context: Context, fileKey: String): File {
        return File(context.filesDir, "widget_state")
    }

    /**
     * Returns a lightweight [DataStore] adapter that reads attendance
     * data from Room and DataStore on every collection.
     */
    override suspend fun getDataStore(
        context: Context,
        fileKey: String
    ): DataStore<WidgetState> = SnapshotWidgetDataStore(context)
}

/**
 * Minimal [DataStore] adapter that produces a single [WidgetState] snapshot
 * on every read. Writes are no-ops — the widget is read-only.
 */
private class SnapshotWidgetDataStore(
    private val context: Context
) : DataStore<WidgetState> {

    override val data: kotlinx.coroutines.flow.Flow<WidgetState>
        get() = kotlinx.coroutines.flow.flow { emit(readState()) }

    override suspend fun updateData(
        transform: suspend (WidgetState) -> WidgetState
    ): WidgetState = readState()

    private suspend fun readState(): WidgetState {
        return try {
            val db = AppDatabase.getInstance(context)
            val timeSessionDao = db.timeSessionDao()
            val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)

            // Open session check
            val openSession = timeSessionDao.getOpenSessionSnapshot()
            val isCheckedIn = openSession != null
            val nowMs = System.currentTimeMillis()
            val openElapsedMs = if (openSession != null) nowMs - openSession.checkInTime else 0L

            val today = LocalDate.now(WeekBoundaryUtil.IST)
            val weekStart = WeekBoundaryUtil.getCurrentWeekStart()
            val weekEnd = WeekBoundaryUtil.getCurrentWeekEnd()

            // All sessions this week
            val weekSessions = timeSessionDao.getSessionsInRangeSnapshot(
                weekStart.toString(),
                weekEnd.toString()
            )

            // --- Today's duration ---
            val todayStr = today.toString()
            val todayClosedMs = weekSessions
                .filter { it.date == todayStr && it.checkOutTime != null }
                .sumOf { it.durationMs }

            val todayOpenMs = if (openSession != null && openSession.date == todayStr) {
                openElapsedMs
            } else {
                0L
            }
            val todayTotalMs = todayClosedMs + todayOpenMs

            // --- Week duration ---
            val weekClosedMs = weekSessions
                .filter { it.checkOutTime != null }
                .sumOf { it.durationMs }

            val weekTotalMs = weekClosedMs + openElapsedMs

            // --- Qualifying days ---
            val dailyTotals = mutableMapOf<String, Long>()
            for (session in weekSessions) {
                if (session.checkOutTime != null) {
                    dailyTotals[session.date] =
                        (dailyTotals[session.date] ?: 0L) + session.durationMs
                }
            }
            // Project open session elapsed time onto today
            if (openSession != null) {
                dailyTotals[todayStr] = (dailyTotals[todayStr] ?: 0L) + openElapsedMs
            }

            val daysCompleted = dailyTotals.count { (dateStr, totalMs) ->
                val date = LocalDate.parse(dateStr)
                date.dayOfWeek != DayOfWeek.SATURDAY &&
                    date.dayOfWeek != DayOfWeek.SUNDAY &&
                    totalMs >= WidgetState.MIN_MS_FOR_ATTENDANCE
            }

            // --- Target evaluation ---
            val targetDays = settingsDs.getTargetDaysPerWeek()
            val targetHoursPerDay = settingsDs.getTargetHoursPerDay()
            val isTargetMet = WidgetState.isTargetMet(
                qualifyingDays = daysCompleted,
                totalWeekMs = weekTotalMs,
                targetDays = targetDays,
                targetHoursPerDay = targetHoursPerDay
            )

            WidgetState(
                isCheckedIn = isCheckedIn,
                todayDurationFormatted = WidgetState.formatDuration(todayTotalMs),
                weekDurationFormatted = WidgetState.formatDuration(weekTotalMs),
                daysCompleted = daysCompleted,
                targetDays = targetDays,
                isWeeklyTargetMet = isTargetMet
            )
        } catch (_: Exception) {
            // Return safe defaults if anything goes wrong during read
            WidgetState()
        }
    }
}
