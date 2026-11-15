package com.office.timekeeper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
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

    private suspend fun handleToggle(context: Context) {
        val db = AppDatabase.getInstance(context)
        val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
        val repo = AttendanceRepository(db.timeSessionDao(), db.weeklySummaryDao(), db.archivedTimeSessionDao(), settingsDs)

        try {
            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
            if (openSession != null) {
                repo.checkOut(openSession.id)
            } else {
                repo.checkIn()
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

            // Status
            views.setTextViewText(R.id.status_text, if (isCheckedIn) "IN" else "OUT")
            views.setInt(R.id.status_dot, "setBackgroundColor",
                if (isCheckedIn) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
            )

            // Chronometer (live timer while checked in)
            if (isCheckedIn) {
                val elapsedSinceCheckIn = System.currentTimeMillis() - openSession!!.checkInTime
                views.setViewVisibility(R.id.session_chronometer, View.VISIBLE)
                views.setViewVisibility(R.id.today_duration, View.GONE)
                views.setChronometer(R.id.session_chronometer,
                    SystemClock.elapsedRealtime() - elapsedSinceCheckIn,
                    null, true)
            } else {
                views.setViewVisibility(R.id.session_chronometer, View.GONE)
                views.setViewVisibility(R.id.today_duration, View.VISIBLE)
                // Show today's total when checked out
                val today = LocalDate.now(WeekBoundaryUtil.IST).toString()
                val todayMs = dao.getTotalDurationForDateSnapshot(today)
                views.setTextViewText(R.id.today_duration, formatDuration(todayMs))
            }

            // Week total
            val weekStart = WeekBoundaryUtil.getCurrentWeekStart().toString()
            val weekEnd = WeekBoundaryUtil.getCurrentWeekEnd().toString()
            val weekSessions = dao.getSessionsInRangeSnapshot(weekStart, weekEnd)
            val weekClosedMs = weekSessions.filter { it.checkOutTime != null }.sumOf { it.durationMs }
            val weekOpenMs = if (isCheckedIn) System.currentTimeMillis() - openSession!!.checkInTime else 0L
            views.setTextViewText(R.id.week_duration, "Week: ${formatDuration(weekClosedMs + weekOpenMs)}")

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
            val targetMet = qualifyingDays >= adjustedTarget && (weekClosedMs + weekOpenMs) >= adjustedTarget * 8 * 3_600_000L
            views.setTextViewText(R.id.days_count, "Days: $qualifyingDays/$adjustedTarget${if (targetMet) " ✓" else ""}")

            // Toggle button
            views.setTextViewText(R.id.toggle_button, if (isCheckedIn) "CHECK OUT" else "CHECK IN")

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
