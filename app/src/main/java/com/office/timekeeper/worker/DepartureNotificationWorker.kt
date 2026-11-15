package com.office.timekeeper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.domain.NotificationHelper

/**
 * Fires a departure notification after a 5-minute dwell delay.
 * Only fires if the user is still checked in (hasn't manually checked out).
 */
class DepartureNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val openSession = db.timeSessionDao().getOpenSessionSnapshot()

        // Only notify if still checked in
        if (openSession != null) {
            val notificationHelper = NotificationHelper(applicationContext)
            notificationHelper.createNotificationChannels()
            notificationHelper.notify(
                NotificationHelper.NOTIFICATION_GEOFENCE_DEPARTURE,
                notificationHelper.buildGeofenceDepartureNotification()
            )
        }
        return Result.success()
    }
}
