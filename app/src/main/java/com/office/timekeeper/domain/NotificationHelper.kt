package com.office.timekeeper.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.office.timekeeper.R

/**
 * Creates and manages notification channels and notifications for TimeKeeper.
 *
 * Channels:
 * - geofence_prompts: Heads-up notifications for office arrival/departure
 * - smart_progress: Alerts when approaching weekly target
 * - celebrations: Weekly target met celebration
 */
class NotificationHelper(private val context: Context) {

    companion object {
        // Channel IDs
        const val CHANNEL_GEOFENCE = "geofence_prompts"
        const val CHANNEL_SMART_PROGRESS = "smart_progress"
        const val CHANNEL_CELEBRATIONS = "celebrations"

        // Notification IDs
        const val NOTIFICATION_GEOFENCE_ARRIVAL = 1001
        const val NOTIFICATION_GEOFENCE_DEPARTURE = 1002
        const val NOTIFICATION_PROGRESS_20_MIN = 2001
        const val NOTIFICATION_PROGRESS_10_MIN = 2002
        const val NOTIFICATION_TARGET_MET = 3001

        // Action intents
        const val ACTION_SIGN_IN = "com.office.timekeeper.ACTION_SIGN_IN"
        const val ACTION_SIGN_OUT = "com.office.timekeeper.ACTION_SIGN_OUT"
        const val ACTION_DISMISS = "com.office.timekeeper.ACTION_DISMISS"
    }

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    /**
     * Creates all notification channels. Safe to call multiple times —
     * the system ignores channels that already exist.
     */
    fun createNotificationChannels() {
        val geofenceChannel = NotificationChannel(
            CHANNEL_GEOFENCE,
            "Geofence Prompts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sign-in and sign-out prompts when arriving at or leaving the office"
        }

        val progressChannel = NotificationChannel(
            CHANNEL_SMART_PROGRESS,
            "Smart Progress",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when approaching your weekly attendance target"
        }

        val celebrationsChannel = NotificationChannel(
            CHANNEL_CELEBRATIONS,
            "Celebrations",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification when weekly attendance target is met"
        }

        notificationManager.createNotificationChannels(
            listOf(geofenceChannel, progressChannel, celebrationsChannel)
        )
    }

    /**
     * Builds a geofence arrival notification.
     * Title: "You've arrived at office"
     * Actions: [Sign In] [Dismiss]
     *
     * Requirement 8.2
     */
    fun buildGeofenceArrivalNotification(): NotificationCompat.Builder {
        val signInIntent = createActionPendingIntent(ACTION_SIGN_IN, NOTIFICATION_GEOFENCE_ARRIVAL)
        val dismissIntent = createActionPendingIntent(ACTION_DISMISS, NOTIFICATION_GEOFENCE_ARRIVAL)

        return NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("You've arrived at office")
            .setContentText("Tap Sign In to start tracking your attendance")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Sign In", signInIntent)
            .addAction(0, "Dismiss", dismissIntent)
    }

    /**
     * Builds a geofence departure notification.
     * Title: "Leaving office?"
     * Actions: [Sign Out] [Dismiss]
     *
     * Requirement 8.3
     */
    fun buildGeofenceDepartureNotification(): NotificationCompat.Builder {
        val signOutIntent = createActionPendingIntent(ACTION_SIGN_OUT, NOTIFICATION_GEOFENCE_DEPARTURE)
        val dismissIntent = createActionPendingIntent(ACTION_DISMISS, NOTIFICATION_GEOFENCE_DEPARTURE)

        return NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Leaving office?")
            .setContentText("Tap Sign Out to record your departure")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Sign Out", signOutIntent)
            .addAction(0, "Dismiss", dismissIntent)
    }

    /**
     * Builds a "20 minutes to go!" smart progress notification.
     *
     * Requirement 11.2
     */
    fun buildProgress20MinNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_SMART_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("20 minutes to go!")
            .setContentText("You're almost at your weekly target")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
    }

    /**
     * Builds a "10 minutes to go!" smart progress notification.
     *
     * Requirement 11.3
     */
    fun buildProgress10MinNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_SMART_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("10 minutes to go!")
            .setContentText("Almost there — just a few more minutes")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
    }

    /**
     * Builds a "Weekly target met!" celebration notification.
     *
     * Requirement 11.4
     */
    fun buildTargetMetNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_CELEBRATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Weekly target met! \uD83C\uDF89")
            .setContentText("Great job — you've hit your attendance goal for the week")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
    }

    /**
     * Posts a notification using the given builder and notification ID.
     */
    fun notify(notificationId: Int, builder: NotificationCompat.Builder) {
        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Cancels a notification by its ID.
     */
    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    /**
     * Creates a PendingIntent that broadcasts to NotificationActionReceiver.
     */
    private fun createActionPendingIntent(action: String, notificationId: Int): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
            putExtra("notification_id", notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
