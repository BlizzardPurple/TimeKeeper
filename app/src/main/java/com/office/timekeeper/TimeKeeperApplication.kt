package com.office.timekeeper

import android.app.Application
import com.office.timekeeper.di.AppModule
import com.office.timekeeper.domain.NotificationHelper
import kotlinx.coroutines.launch

/**
 * Application class for TimeKeeper.
 *
 * Initializes the manual DI container ([AppModule]) and registers
 * notification channels at startup.
 *
 * Requirements: N/A (app initialization)
 */
class TimeKeeperApplication : Application() {

    lateinit var appModule: AppModule
        private set

    override fun onCreate() {
        super.onCreate()
        appModule = AppModule(this)

        // Create notification channels early so they are available
        // before any worker or receiver fires a notification.
        appModule.notificationHelper.createNotificationChannels()

        // Schedule weekly reset worker on app start (not just on reboot)
        com.office.timekeeper.worker.WeeklyResetWorker.schedule(this)

        // Belt-and-suspenders: reset absences if we missed a weekly boundary
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val lastReset = appModule.settingsDataStore.getLastWeeklyResetMs()
                val nextReset = com.office.timekeeper.domain.WeekBoundaryUtil.getNextResetInstant()
                // If last reset is before the PREVIOUS reset boundary, we missed one
                val now = java.time.Instant.now()
                if (lastReset > 0 && java.time.Instant.ofEpochMilli(lastReset).isBefore(nextReset.minus(java.time.Duration.ofDays(7)))) {
                    appModule.settingsDataStore.setAbsencesThisWeek(0)
                    appModule.settingsDataStore.setLastWeeklyResetMs(System.currentTimeMillis())
                    android.util.Log.d("TimeKeeperApp", "Safety: reset absences — missed weekly boundary")
                }
            } catch (e: Exception) {
                android.util.Log.e("TimeKeeperApp", "Safety check failed", e)
            }
        }
    }
}
