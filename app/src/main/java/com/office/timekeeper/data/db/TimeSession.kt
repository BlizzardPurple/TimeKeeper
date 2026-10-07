package com.office.timekeeper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "time_sessions",
    indices = [Index(value = ["date"])]
)
data class TimeSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,              // "YYYY-MM-DD" format
    val checkInTime: Long,         // epoch milliseconds
    val checkOutTime: Long? = null, // null while session is open
    val durationMs: Long = 0       // computed on check-out: checkOutTime - checkInTime
)
