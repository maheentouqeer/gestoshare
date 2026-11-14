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
    val items: List<ContentItem>,
    val progress: Float = 0f, // 0.0 to 1.0
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedMbps: Float = 0f,
    val status: TransferStatus = TransferStatus.PREPARING,
    val direction: TransferDirection = TransferDirection.SEND,
    val errorMessage: String? = null,
    val sha256Checksum: String = "Pending Verification",
    val isIntegrityVerified: Boolean = false,
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
