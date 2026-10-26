package com.office.timekeeper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.office.timekeeper.data.repository.SettingsRepository
import com.office.timekeeper.geofence.GeofenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Exposes the three configurable schedule settings as [StateFlow]s and
 * provides action methods that delegate writes (with validation) to
 * [SettingsRepository].
 *
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geofenceManager: GeofenceManager? = null
) : ViewModel() {

    /** Target office days per week (1–5, default 3). */
    val targetDaysPerWeek: StateFlow<Int> =
        settingsRepository.targetDaysPerWeek
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    /** Target hours per office day (1–12, default 8). */
    val targetHoursPerDay: StateFlow<Int> =
        settingsRepository.targetHoursPerDay
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 8)

    /** Geofence radius in meters (50–500, default 100). */
    val geofenceRadiusMeters: StateFlow<Float> =
        settingsRepository.geofenceRadiusMeters
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100f)

    /** WFH/leave absences this week (0–4, default 0). */
    val absencesThisWeek: StateFlow<Int> =
        settingsRepository.absencesThisWeek
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the office location has been calibrated via GPS. */
    val isLocationCalibrated: StateFlow<Boolean> =
        settingsRepository.isLocationCalibrated
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Calibrated latitude (null if not calibrated). */
    val calibratedLatitude: StateFlow<Double?> =
        settingsRepository.calibratedLatitude
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Calibrated longitude (null if not calibrated). */
    val calibratedLongitude: StateFlow<Double?> =
        settingsRepository.calibratedLongitude
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<String?>(null)
    /** Transient error message for UI display; cleared after showing. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Updates the target days per week.
     *
     * Validation (1–5) is enforced by [SettingsRepository]; out-of-range
     * values are caught and surfaced via [error].
     */
    fun onTargetDaysChanged(days: Int) {
        viewModelScope.launch {
            try {
                settingsRepository.setTargetDaysPerWeek(days)
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }

    /**
     * Updates the target hours per day.
     *
     * Validation (1–12) is enforced by [SettingsRepository].
     */
    fun onTargetHoursChanged(hours: Int) {
        viewModelScope.launch {
            try {
                settingsRepository.setTargetHoursPerDay(hours)
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }

    /**
     * Updates the geofence radius.
     *
     * Validation (50–500 m) is enforced by [SettingsRepository].
     */
    fun onGeofenceRadiusChanged(radius: Float) {
        viewModelScope.launch {
            try {
                settingsRepository.setGeofenceRadiusMeters(radius)
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }

    /**
     * Updates the number of absences (WFH/leave days) this week.
     *
     * Validation (0–4) is enforced by [SettingsRepository].
     */
    fun onAbsencesChanged(absences: Int) {
        viewModelScope.launch {
            try {
                settingsRepository.setAbsencesThisWeek(absences)
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }

    /** Call from the UI after displaying the error to clear it. */
    fun clearError() {
        _error.value = null
    }

    /**
     * Saves the calibrated office location and re-registers the geofence
     * at the new coordinates.
     */
    fun onCalibrateLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                settingsRepository.calibrateLocation(latitude, longitude)
                geofenceManager?.reRegisterGeofence()
            } catch (e: Exception) {
                _error.value = "Failed to calibrate: ${e.message}"
            }
        }
    }
}
