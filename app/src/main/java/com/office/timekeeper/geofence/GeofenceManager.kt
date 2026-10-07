package com.office.timekeeper.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.receiver.GeofenceBroadcastReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Manages geofence registration with Google Play Services Location.
 *
 * Registers a geofence at the office location (Concentrix Building 6,
 * Gurugram) monitoring ENTER and EXIT transitions. Handles permission
 * checks, radius configuration, and re-registration on radius change
 * or device reboot.
 *
 * Requirements: 8.1, 9.1, 12.4
 */
class GeofenceManager(
    private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "GeofenceManager"
        const val GEOFENCE_ID = "office_concentrix_b6"
        const val DEFAULT_LATITUDE = 28.4966
        const val DEFAULT_LONGITUDE = 77.0965
        const val DEFAULT_RADIUS_METERS = 100f
        private const val PENDING_INTENT_REQUEST_CODE = 1001
    }

    /**
     * Registers the office geofence with the configured radius.
     *
     * Skips registration if ACCESS_FINE_LOCATION is not granted.
     * Uses ENTER + EXIT transitions so GeofenceBroadcastReceiver
     * receives both arrival and departure events.
     */
    @Suppress("MissingPermission")
    suspend fun registerGeofence() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted — skipping geofence registration.")
            return
        }

        val radiusMeters = settingsDataStore.geofenceRadiusMeters.first()

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(request, getGeofencePendingIntent()).await()
            Log.d(TAG, "Geofence registered: radius=${radiusMeters}m")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register geofence", e)
        }
    }

    /**
     * Removes the currently registered geofence.
     */
    suspend fun unregisterGeofence() {
        try {
            geofencingClient.removeGeofences(getGeofencePendingIntent()).await()
            Log.d(TAG, "Geofence unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister geofence", e)
        }
    }

    /**
     * Re-registers the geofence with an updated radius. Removes the
     * old geofence first, then registers a fresh one.
     */
    suspend fun updateGeofenceRadius(radiusMeters: Float) {
        settingsDataStore.setGeofenceRadiusMeters(radiusMeters)
        unregisterGeofence()
        registerGeofence()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
