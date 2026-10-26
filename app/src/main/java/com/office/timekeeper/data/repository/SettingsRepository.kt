package com.office.timekeeper.data.repository

import com.office.timekeeper.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around [SettingsDataStore] that adds input validation
 * for configurable schedule settings.
 *
 * Read properties are direct delegations to the underlying DataStore flows.
 * Write methods enforce the allowed ranges defined in Requirements 12.2–12.4.
 */
class SettingsRepository(
    private val settingsDataStore: SettingsDataStore
) {
    // --- Read (Flows) ---

    val targetDaysPerWeek: Flow<Int> = settingsDataStore.targetDaysPerWeek

    val targetHoursPerDay: Flow<Int> = settingsDataStore.targetHoursPerDay

    val geofenceRadiusMeters: Flow<Float> = settingsDataStore.geofenceRadiusMeters

    val targetWeeklyHours: Flow<Int> = settingsDataStore.targetWeeklyHours

    val absencesThisWeek: Flow<Int> = settingsDataStore.absencesThisWeek

    val isLocationCalibrated: Flow<Boolean> = settingsDataStore.isLocationCalibrated
    val autoCheckInTriggered: Flow<Boolean> = settingsDataStore.autoCheckInTriggered
    val calibratedLatitude: Flow<Double?> = settingsDataStore.calibratedLatitude
    val calibratedLongitude: Flow<Double?> = settingsDataStore.calibratedLongitude

    // --- Write (with validation) ---

    /**
     * Updates the target days per week.
     *
     * @param days must be in 1..5
     * @throws IllegalArgumentException if [days] is outside the allowed range
     */
    suspend fun setTargetDaysPerWeek(days: Int) {
        require(days in 1..5) {
            "Target days per week must be between 1 and 5, got $days."
        }
        settingsDataStore.setTargetDaysPerWeek(days)
    }

    /**
     * Updates the target hours per day.
     *
     * @param hours must be in 1..12
     * @throws IllegalArgumentException if [hours] is outside the allowed range
     */
    suspend fun setTargetHoursPerDay(hours: Int) {
        require(hours in 1..12) {
            "Target hours per day must be between 1 and 12, got $hours."
        }
        settingsDataStore.setTargetHoursPerDay(hours)
    }

    /**
     * Updates the geofence radius.
     *
     * @param radius must be in 50.0..500.0 meters
     * @throws IllegalArgumentException if [radius] is outside the allowed range
     */
    suspend fun setGeofenceRadiusMeters(radius: Float) {
        require(radius in 50f..500f) {
            "Geofence radius must be between 50 and 500 meters, got $radius."
        }
        settingsDataStore.setGeofenceRadiusMeters(radius)
    }

    /**
     * Updates the number of absences (WFH/leave days) this week.
     *
     * @param absences must be in 0..4
     * @throws IllegalArgumentException if [absences] is outside the allowed range
     */
    suspend fun setAbsencesThisWeek(absences: Int) {
        require(absences in 0..4) {
            "Absences must be between 0 and 4, got $absences."
        }
        settingsDataStore.setAbsencesThisWeek(absences)
    }

    suspend fun calibrateLocation(latitude: Double, longitude: Double) {
        settingsDataStore.setCalibratedLocation(latitude, longitude)
    }

    suspend fun clearAutoCheckInTriggered() {
        settingsDataStore.setAutoCheckInTriggered(false)
    }
}
