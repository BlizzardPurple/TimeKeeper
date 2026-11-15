package com.office.timekeeper.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.data.repository.AttendanceRepository
import com.office.timekeeper.domain.WeekBoundaryUtil
import com.office.timekeeper.widget.TimeKeeperWidgetProvider
import com.office.timekeeper.widget.widgetSettingsDataStore
import java.util.concurrent.TimeUnit

/**
 * Archives the current attendance week at Sunday 12:00 PM IST and
 * reschedules itself for the following Sunday.
 *
 * Uses [OneTimeWorkRequest] with a computed initial delay so the
 * archival runs at exactly the week boundary defined by
 * [WeekBoundaryUtil.getNextResetInstant].
 *
 * Req 7.1 — weekly reset fires at Sunday noon IST.
 * Req 7.6 — widget updated after reset.
 * Req 7.7 — worker reschedules itself for the next boundary.
 */
class WeeklyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Build repository from Room + DataStore singletons
            val db = AppDatabase.getInstance(applicationContext)
            val settingsDs = SettingsDataStore(applicationContext.widgetSettingsDataStore)
            val repository = AttendanceRepository(
                timeSessionDao = db.timeSessionDao(),
                weeklySummaryDao = db.weeklySummaryDao(),
                archivedTimeSessionDao = db.archivedTimeSessionDao(),
                settingsDataStore = settingsDs
            )

            // 2. Archive the current week (auto-closes open sessions, creates
            //    WeeklySummary, copies to archive, clears active data)
            repository.archiveCurrentWeek()

            // 2b. Reset absences for the new week
            settingsDs.setAbsencesThisWeek(0)
            settingsDs.setLastWeeklyResetMs(System.currentTimeMillis())

            // 3. Update all widget instances to show the fresh week (0h, 0 days)
            TimeKeeperWidgetProvider.updateAllWidgets(applicationContext)

            // 4. Reschedule for the next Sunday 12:00 PM IST
            schedule(applicationContext)

            Log.d(TAG, "Weekly reset completed. Rescheduled for next Sunday noon IST.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weekly reset failed, will retry.", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WeeklyResetWorker"
        const val WORK_NAME = "weekly_reset"

        /**
         * Enqueues a [OneTimeWorkRequest] that fires at the next Sunday
         * 12:00 PM IST.
         *
         * Uses [ExistingWorkPolicy.REPLACE] so only one pending reset
         * exists at a time — safe to call from [BootReceiver], app
         * startup, or the worker's own reschedule path.
         */
        fun schedule(context: Context) {
            val delayMs = WeekBoundaryUtil.getNextResetInstant().toEpochMilli() -
                System.currentTimeMillis()

            val request = OneTimeWorkRequestBuilder<WeeklyResetWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
