package com.office.timekeeper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSessionDao {
    @Insert
    suspend fun insert(session: TimeSession): Long

    @Update
    suspend fun update(session: TimeSession)

    @Query("SELECT * FROM time_sessions WHERE date = :date ORDER BY checkInTime ASC")
    fun getSessionsByDate(date: String): Flow<List<TimeSession>>

    @Query("SELECT * FROM time_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, checkInTime ASC")
    fun getSessionsInRange(startDate: String, endDate: String): Flow<List<TimeSession>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM time_sessions WHERE date = :date AND checkOutTime IS NOT NULL")
    fun getTotalDurationForDate(date: String): Flow<Long>

    @Query("SELECT * FROM time_sessions WHERE checkOutTime IS NULL LIMIT 1")
    fun getOpenSession(): Flow<TimeSession?>

    @Query("SELECT DISTINCT date FROM time_sessions WHERE date BETWEEN :startDate AND :endDate")
    fun getDistinctDatesInRange(startDate: String, endDate: String): Flow<List<String>>

    @Query("DELETE FROM time_sessions WHERE date BETWEEN :startDate AND :endDate")
    suspend fun deleteSessionsInRange(startDate: String, endDate: String)

    @Query("SELECT * FROM time_sessions WHERE checkOutTime IS NULL LIMIT 1")
    suspend fun getOpenSessionSnapshot(): TimeSession?

    @Query("SELECT * FROM time_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, checkInTime ASC")
    suspend fun getSessionsInRangeSnapshot(startDate: String, endDate: String): List<TimeSession>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM time_sessions WHERE date = :date AND checkOutTime IS NOT NULL")
    suspend fun getTotalDurationForDateSnapshot(date: String): Long

    @Query("SELECT * FROM time_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): TimeSession?
}
