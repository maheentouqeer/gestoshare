package com.example.model

enum class SessionStatus {
    INITIATED,
    ACCEPTED_LOCALLY,
    CONNECTED,
    REJECTED,
    FAILED,
    DISCONNECTED
}

/**
 * Encapsulates the temporary authentication handshake between two devices.
 * Displays the actual 4-to-6 digit authentication token provided by Nearby Connections.
 */
data class ConnectionSession(
    val endpointId: String,
    val endpointName: String,
    val authDigits: String,
    val isIncoming: Boolean,
    val status: SessionStatus = SessionStatus.INITIATED
)

/**
 * Message model used for the real Phase 3 text payload test over Nearby Connections.
 */
data class NearbyMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderEndpointId: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    val isAcknowledgment: Boolean = false
)
