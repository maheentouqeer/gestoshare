package com.example.model

enum class DeviceType {
    PHONE,
    TABLET,
    LAPTOP,
    UNKNOWN
}

enum class DeviceConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    FAILED,
    DISCONNECTED
}

/**
 * Represents an actual discovered peer device from Google Nearby Connections.
 * No fabricated angles or radar coordinates are stored here.
 */
data class DeviceItem(
    val endpointId: String,
    val name: String,
    val deviceType: DeviceType = DeviceType.PHONE,
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCOVERED,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    // Backwards-compatible ID getter pointing directly to the real endpoint ID
    val id: String get() = endpointId
}
