package com.example.model

enum class GestureType(
    val actionName: String,
    val description: String,
    val symbol: String
) {
    LIKE("Activate", "Initialize gesture session & wake system", "👍"),
    FIST("Grab", "Lock and stage selected files for transfer", "✊"),
    POINT("Select Device", "Aim toward destination peer device", "👉"),
    THREE_GUN("Release / Send", "Trigger wireless transmission", "🤟"),
    STOP("Cancel", "Abort current gesture or transfer", "✋"),
    NONE("Searching", "No registered hand gesture detected", "🔍")
}

data class GestureResult(
    val gesture: GestureType = GestureType.NONE,
    val confidence: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class GestureSessionState(val statusLabel: String) {
    IDLE("Ready for gesture activation"),
    ACTIVATED("Session Active — Show FIST to grab content"),
    CONTENT_HELD("Content Grabbed — POINT toward target device"),
    TARGET_LOCKED("Target Device Locked — Show THREE_GUN to send"),
    TRANSFERRING("Transfer In Progress"),
    COMPLETED("Transfer Successfully Completed"),
    CANCELLED("Session Cancelled")
}
