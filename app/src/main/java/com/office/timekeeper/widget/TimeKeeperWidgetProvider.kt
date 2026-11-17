package com.office.timekeeper.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.office.timekeeper.MainActivity
import com.office.timekeeper.R
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.data.repository.AttendanceRepository
import com.office.timekeeper.domain.WeekBoundaryUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class TimeKeeperWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.office.timekeeper.WIDGET_TOGGLE"
        private const val MIN_MS_FOR_ATTENDANCE = 4 * 3_600_000L
        private const val REFRESH_REQUEST_CODE = 999
        private const val REFRESH_INTERVAL_MS = 60_000L // 1 minute

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, TimeKeeperWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(context).getAppWidgetIds(
                        ComponentName(context, TimeKeeperWidgetProvider::class.java)
                    )
                )
            }
            context.sendBroadcast(intent)
        }

        /**
         * Schedules a repeating alarm to refresh the widget every 60 seconds.
         * Called when the user checks in so the "Xh Ym" display stays current.
         */
        fun scheduleMinuteRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = getRefreshPendingIntent(context)
            alarmManager.setRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                pendingIntent
            )
        }

        /**
         * Cancels the minute-by-minute refresh alarm.
         * Called when the user checks out.
         */
        fun cancelMinuteRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(getRefreshPendingIntent(context))
        }

        private fun getRefreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, TimeKeeperWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(context).getAppWidgetIds(
                        ComponentName(context, TimeKeeperWidgetProvider::class.java)
                    )
                )
            }
            return PendingIntent.getBroadcast(
                context, REFRESH_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            CoroutineScope(Dispatchers.IO).launch {
                handleToggle(context)
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMinuteRefresh(context)
    }

    private suspend fun handleToggle(context: Context) {
        val db = AppDatabase.getInstance(context)
        val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
        val repo = AttendanceRepository(db.timeSessionDao(), db.weeklySummaryDao(), db.archivedTimeSessionDao(), settingsDs)

        try {
            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
            if (openSession != null) {
                repo.checkOut(openSession.id)
                cancelMinuteRefresh(context)
            } else {
                repo.checkIn()
                scheduleMinuteRefresh(context)
            }
        } catch (_: Exception) {}

        // Refresh all widgets
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, TimeKeeperWidgetProvider::class.java))
        for (id in ids) {
            updateWidget(context, appWidgetManager, id)
        }

        // Launch app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntent?.let { context.startActivity(it) }
    }

    private suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val db = AppDatabase.getInstance(context)
        val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
        val dao = db.timeSessionDao()

        val views = RemoteViews(context.packageName, R.layout.widget_timekeeper)

        try {
            val openSession = dao.getOpenSessionSnapshot()
            val isCheckedIn = openSession != null

            // Status dot + text
            views.setTextViewText(R.id.status_text, if (isCheckedIn) "IN" else "OUT")
            views.setInt(R.id.status_dot, "setBackgroundColor",
                if (isCheckedIn) 0xFF4CAF50.toInt() else 0xFF666666.toInt()
            )

            // Today's duration — "Xh Ym" format, includes open session elapsed
            val todayStr = LocalDate.now(WeekBoundaryUtil.IST).toString()
            val todayClosedMs = dao.getTotalDurationForDateSnapshot(todayStr)
            val todayOpenMs = if (isCheckedIn) System.currentTimeMillis() - openSession!!.checkInTime else 0L
            val todayTotalMs = todayClosedMs + todayOpenMs
            views.setTextViewText(R.id.today_duration, formatDuration(todayTotalMs))
            views.setTextColor(R.id.today_duration,
                if (isCheckedIn) 0xFF4CAF50.toInt() else 0xFFBBBBBB.toInt()
            )

            // Week total
            val weekStart = WeekBoundaryUtil.getCurrentWeekStart().toString()
            val weekEnd = WeekBoundaryUtil.getCurrentWeekEnd().toString()
            val weekSessions = dao.getSessionsInRangeSnapshot(weekStart, weekEnd)
            val weekClosedMs = weekSessions.filter { it.checkOutTime != null }.sumOf { it.durationMs }
            views.setTextViewText(R.id.week_duration, "Week: ${formatDuration(weekClosedMs + todayOpenMs)}")

            // Days count
            val targetDays = settingsDs.getTargetDaysPerWeek()
            val absences = settingsDs.getAbsencesThisWeek()
            val adjustedTarget = (targetDays - absences).coerceAtLeast(0)
            val dailyTotals = weekSessions.filter { it.checkOutTime != null }
                .groupBy { it.date }
                .mapValues { (_, sessions) -> sessions.sumOf { it.durationMs } }
            val qualifyingDays = dailyTotals.count { (dateStr, ms) ->
                val d = LocalDate.parse(dateStr)
                d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && ms >= MIN_MS_FOR_ATTENDANCE
            }
            val totalWeekMs = weekClosedMs + todayOpenMs
            val targetMet = qualifyingDays >= adjustedTarget && totalWeekMs >= adjustedTarget * 8 * 3_600_000L
            views.setTextViewText(R.id.days_count, "Days: $qualifyingDays/$adjustedTarget${if (targetMet) " ✓" else ""}")

            // Toggle button — RED when checked in, GREEN when out
            views.setTextViewText(R.id.toggle_button, if (isCheckedIn) "CHECK OUT" else "CHECK IN")
            views.setInt(R.id.toggle_button, "setBackgroundColor",
                if (isCheckedIn) 0xFFE53935.toInt() else 0xFF4CAF50.toInt()
            )

            // Manage minute refresh alarm
            if (isCheckedIn) {
                scheduleMinuteRefresh(context)
            } else {
                cancelMinuteRefresh(context)
            }

        } catch (_: Exception) {
            views.setTextViewText(R.id.status_text, "OUT")
            views.setTextViewText(R.id.today_duration, "0h 0m")
            views.setTextViewText(R.id.week_duration, "Week: 0h 0m")
            views.setTextViewText(R.id.days_count, "Days: 0/3")
            views.setTextViewText(R.id.toggle_button, "CHECK IN")
        }

        // Click to open app
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

        // Toggle button click
        val toggleIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, TimeKeeperWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.toggle_button, toggleIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0h 0m"
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }
}
