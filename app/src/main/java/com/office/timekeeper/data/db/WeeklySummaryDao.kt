package com.office.timekeeper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklySummaryDao {
    @Insert
    suspend fun insert(summary: WeeklySummary): Long

    @Query("SELECT * FROM weekly_summaries ORDER BY weekStartDate DESC")
    fun getAllSummaries(): Flow<List<WeeklySummary>>

    @Query("SELECT * FROM weekly_summaries WHERE id = :id")
    suspend fun getSummaryById(id: Long): WeeklySummary?
}
