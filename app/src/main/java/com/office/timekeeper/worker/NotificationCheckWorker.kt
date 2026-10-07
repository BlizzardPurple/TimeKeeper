package com.office.timekeeper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.domain.NotificationHelper
import com.office.timekeeper.domain.WeekBoundaryUtil
import com.office.timekeeper.widget.widgetSettingsDataStore
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Periodic worker that runs every 15 minutes to evaluate the user's
 * progress toward the weekly attendance target and fires smart
 * notifications when approaching the goal.
 *
 * Decision table (only when qualifyingDays >= targetDays):
 *  - remainingMs in (600_000, 1_200_000] → "20 minutes to go!"
 *  - remainingMs in (0, 600_000]         → "10 minutes to go!"
 *  - remainingMs <= 0                    → "Weekly target met! 🎉"
 *
 * Notification state is tracked via SharedPreferences to avoid
 * re-firing the same alert within a single attendance week.
 *
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6
 */
class NotificationCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "notification_check_worker"

        /** SharedPreferences file name for notification tracking. */
        private const val PREFS_NAME = "notification_check_prefs"

        /** Key prefix for the current week's notification flags. */
        private const val KEY_PREFIX = "notification_week_"

        // Notification flag suffixes
        private const val FLAG_20_MIN = "_20_min_fired"
        private const val FLAG_10_MIN = "_10_min_fired"
        private const val FLAG_TARGET_MET = "_target_met_fired"

        // Time thresholds in milliseconds
        const val TWENTY_MINUTES_MS = 1_200_000L
        const val TEN_MINUTES_MS = 600_000L

        /** 4 hours in milliseconds — minimum for a day to qualify. */
        private const val MIN_MS_FOR_ATTENDANCE = 14_400_000L

        private const val MS_PER_HOUR = 3_600_000L
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        // 1. Get DB, DAOs, DataStore
        val db = AppDatabase.getInstance(context)
        val timeSessionDao = db.timeSessionDao()
        val settingsDataStore = SettingsDataStore(
            context.widgetSettingsDataStore
        )

        // 2. Check for open session — if none, skip (no notifications while checked out)
        val openSession = timeSessionDao.getOpenSessionSnapshot()
            ?: return Result.success()

        // 3. Compute current week boundaries
        val weekStart = WeekBoundaryUtil.getCurrentWeekStart()
        val weekEnd = WeekBoundaryUtil.getCurrentWeekEnd()
        val startStr = weekStart.toString()
        val endStr = weekEnd.toString()

        // 4. Compute current week total (closed sessions + open elapsed)
        val sessions = timeSessionDao.getSessionsInRangeSnapshot(startStr, endStr)

        val closedTotal = sessions
            .filter { it.checkOutTime != null }
            .sumOf { it.durationMs }

        val openElapsed = System.currentTimeMillis() - openSession.checkInTime
        val currentWeekTotal = closedTotal + openElapsed

        // 5. Compute qualifying days (including today's projection)
        val today = LocalDate.now(WeekBoundaryUtil.IST)
        val dailyTotals = mutableMapOf<String, Long>()
        for (session in sessions) {
            if (session.checkOutTime != null) {
                dailyTotals[session.date] =
                    (dailyTotals[session.date] ?: 0L) + session.durationMs
            }
        }
        // Project open session elapsed time onto today
        val todayStr = today.toString()
        dailyTotals[todayStr] = (dailyTotals[todayStr] ?: 0L) + openElapsed

        val qualifyingDays = dailyTotals.count { (dateStr, totalMs) ->
            val date = LocalDate.parse(dateStr)
            date.dayOfWeek != DayOfWeek.SATURDAY &&
                date.dayOfWeek != DayOfWeek.SUNDAY &&
                totalMs >= MIN_MS_FOR_ATTENDANCE
        }

        // 6. Get target settings
        val targetDays = settingsDataStore.getTargetDaysPerWeek()
        val targetHoursPerDay = settingsDataStore.getTargetHoursPerDay()
        val targetWeeklyMs = targetDays.toLong() * targetHoursPerDay * MS_PER_HOUR

        // 7. Compute remaining time
        val remainingMs = targetWeeklyMs - currentWeekTotal

        // 8. Check if qualifying days condition is met (Req 11.5)
        if (qualifyingDays < targetDays) {
            return Result.success()
        }

        // 9. Fire appropriate notification based on remaining time brackets
        val notificationHelper = NotificationHelper(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val weekKey = KEY_PREFIX + startStr

        // Clean up old week keys on week change
        cleanupOldWeekKeys(prefs, weekKey)

        when {
            remainingMs <= 0 -> {
                // Target met (Req 11.4)
                if (!prefs.getBoolean(weekKey + FLAG_TARGET_MET, false)) {
                    notificationHelper.notify(
                        NotificationHelper.NOTIFICATION_TARGET_MET,
                        notificationHelper.buildTargetMetNotification()
                    )
                    prefs.edit().putBoolean(weekKey + FLAG_TARGET_MET, true).apply()
                }
            }
            remainingMs <= TEN_MINUTES_MS -> {
                // 10 minutes to go (Req 11.3)
                if (!prefs.getBoolean(weekKey + FLAG_10_MIN, false)) {
                    notificationHelper.notify(
                        NotificationHelper.NOTIFICATION_PROGRESS_10_MIN,
                        notificationHelper.buildProgress10MinNotification()
                    )
                    prefs.edit().putBoolean(weekKey + FLAG_10_MIN, true).apply()
                }
            }
            remainingMs <= TWENTY_MINUTES_MS -> {
                // 20 minutes to go (Req 11.2)
                if (!prefs.getBoolean(weekKey + FLAG_20_MIN, false)) {
                    notificationHelper.notify(
                        NotificationHelper.NOTIFICATION_PROGRESS_20_MIN,
                        notificationHelper.buildProgress20MinNotification()
                    )
                    prefs.edit().putBoolean(weekKey + FLAG_20_MIN, true).apply()
                }
            }
            // remainingMs > 20 min — no notification
        }

        return Result.success()
    }

    /**
     * Removes SharedPreferences entries from previous weeks so they
     * don't accumulate indefinitely. Keeps only keys for [currentWeekKey].
     */
    private fun cleanupOldWeekKeys(
        prefs: android.content.SharedPreferences,
        currentWeekKey: String
    ) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) && !it.startsWith(currentWeekKey) }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}
