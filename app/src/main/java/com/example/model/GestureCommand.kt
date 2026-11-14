package com.example.model

enum class GestureType(
    val actionName: String,
    val description: String,
    val symbol: String
) {
    LIKE("ACTIVATE", "Initialize gesture session & wake command processing", "👍"),
    FIST("GRAB", "Confirm & hold staged content for transfer", "✊"),
    POINT("SELECT_DEVICE", "Cycle discovered peers using real endpoint IDs", "👉"),
    THREE_GUN("RELEASE / SEND", "Send real TRANSFER_OFFER to connected peer", "🤟"),
    STOP("CANCEL", "Abort active offer/transfer & reset session", "✋"),
    OTHER("NEUTRAL / NO ACTION", "Neutral pose or confidence below threshold", "🤷"),
    NONE("NO HAND", "No hand detected in camera frame", "🔍")
}

enum class GestureOperationMode(
    val label: String,
    val subtitle: String
) {
    DEBUG_DRY_RUN(
        label = "Debug / Dry Run",
        subtitle = "State machine & logs run safely; real P2P file transfers are disabled"
    ),
    GESTURE_TRANSFER(
        label = "Gesture Transfer Mode",
        subtitle = "Gestures trigger real Nearby offers (receiver consent remains mandatory)"
    )
}

data class GestureResult(
    val gesture: GestureType = GestureType.NONE,
    val confidence: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val predictedClass: String = "no hand",
    val predictedClassIndex: Int = -1,
    val classProbabilities: List<Float> = emptyList(),
    val inferenceLatencyMs: Long = 0L,
    val inferenceLatencyUs: Long = 0L,
    val isHandDetected: Boolean = false,
    val isAccepted: Boolean = false,
    val errorMessage: String? = null
)

enum class GestureSessionState(
    val statusLabel: String,
    val isActiveSession: Boolean
) {
    IDLE("Ready — Show 👍 LIKE to activate gesture session", false),
    ACTIVATED("Session Active — Show ✊ FIST to grab staged content", true),
    CONTENT_HELD("Content Grabbed — Show 👉 POINT to select a discovered device", true),
    TARGET_SELECTED("Target Selected — Pair via Nearby handshake to connect", true),
    CONNECTION_PENDING("Connection Pending — Confirm 6-digit code on both devices", true),
    CONNECTED("Connected — Show 🤟 THREE_GUN to send transfer offer", true),
    TRANSFER_OFFERED("Transfer Offer Initiated — Preparing offer...", true),
    WAITING_FOR_RECEIVER("Waiting for Receiver Consent...", true),
    TRANSFERRING("Transferring Payload Over Nearby P2P...", true),
    VERIFYING("Verifying SHA-256 Checksum on Receiver...", true),
    COMPLETED("Transfer Verified & Completed", false),
    CANCELLED("Session Cancelled", false),
    FAILED("Session Failed", false)
}
