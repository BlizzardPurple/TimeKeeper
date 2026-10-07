package com.office.timekeeper.di

import android.content.Context
import com.google.android.gms.location.LocationServices
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.data.repository.AttendanceRepository
import com.office.timekeeper.data.repository.SettingsRepository
import com.office.timekeeper.domain.NotificationHelper
import com.office.timekeeper.geofence.GeofenceManager
import com.office.timekeeper.widget.widgetSettingsDataStore

/**
 * Manual dependency injection container for TimeKeeper.
 *
 * Provides lazy-initialized singletons for the database, DAOs,
 * repositories, and system-service wrappers. Pass the application
 * [Context] at creation time (typically in [TimeKeeperApplication]).
 */
class AppModule(private val context: Context) {

    val database: AppDatabase by lazy { AppDatabase.getInstance(context) }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(context.widgetSettingsDataStore)
    }

    val timeSessionDao get() = database.timeSessionDao()
    val weeklySummaryDao get() = database.weeklySummaryDao()
    val archivedTimeSessionDao get() = database.archivedTimeSessionDao()

    val attendanceRepository: AttendanceRepository by lazy {
        AttendanceRepository(
            timeSessionDao,
            weeklySummaryDao,
            archivedTimeSessionDao,
            settingsDataStore
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(settingsDataStore)
    }

    val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(context)
    }

    val geofenceManager: GeofenceManager by lazy {
        GeofenceManager(
            context,
            LocationServices.getGeofencingClient(context),
            settingsDataStore
        )
    }
}
