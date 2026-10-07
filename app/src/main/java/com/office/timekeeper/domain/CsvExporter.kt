package com.office.timekeeper.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.office.timekeeper.data.db.TimeSession
import com.office.timekeeper.data.db.WeeklySummary
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

/**
 * Generates CSV-formatted attendance data for a given week and launches
 * an Android share intent via FileProvider.
 *
 * Requirements: 14.1, 14.2, 14.3
 */
object CsvExporter {

    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(IST)

    /**
     * Generates a CSV string for the given weekly summary and its sessions.
     *
     * The CSV contains:
     * - A summary header row (Week, Start Date, End Date, Total Hours, Days Attended, Target Met)
     * - A blank line separator
     * - Session detail header (Date, Check-In, Check-Out, Duration (hours))
     * - One row per session with durations as decimal hours
     *
     * Req 14.1: summary header + session rows
     * Req 14.3: durations as decimal hours
     */
    fun generateCsv(weeklySummary: WeeklySummary, sessions: List<TimeSession>): String {
        val sb = StringBuilder()

        // Summary header
        sb.appendLine("Week,Start Date,End Date,Total Hours,Days Attended,Target Met")

        val weekLabel = computeWeekLabel(weeklySummary.weekStartDate)
        val totalHours = msToDecimalHours(weeklySummary.totalMs)
        val targetMetStr = if (weeklySummary.targetMet) "Yes" else "No"

        sb.appendLine(
            "$weekLabel,${weeklySummary.weekStartDate},${weeklySummary.weekEndDate},$totalHours,${weeklySummary.daysAttended},$targetMetStr"
        )

        // Blank line separator
        sb.appendLine()

        // Session detail header
        sb.appendLine("Date,Check-In,Check-Out,Duration (hours)")

        // Session rows sorted by date then check-in time
        val sortedSessions = sessions.sortedWith(compareBy({ it.date }, { it.checkInTime }))
        for (session in sortedSessions) {
            val checkIn = formatEpochAsTime(session.checkInTime)
            val checkOut = if (session.checkOutTime != null) {
                formatEpochAsTime(session.checkOutTime)
            } else {
                ""
            }
            val durationHours = msToDecimalHours(session.durationMs)
            sb.appendLine("${session.date},$checkIn,$checkOut,$durationHours")
        }

        return sb.toString()
    }

    /**
     * Writes CSV content to the app cache directory and launches a share intent
     * via FileProvider.
     *
     * Req 14.2: write to cache dir and launch share intent
     */
    fun shareCsv(context: Context, csvContent: String, weekLabel: String) {
        val fileName = "timekeeper_$weekLabel.csv"
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeText(csvContent)

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, cacheFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share attendance CSV")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    /**
     * Computes the ISO week label (e.g. "2025-W03") from a date string in "YYYY-MM-DD" format.
     */
    internal fun computeWeekLabel(dateStr: String): String {
        val date = LocalDate.parse(dateStr)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    /**
     * Converts milliseconds to decimal hours, rounded to 2 decimal places.
     * E.g. 4h 15m → 4.25
     */
    internal fun msToDecimalHours(ms: Long): String {
        val hours = ms.toDouble() / 3_600_000.0
        return String.format("%.2f", hours)
    }

    /**
     * Formats an epoch millisecond timestamp as HH:mm in IST timezone.
     */
    internal fun formatEpochAsTime(epochMs: Long): String {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs))
    }
}
