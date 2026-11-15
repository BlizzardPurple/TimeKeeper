package com.office.timekeeper.ui.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.office.timekeeper.data.db.TimeSession
import com.office.timekeeper.data.repository.AttendanceRepository
import com.office.timekeeper.data.repository.SettingsRepository
import com.office.timekeeper.domain.WeatherService
import com.office.timekeeper.domain.WeekBoundaryUtil
import com.office.timekeeper.geofence.GeofenceManager
import com.office.timekeeper.widget.TimeKeeperWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ViewModel for the Dashboard screen.
 *
 * Exposes live attendance state (check-in status, session info, daily/weekly
 * totals, qualifying days, and target status) as StateFlows, and provides
 * check-in / check-out actions.
 *
 * Req 1.1, 1.5, 2.1, 2.5, 3.2, 5.3
 */
class DashboardViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val settingsRepository: SettingsRepository,
    private val application: Application,
    private val geofenceManager: GeofenceManager? = null
) : ViewModel() {

    /** Whether the user currently has an open session. */
    val isCheckedIn: StateFlow<Boolean> =
        attendanceRepository.isCurrentlyCheckedIn()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The currently open [TimeSession], or null if checked out. */
    val activeSession: StateFlow<TimeSession?> =
        attendanceRepository.getActiveSession()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Today's total completed session duration in milliseconds. */
    val todayTotalDuration: StateFlow<Long> =
        attendanceRepository.getTotalDurationForDate(LocalDate.now(WeekBoundaryUtil.IST))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Current week's total duration in milliseconds (includes open session projection). */
    val weekTotalDuration: StateFlow<Long> =
        attendanceRepository.getCurrentWeekTotalDuration()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Number of qualifying days (≥ 4 h) in the current week. */
    val qualifyingDays: StateFlow<Int> =
        attendanceRepository.getCurrentWeekQualifyingDays()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Configured target days per week from settings. */
    val targetDays: StateFlow<Int> =
        settingsRepository.targetDaysPerWeek
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    /** Number of WFH/leave absence days this week. */
    val absences: StateFlow<Int> =
        settingsRepository.absencesThisWeek
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the weekly attendance target has been met. */
    val isWeeklyTargetMet: StateFlow<Boolean> =
        attendanceRepository.isWeeklyTargetMet()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether an auto check-in was triggered (for dashboard note). */
    val autoCheckInTriggered: StateFlow<Boolean> =
        settingsRepository.autoCheckInTriggered
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _error = MutableStateFlow<String?>(null)
    /** Transient error message for UI display; cleared after showing. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _weatherSuggestion = MutableStateFlow<WeatherService.WeatherSuggestion?>(null)
    val weatherSuggestion: StateFlow<WeatherService.WeatherSuggestion?> = _weatherSuggestion.asStateFlow()

    val weatherEnabled: StateFlow<Boolean> =
        settingsRepository.weatherSuggestionsEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Refresh weather every 30 minutes while the ViewModel is active
        viewModelScope.launch {
            while (true) {
                refreshWeather()
                delay(30 * 60 * 1000L) // 30 min
            }
        }
    }

    /**
     * Initiates a check-in. On success the reactive StateFlows update
     * automatically through Room/DataStore. On failure the error is
     * surfaced via [error].
     */
    fun onCheckIn() {
        viewModelScope.launch {
            try {
                attendanceRepository.checkIn()
                refreshWidgets()
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }

    /**
     * Closes the current open session. Uses [activeSession] to resolve the
     * session ID. If no active session exists an error is emitted.
     */
    fun onCheckOut() {
        viewModelScope.launch {
            try {
                val sessionId = activeSession.value?.id
                    ?: throw IllegalArgumentException("No active session to check out.")
                attendanceRepository.checkOut(sessionId)
                refreshWidgets()
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }

    /** Call from the UI after displaying the error to clear it. */
    fun clearError() {
        _error.value = null
    }

    fun clearAutoCheckInNote() {
        viewModelScope.launch {
            settingsRepository.clearAutoCheckInTriggered()
        }
    }

    /**
     * Pushes an immediate update to every placed TimeKeeperWidget instance
     * so the home-screen widget reflects the latest attendance state.
     */
    private fun refreshWidgets() {
        TimeKeeperWidgetProvider.updateAllWidgets(application)
    }

    fun refreshWeather() {
        viewModelScope.launch {
            try {
                val enabled = settingsRepository.weatherSuggestionsEnabled.first()
                if (!enabled) {
                    _weatherSuggestion.value = null
                    return@launch
                }
                val lat = settingsRepository.calibratedLatitude.first()
                val lng = settingsRepository.calibratedLongitude.first()
                if (lat == null || lng == null) return@launch

                val forecasts = WeatherService.fetchForecast(lat, lng) ?: return@launch

                // Calculate remaining minutes
                val weekTotalMs = weekTotalDuration.value
                val targetDaysVal = targetDays.value
                val absencesVal = absences.value
                val adjustedDays = (targetDaysVal - absencesVal).coerceAtLeast(0)
                val targetWeeklyMs = adjustedDays * 8L * 3_600_000L
                val remainingMs = (targetWeeklyMs - weekTotalMs).coerceAtLeast(0)
                val remainingMinutes = remainingMs / 60_000

                val commuteBuffer = settingsRepository.commuteBufferMinutes.first()
                _weatherSuggestion.value = WeatherService.generateSuggestion(forecasts, remainingMinutes, commuteBuffer)
            } catch (_: Exception) {
                _weatherSuggestion.value = null
            }
        }
    }
}
