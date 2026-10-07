package com.office.timekeeper.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
}
