package com.example.model

enum class TransferStatus(val label: String) {
    PREPARING("Preparing Transfer..."),
    AWAITING_ACCEPTANCE("Awaiting Receiver Consent..."),
    TRANSFERRING("Transferring Data"),
    VERIFYING("Verifying SHA-256..."),
    SUCCESS("Transfer Verified & Completed"),
    FAILED("Transfer Failed"),
    CANCELLED("Transfer Cancelled")
}

/**
 * Granular pipeline stage so UI & Diagnostics distinguish raw Nearby byte delivery
 * from scoped-storage copy, SHA-256 integrity verification, and public MediaStore export.
 */
enum class TransferPipelineStage(val label: String, val stepOrder: Int) {
    DISCOVERING("Discovering Peers", 0),
    CONNECTING("Connecting Peer", 1),
    AUTHENTICATING("Confirming 6-Digit Code", 2),
    CONNECTED("Peer Connected", 3),
    CONTENT_STAGED("Content Staged", 4),
    RECIPIENT_SELECTED("Recipient Selected", 5),
    WAITING_FOR_FIRE("Armed for THREE_GUN", 6),
    PREPARING("Preparing Transfer", 6),
    AWAITING_ACCEPTANCE("Awaiting Receiver Consent", 7),
    AWAITING_USER_ACCEPTANCE("Awaiting Receiver Consent", 7),
    SENDING("Sending Payload", 7),
    RECEIVING("Receiving Payload", 7),
    TRANSFERRING("Transferring Payload", 7),
    TRANSPORT_COMPLETED("Transport Bytes Delivered", 8),
    COPYING_TO_PRIVATE_STORAGE("Copying Payload via ContentResolver", 9),
    VERIFYING_INTEGRITY("Verifying SHA-256 Digest", 10),
    EXPORTING_TO_DOWNLOADS("Exporting to Downloads/GestoShare", 11),
    COMPLETED("Verified & Saved", 12),
    SUCCESS("Verified & Saved", 12),
    FAILED("Transfer Failed", -1),
    CANCELLED("Transfer Cancelled", -2)
}

enum class TransferDirection {
    SEND,
    RECEIVE
}

data class TransferItemMetadata(
    val itemIndex: Int,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentType: ContentType,
    val sha256: String,
    val textContent: String? = null
)

data class IncomingTransferOffer(
    val transferId: String,
    val endpointId: String,
    val senderName: String,
    val totalBytes: Long,
    val items: List<TransferItemMetadata>
)

data class ActiveTransfer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val receiverName: String,
    val recipientEndpointId: String = "",
    val items: List<ContentItem>,
    val progress: Float = 0f,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedMbps: Float = 0f,
    val status: TransferStatus = TransferStatus.PREPARING,
    val pipelineStage: TransferPipelineStage = TransferPipelineStage.PREPARING,
    val direction: TransferDirection = TransferDirection.SEND,
    val errorMessage: String? = null,
    val lastErrorCode: String? = null,
    val sha256Checksum: String = "Pending Verification",
    val isIntegrityVerified: Boolean = false,
    val transportCompleted: Boolean = false,
    val fileCopyCompleted: Boolean = false,
    val mediaStoreExported: Boolean? = null,
    val mediaStoreExportNote: String? = null,
    val statusDetail: String? = null,
    val savedUriString: String? = null,
    val savedMimeType: String? = null
)

data class TransferRecord(
    val id: String,
    val fileName: String,
    val fileType: String,
    val fileSizeFormatted: String,
    val senderName: String,
    val receiverName: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val timestamp: Long,
    val sha256Verification: String
)
