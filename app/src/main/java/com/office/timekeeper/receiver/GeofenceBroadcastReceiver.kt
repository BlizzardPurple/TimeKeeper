package com.office.timekeeper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.office.timekeeper.domain.NotificationHelper

/**
 * Receives geofence transition events from Google Play Services and
 * posts sign-in/sign-out prompt notifications.
 *
 * Requirements: 8.2, 8.3
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastRcvr"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.w(TAG, "GeofencingEvent is null")
            return
        }
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofence error: ${geofencingEvent.errorCode}")
            return
        }

        val notificationHelper = NotificationHelper(context)
        notificationHelper.createNotificationChannels()

        when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "Geofence ENTER — posting arrival notification")
                notificationHelper.notify(
                    NotificationHelper.NOTIFICATION_GEOFENCE_ARRIVAL,
                    notificationHelper.buildGeofenceArrivalNotification()
                )
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "Geofence EXIT — posting departure notification")
                notificationHelper.notify(
                    NotificationHelper.NOTIFICATION_GEOFENCE_DEPARTURE,
                    notificationHelper.buildGeofenceDepartureNotification()
                )
            }
            else -> {
                Log.w(TAG, "Unknown transition: ${geofencingEvent.geofenceTransition}")
            }
        }
    }
}
