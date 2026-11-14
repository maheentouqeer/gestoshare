package com.example.data

import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TransferHistoryRepository {
    fun getAllTransfers(): Flow<List<TransferRecord>>
    suspend fun insertTransfer(record: TransferRecord)
    suspend fun deleteTransfer(id: String)
    suspend fun clearHistory()
}

class RoomTransferHistoryRepository(
    private val transferDao: TransferDao
) : TransferHistoryRepository {

    override fun getAllTransfers(): Flow<List<TransferRecord>> {
        return transferDao.getAllTransfers().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun insertTransfer(record: TransferRecord) {
        transferDao.insertTransfer(TransferRecordEntity.fromDomainModel(record))
    }

    override suspend fun deleteTransfer(id: String) {
        transferDao.deleteTransferById(id)
    }

    override suspend fun clearHistory() {
        transferDao.clearAll()
    }
}
