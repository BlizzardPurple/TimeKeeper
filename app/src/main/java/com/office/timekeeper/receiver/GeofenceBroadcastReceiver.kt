package com.office.timekeeper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.data.repository.AttendanceRepository
import com.office.timekeeper.domain.NotificationHelper
import com.office.timekeeper.geofence.GeofenceManager
import com.office.timekeeper.widget.TimeKeeperWidgetProvider
import com.office.timekeeper.widget.widgetSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "GeofenceBroadcastRcvr"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            Log.e(TAG, "Geofence error or null event")
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        val transition = geofencingEvent.geofenceTransition
        val geofenceIds = triggeringGeofences.map { it.requestId }

        Log.d(TAG, "Geofence transition=$transition, ids=$geofenceIds")

        val notificationHelper = NotificationHelper(context)
        notificationHelper.createNotificationChannels()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
                val repo = AttendanceRepository(db.timeSessionDao(), db.weeklySummaryDao(), db.archivedTimeSessionDao(), settingsDs)

                when (transition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        if (GeofenceManager.GEOFENCE_ID_INNER in geofenceIds) {
                            // Inner ring (10m) — auto check-in if not already checked in
                            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
                            if (openSession == null) {
                                try {
                                    repo.checkIn()
                                    Log.d(TAG, "Auto check-in triggered (inner geofence dwell)")
                                    // Store auto-checkin flag for dashboard display
                                    settingsDs.setAutoCheckInTriggered(true)
                                    refreshWidgets(context)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Auto check-in failed: ${e.message}")
                                }
                            }
                        }
                        if (GeofenceManager.GEOFENCE_ID_OUTER in geofenceIds) {
                            // Cancel any pending departure notification (user came back within 5 min)
                            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("departure_notification")
                            // Outer ring (100m) — show notification if not checked in
                            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
                            if (openSession == null) {
                                notificationHelper.notify(
                                    NotificationHelper.NOTIFICATION_GEOFENCE_ARRIVAL,
                                    notificationHelper.buildGeofenceArrivalNotification()
                                )
                            }
                        }
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        if (GeofenceManager.GEOFENCE_ID_OUTER in geofenceIds) {
                            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
                            if (openSession != null) {
                                // Schedule delayed checkout notification (5 min dwell)
                                val delayedWork = androidx.work.OneTimeWorkRequestBuilder<com.office.timekeeper.worker.DepartureNotificationWorker>()
                                    .setInitialDelay(5, java.util.concurrent.TimeUnit.MINUTES)
                                    .build()
                                androidx.work.WorkManager.getInstance(context)
                                    .enqueueUniqueWork(
                                        "departure_notification",
                                        androidx.work.ExistingWorkPolicy.REPLACE,
                                        delayedWork
                                    )
                                Log.d(TAG, "Scheduled departure notification in 5 minutes")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling geofence transition", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun refreshWidgets(context: Context) {
        TimeKeeperWidgetProvider.updateAllWidgets(context)
    }
}
