package com.office.timekeeper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.geofence.GeofenceManager
import com.office.timekeeper.widget.widgetSettingsDataStore
import com.office.timekeeper.worker.NotificationCheckWorker
import com.office.timekeeper.worker.WeeklyResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Re-registers background services after a device reboot.
 *
 * Android clears geofences and cancels pending WorkManager jobs on
 * reboot, so this receiver restores:
 *  1. The office geofence via [GeofenceManager]
 *  2. The [WeeklyResetWorker] scheduled for the next Sunday noon IST
 *  3. The [NotificationCheckWorker] if an active (open) session exists
 *
 * Requires `RECEIVE_BOOT_COMPLETED` permission and an intent-filter
 * for `ACTION_BOOT_COMPLETED` in AndroidManifest.xml.
 *
 * Requirements: 15.1, 15.2, 15.3
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        goAsync().let { pendingResult ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Re-register geofence (Req 15.1)
                    val geofencingClient = LocationServices.getGeofencingClient(context)
                    val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
                    val geofenceManager = GeofenceManager(context, geofencingClient, settingsDs)
                    geofenceManager.registerGeofence()

                    // 2. Reschedule WeeklyResetWorker (Req 15.2)
                    WeeklyResetWorker.schedule(context)

                    // 3. If active session exists, schedule NotificationCheckWorker (Req 15.3)
                    val db = AppDatabase.getInstance(context)
                    val openSession = db.timeSessionDao().getOpenSessionSnapshot()
                    if (openSession != null) {
                        val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(
                            15, TimeUnit.MINUTES
                        ).build()
                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            NotificationCheckWorker.WORK_NAME,
                            ExistingPeriodicWorkPolicy.KEEP,
                            request
                        )
                    }
                } catch (_: Exception) {
                    // Swallow — nothing we can do if recovery fails on boot
                }
                pendingResult.finish()
            }
        }
    }
}
