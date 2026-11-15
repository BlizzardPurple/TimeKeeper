package com.office.timekeeper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.data.repository.AttendanceRepository
import com.office.timekeeper.domain.NotificationHelper
import com.office.timekeeper.widget.TimeKeeperWidgetProvider
import com.office.timekeeper.widget.widgetSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles taps on notification action buttons (Sign In, Sign Out, Dismiss).
 *
 * - ACTION_SIGN_IN:  invokes [AttendanceRepository.checkIn], cancels the notification,
 *   and refreshes the Glance widget.  (Req 8.4)
 * - ACTION_SIGN_OUT: invokes [AttendanceRepository.checkOut] for the open session,
 *   cancels the notification, and refreshes the widget.  (Req 8.5)
 * - ACTION_DISMISS:  cancels the notification without modifying attendance state. (Req 8.6)
 *
 * Uses [goAsync] + a coroutine to perform suspend operations safely inside the
 * BroadcastReceiver window.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", -1)

        when (intent.action) {
            NotificationHelper.ACTION_SIGN_IN -> {
                goAsync().let { pendingResult ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
                            val repo = AttendanceRepository(
                                db.timeSessionDao(),
                                db.weeklySummaryDao(),
                                db.archivedTimeSessionDao(),
                                settingsDs
                            )
                            repo.checkIn()

                            // Refresh the widget to reflect checked-in state
                            TimeKeeperWidgetProvider.updateAllWidgets(context)
                        } catch (_: Exception) {
                            // Silently handle errors (e.g., weekend check-in, already checked in)
                        }
                        NotificationHelper(context).cancel(notificationId)
                        pendingResult.finish()
                    }
                }
            }

            NotificationHelper.ACTION_SIGN_OUT -> {
                goAsync().let { pendingResult ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
                            val repo = AttendanceRepository(
                                db.timeSessionDao(),
                                db.weeklySummaryDao(),
                                db.archivedTimeSessionDao(),
                                settingsDs
                            )
                            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
                            if (openSession != null) {
                                repo.checkOut(openSession.id)
                            }

                            // Refresh the widget to reflect checked-out state
                            TimeKeeperWidgetProvider.updateAllWidgets(context)
                        } catch (_: Exception) {
                            // Silently handle errors (e.g., no open session found)
                        }
                        NotificationHelper(context).cancel(notificationId)
                        pendingResult.finish()
                    }
                }
            }

            NotificationHelper.ACTION_DISMISS -> {
                NotificationHelper(context).cancel(notificationId)
            }
        }
    }
}
