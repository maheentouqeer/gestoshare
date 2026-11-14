package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.example.model.TransferStatus

@Entity(tableName = "transfer_records")
data class TransferRecordEntity(
    @PrimaryKey
    val id: String,
    val fileName: String,
    val fileType: String,
    val fileSizeFormatted: String,
    val senderName: String,
    val receiverName: String,
    val direction: String, // "SEND" or "RECEIVE"
    val status: String,    // "SUCCESS", "FAILED", "CANCELLED"
    val timestamp: Long,
    val sha256Verification: String
) {
    fun toDomainModel(): TransferRecord {
        return TransferRecord(
            id = id,
            fileName = fileName,
            fileType = fileType,
            fileSizeFormatted = fileSizeFormatted,
            senderName = senderName,
            receiverName = receiverName,
            direction = try {
                TransferDirection.valueOf(direction)
            } catch (e: Exception) {
                TransferDirection.SEND
            },
            status = try {
                TransferStatus.valueOf(status)
            } catch (e: Exception) {
                TransferStatus.SUCCESS
            },
            timestamp = timestamp,
            sha256Verification = sha256Verification
        )
    }

    companion object {
        fun fromDomainModel(record: TransferRecord): TransferRecordEntity {
            return TransferRecordEntity(
                id = record.id,
                fileName = record.fileName,
                fileType = record.fileType,
                fileSizeFormatted = record.fileSizeFormatted,
                senderName = record.senderName,
                receiverName = record.receiverName,
                direction = record.direction.name,
                status = record.status.name,
                timestamp = record.timestamp,
                sha256Verification = record.sha256Verification
            )
        }
    }
}
