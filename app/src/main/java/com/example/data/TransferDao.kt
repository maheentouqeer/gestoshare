package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_records ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(record: TransferRecordEntity)

    @Query("DELETE FROM transfer_records WHERE id = :id")
    suspend fun deleteTransferById(id: String)

    @Query("DELETE FROM transfer_records")
    suspend fun clearAll()
}
