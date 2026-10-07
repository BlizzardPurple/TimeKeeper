package com.office.timekeeper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archived_time_sessions",
    indices = [Index(value = ["weeklySummaryId"]), Index(value = ["date"])]
)
data class ArchivedTimeSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weeklySummaryId: Long,     // FK to WeeklySummary.id
    val date: String,              // "YYYY-MM-DD" format
    val checkInTime: Long,         // epoch milliseconds
    val checkOutTime: Long,        // non-null (must be closed before archival)
    val durationMs: Long           // computed duration in milliseconds
)
