package com.office.timekeeper.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.receiver.GeofenceBroadcastReceiver
import kotlinx.coroutines.tasks.await

class GeofenceManager(
    private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "GeofenceManager"
        const val GEOFENCE_ID_INNER = "office_inner"
        const val GEOFENCE_ID_OUTER = "office_outer"
        const val DEFAULT_LATITUDE = 28.4966
        const val DEFAULT_LONGITUDE = 77.0965
        const val INNER_RADIUS_METERS = 10f
        const val OUTER_RADIUS_METERS = 100f
        private const val PENDING_INTENT_REQUEST_CODE = 1001
    }

    suspend fun getOfficeLocation(): Pair<Double, Double> {
        val lat = settingsDataStore.getCalibratedLatitude()
        val lng = settingsDataStore.getCalibratedLongitude()
        return if (lat != null && lng != null) Pair(lat, lng) else Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
    }

    @Suppress("MissingPermission")
    suspend fun registerGeofence() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted — skipping geofence registration.")
            return
        }

        val (lat, lng) = getOfficeLocation()

        val innerGeofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID_INNER)
            .setCircularRegion(lat, lng, INNER_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setLoiteringDelay(10 * 60 * 1000) // 10 minutes dwell
            .build()

        val outerGeofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID_OUTER)
            .setCircularRegion(lat, lng, OUTER_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofence(innerGeofence)
            .addGeofence(outerGeofence)
            .build()

        try {
            geofencingClient.addGeofences(request, getGeofencePendingIntent()).await()
            Log.d(TAG, "Two-ring geofence registered: inner=10m, outer=100m at ($lat, $lng)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register geofence", e)
        }
    }

    suspend fun unregisterGeofence() {
        try {
            geofencingClient.removeGeofences(getGeofencePendingIntent()).await()
            Log.d(TAG, "Geofences unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister geofence", e)
        }
    }

    suspend fun reRegisterGeofence() {
        unregisterGeofence()
        registerGeofence()
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return hasFine && hasBg
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(context, PENDING_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}
