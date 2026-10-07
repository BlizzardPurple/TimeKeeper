package com.office.timekeeper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchivedTimeSessionDao {
    @Insert
    suspend fun insertAll(sessions: List<ArchivedTimeSession>)

    @Query("SELECT * FROM archived_time_sessions WHERE weeklySummaryId = :weeklySummaryId ORDER BY date ASC, checkInTime ASC")
    fun getSessionsByWeeklySummaryId(weeklySummaryId: Long): Flow<List<ArchivedTimeSession>>

    @Query("SELECT * FROM archived_time_sessions WHERE weeklySummaryId = :weeklySummaryId ORDER BY date ASC, checkInTime ASC")
    suspend fun getSessionsByWeeklySummaryIdSnapshot(weeklySummaryId: Long): List<ArchivedTimeSession>
}
