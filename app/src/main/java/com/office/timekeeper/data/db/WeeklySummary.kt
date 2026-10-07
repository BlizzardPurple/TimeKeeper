package com.office.timekeeper.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_summaries")
data class WeeklySummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekStartDate: String,     // "YYYY-MM-DD" (Monday of the week)
    val weekEndDate: String,       // "YYYY-MM-DD" (Friday of the week)
    val totalMs: Long,             // sum of all session durations in the week
    val daysAttended: Int,         // count of days meeting 4h minimum
    val targetMet: Boolean         // daysAttended >= targetDays AND totalMs >= targetWeeklyMs
)
