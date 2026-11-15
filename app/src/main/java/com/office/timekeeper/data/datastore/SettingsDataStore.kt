package com.office.timekeeper.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsDataStore(private val dataStore: DataStore<Preferences>) {

    companion object {
        val TARGET_DAYS_PER_WEEK = intPreferencesKey("target_days_per_week")
        val TARGET_HOURS_PER_DAY = intPreferencesKey("target_hours_per_day")
        val GEOFENCE_RADIUS_METERS = floatPreferencesKey("geofence_radius_meters")
        val ACTIVE_SESSION_ID = longPreferencesKey("active_session_id")

        const val DEFAULT_TARGET_DAYS = 3
        const val DEFAULT_TARGET_HOURS = 8
        const val DEFAULT_GEOFENCE_RADIUS = 100f

        val ABSENCES_THIS_WEEK = intPreferencesKey("absences_this_week")
        const val DEFAULT_ABSENCES = 0

        // Calibrated office location
        val CALIBRATED_LATITUDE = stringPreferencesKey("calibrated_latitude")   // stored as String to avoid Float precision loss
        val CALIBRATED_LONGITUDE = stringPreferencesKey("calibrated_longitude")
        val IS_LOCATION_CALIBRATED = booleanPreferencesKey("is_location_calibrated")
        val AUTO_CHECK_IN_ENABLED = booleanPreferencesKey("auto_check_in_enabled")  // default true
        val AUTO_CHECK_IN_TRIGGERED = booleanPreferencesKey("auto_check_in_triggered")

        val WEATHER_SUGGESTIONS_ENABLED = booleanPreferencesKey("weather_suggestions_enabled") // default false (opt-in)
        val COMMUTE_BUFFER_MINUTES = intPreferencesKey("commute_buffer_minutes") // default 15
        val LAST_WEEKLY_RESET_MS = longPreferencesKey("last_weekly_reset_ms") // epoch ms of last successful reset
    }

    // --- Flows with defaults ---

    val targetDaysPerWeek: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TARGET_DAYS_PER_WEEK] ?: DEFAULT_TARGET_DAYS
    }

    val targetHoursPerDay: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TARGET_HOURS_PER_DAY] ?: DEFAULT_TARGET_HOURS
    }

    val geofenceRadiusMeters: Flow<Float> = dataStore.data.map { prefs ->
        prefs[GEOFENCE_RADIUS_METERS] ?: DEFAULT_GEOFENCE_RADIUS
    }

    val activeSessionId: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[ACTIVE_SESSION_ID]
    }

    val absencesThisWeek: Flow<Int> = dataStore.data.map { prefs ->
        prefs[ABSENCES_THIS_WEEK] ?: DEFAULT_ABSENCES
    }

    val calibratedLatitude: Flow<Double?> = dataStore.data.map { it[CALIBRATED_LATITUDE]?.toDoubleOrNull() }
    val calibratedLongitude: Flow<Double?> = dataStore.data.map { it[CALIBRATED_LONGITUDE]?.toDoubleOrNull() }
    val isLocationCalibrated: Flow<Boolean> = dataStore.data.map { it[IS_LOCATION_CALIBRATED] ?: false }
    val autoCheckInEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_CHECK_IN_ENABLED] ?: true }
    val autoCheckInTriggered: Flow<Boolean> = dataStore.data.map { it[AUTO_CHECK_IN_TRIGGERED] ?: false }

    val weatherSuggestionsEnabled: Flow<Boolean> = dataStore.data.map { it[WEATHER_SUGGESTIONS_ENABLED] ?: false }
    val commuteBufferMinutes: Flow<Int> = dataStore.data.map { it[COMMUTE_BUFFER_MINUTES] ?: 15 }
    val lastWeeklyResetMs: Flow<Long> = dataStore.data.map { it[LAST_WEEKLY_RESET_MS] ?: 0L }

    // --- Derived ---

    val targetWeeklyHours: Flow<Int> = combine(targetDaysPerWeek, targetHoursPerDay) { days, hours ->
        days * hours
    }

    val minHoursPerDayForAttendance: Int = 4

    // --- Setters ---

    suspend fun setTargetDaysPerWeek(days: Int) {
        dataStore.edit { prefs ->
            prefs[TARGET_DAYS_PER_WEEK] = days
        }
    }

    suspend fun setTargetHoursPerDay(hours: Int) {
        dataStore.edit { prefs ->
            prefs[TARGET_HOURS_PER_DAY] = hours
        }
    }

    suspend fun setGeofenceRadiusMeters(radius: Float) {
        dataStore.edit { prefs ->
            prefs[GEOFENCE_RADIUS_METERS] = radius
        }
    }

    suspend fun setActiveSessionId(sessionId: Long?) {
        dataStore.edit { prefs ->
            if (sessionId != null) {
                prefs[ACTIVE_SESSION_ID] = sessionId
            } else {
                prefs.remove(ACTIVE_SESSION_ID)
            }
        }
    }

    suspend fun setAbsencesThisWeek(absences: Int) {
        dataStore.edit { prefs ->
            prefs[ABSENCES_THIS_WEEK] = absences
        }
    }

    suspend fun setCalibratedLocation(latitude: Double, longitude: Double) {
        dataStore.edit { prefs ->
            prefs[CALIBRATED_LATITUDE] = latitude.toString()
            prefs[CALIBRATED_LONGITUDE] = longitude.toString()
            prefs[IS_LOCATION_CALIBRATED] = true
        }
    }

    suspend fun clearCalibratedLocation() {
        dataStore.edit { prefs ->
            prefs.remove(CALIBRATED_LATITUDE)
            prefs.remove(CALIBRATED_LONGITUDE)
            prefs[IS_LOCATION_CALIBRATED] = false
        }
    }

    suspend fun setAutoCheckInEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AUTO_CHECK_IN_ENABLED] = enabled }
    }

    suspend fun setAutoCheckInTriggered(triggered: Boolean) {
        dataStore.edit { prefs -> prefs[AUTO_CHECK_IN_TRIGGERED] = triggered }
    }

    suspend fun setWeatherSuggestionsEnabled(enabled: Boolean) {
        dataStore.edit { it[WEATHER_SUGGESTIONS_ENABLED] = enabled }
    }

    suspend fun setCommuteBufferMinutes(minutes: Int) {
        dataStore.edit { it[COMMUTE_BUFFER_MINUTES] = minutes }
    }

    suspend fun setLastWeeklyResetMs(ms: Long) {
        dataStore.edit { it[LAST_WEEKLY_RESET_MS] = ms }
    }

    // --- Snapshot getters (suspend, for use in workers) ---

    suspend fun getTargetDaysPerWeek(): Int {
        return dataStore.data.first()[TARGET_DAYS_PER_WEEK] ?: DEFAULT_TARGET_DAYS
    }

    suspend fun getTargetHoursPerDay(): Int {
        return dataStore.data.first()[TARGET_HOURS_PER_DAY] ?: DEFAULT_TARGET_HOURS
    }

    suspend fun getAbsencesThisWeek(): Int {
        return dataStore.data.first()[ABSENCES_THIS_WEEK] ?: DEFAULT_ABSENCES
    }

    suspend fun getCalibratedLatitude(): Double? = dataStore.data.first()[CALIBRATED_LATITUDE]?.toDoubleOrNull()
    suspend fun getCalibratedLongitude(): Double? = dataStore.data.first()[CALIBRATED_LONGITUDE]?.toDoubleOrNull()
    suspend fun isLocationCalibratedSnapshot(): Boolean = dataStore.data.first()[IS_LOCATION_CALIBRATED] ?: false

    suspend fun getLastWeeklyResetMs(): Long = dataStore.data.first()[LAST_WEEKLY_RESET_MS] ?: 0L
}
