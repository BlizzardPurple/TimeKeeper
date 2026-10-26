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
import com.office.timekeeper.widget.TimeKeeperWidget
import com.office.timekeeper.widget.widgetSettingsDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
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
                            // Leaving 100m zone — show checkout notification if checked in
                            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
                            if (openSession != null) {
                                notificationHelper.notify(
                                    NotificationHelper.NOTIFICATION_GEOFENCE_DEPARTURE,
                                    notificationHelper.buildGeofenceDepartureNotification()
                                )
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

    private suspend fun refreshWidgets(context: Context) {
        try {
            val widget = TimeKeeperWidget()
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                widget.update(context, glanceId)
            }
        } catch (_: Exception) {}
    }
}
