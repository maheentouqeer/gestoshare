package com.example.model

enum class DeviceType {
    PHONE,
    TABLET,
    LAPTOP,
    UNKNOWN
}

enum class DeviceConnectionState(val badgeLabel: String) {
    DISCOVERED("DISCOVERED"),
    CONNECTING("CONNECTING"),
    AUTHENTICATING("AUTHENTICATING"),
    CONNECTED("CONNECTED"),
    DISCONNECTED("DISCONNECTED"),
    UNAVAILABLE("UNAVAILABLE"),
    FAILED("FAILED")
}

/**
 * Represents an actual discovered peer device from Google Nearby Connections.
 * Never fabricates radar coordinates or fake signal angles.
 */
data class DeviceItem(
    val endpointId: String,
    val name: String,
    val deviceType: DeviceType = DeviceType.PHONE,
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCOVERED,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val statusDetail: String? = null
) {
    val id: String get() = endpointId

    val isConnected: Boolean get() = connectionState == DeviceConnectionState.CONNECTED
    val isBusyConnecting: Boolean
        get() = connectionState == DeviceConnectionState.CONNECTING ||
            connectionState == DeviceConnectionState.AUTHENTICATING
}

/**
 * Remembered history of previously paired peers.
 * Never auto-trusts connections; users still verify the 6-digit Nearby security code.
 */
data class RecentDeviceRecord(
    val deviceKey: String,
    val displayName: String,
    val lastEndpointId: String,
    val lastConnectedAt: Long,
    val connectionCount: Int
)
