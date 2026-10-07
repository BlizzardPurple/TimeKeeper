package com.office.timekeeper.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.office.timekeeper.data.db.ArchivedTimeSession
import com.office.timekeeper.data.db.WeeklySummary
import com.office.timekeeper.data.repository.AttendanceRepository
import com.office.timekeeper.domain.CsvExporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the History screen.
 *
 * Loads archived [WeeklySummary] records and provides methods to
 * fetch daily session breakdowns and trigger CSV export.
 *
 * Requirements: 13.1, 13.2, 13.3, 14.1, 14.2
 */
class HistoryViewModel(
    private val attendanceRepository: AttendanceRepository
) : ViewModel() {

    /**
     * All archived weekly summaries, most recent first.
     *
     * Req 13.1 — ordered by weekStartDate descending.
     */
    val archivedWeeks: StateFlow<List<WeeklySummary>> =
        attendanceRepository.getArchivedWeeks()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * Loads the archived sessions for a given weekly summary.
     *
     * Req 13.2 — individual session records grouped by date.
     *
     * @param weeklySummaryId the ID of the [WeeklySummary] to load sessions for
     * @return list of archived sessions ordered by date and check-in time
     */
    suspend fun getSessionsForWeek(weeklySummaryId: Long): List<ArchivedTimeSession> =
        attendanceRepository.getArchivedWeekSessions(weeklySummaryId)

    /**
     * Generates a CSV for the given week and launches the Android share intent.
     *
     * Req 14.1, 14.2 — CSV generation and share via FileProvider.
     *
     * @param context the Android context used to write the cache file and launch the share intent
     * @param weeklySummaryId the ID of the [WeeklySummary] to export
     */
    fun onExportCsv(context: Context, weeklySummaryId: Long) {
        viewModelScope.launch {
            val csv = attendanceRepository.exportWeekAsCsv(weeklySummaryId)

            // Find the summary to compute the week label for the file name
            val summary = archivedWeeks.value.firstOrNull { it.id == weeklySummaryId }
            val weekLabel = if (summary != null) {
                CsvExporter.computeWeekLabel(summary.weekStartDate)
            } else {
                "unknown"
            }

            CsvExporter.shareCsv(context, csv, weekLabel)
        }
    }
}
