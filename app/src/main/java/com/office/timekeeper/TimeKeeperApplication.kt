package com.office.timekeeper

import android.app.Application
import com.office.timekeeper.di.AppModule
import com.office.timekeeper.domain.NotificationHelper

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
    }
}
