package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {

    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC")
    fun getAllErrorEvents(): Flow<List<DiagnosticEventEntity>>

    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC")
    suspend fun getAllErrorEventsSnapshot(): List<DiagnosticEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrorEvent(event: DiagnosticEventEntity)

    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY timestamp DESC LIMIT :maxRows)")
    suspend fun trimErrorEvents(maxRows: Int)

    @Query("DELETE FROM diagnostic_events WHERE id = :id")
    suspend fun deleteErrorEventById(id: String)

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearAllErrorEvents()

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<DiagnosticLogEntity>>

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSnapshot(): List<DiagnosticLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DiagnosticLogEntity)

    @Query("DELETE FROM diagnostic_logs WHERE id NOT IN (SELECT id FROM diagnostic_logs ORDER BY timestamp DESC LIMIT :maxRows)")
    suspend fun trimLogs(maxRows: Int)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearAllLogs()

    @Query("SELECT * FROM recent_devices ORDER BY lastConnectedAt DESC")
    fun getRecentDevices(): Flow<List<RecentDeviceEntity>>

    @Query("SELECT * FROM recent_devices WHERE deviceKey = :key LIMIT 1")
    suspend fun getRecentDevice(key: String): RecentDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentDevice(device: RecentDeviceEntity)

    @Query("DELETE FROM recent_devices WHERE deviceKey = :key")
    suspend fun deleteRecentDevice(key: String)

    @Query("DELETE FROM recent_devices")
    suspend fun clearRecentDevices()
}
