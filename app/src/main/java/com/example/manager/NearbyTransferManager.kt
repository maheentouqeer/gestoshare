package com.example.manager

import com.example.model.ActiveTransfer
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.DeviceItem
import com.example.model.IncomingTransferOffer
import com.example.model.NearbyMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining peer discovery, connection authentication, real P2P file/stream transfer,
 * SHA-256 verification, and communication.
 */
interface NearbyTransferManager {
    val discoveredDevices: StateFlow<List<DeviceItem>>
    val isDiscovering: StateFlow<Boolean>
    val isAdvertising: StateFlow<Boolean>
    val pendingSession: StateFlow<ConnectionSession?>
    val connectedDevice: StateFlow<DeviceItem?>
    val receivedMessages: StateFlow<List<NearbyMessage>>
    val incomingTransferOffer: StateFlow<IncomingTransferOffer?>
    val activeTransfer: StateFlow<ActiveTransfer?>
    val statusMessage: StateFlow<String>
    val isNearbyAvailable: StateFlow<Boolean>

    fun setOnTransferFinishedListener(listener: (ActiveTransfer) -> Unit)

    fun startDiscovery(myDeviceName: String)
    fun stopDiscovery()
    fun startAdvertising(myDeviceName: String)
    fun stopAdvertising()
    fun toggleAdvertising(myDeviceName: String)

    fun requestConnection(device: DeviceItem, myDeviceName: String)
    fun acceptConnection(endpointId: String)
    fun rejectConnection(endpointId: String)
    fun disconnectDevice(endpointId: String)
    fun stopAllEndpoints()

    fun sendTextMessage(message: String): Boolean
    fun sendPingTest(): Boolean

    fun acceptTransferOffer(transferId: String)
    fun rejectTransferOffer(transferId: String)

    fun connectDevice(device: DeviceItem) {
        requestConnection(device, "GestoShare Device")
    }
    fun retryDeviceConnection(deviceId: String)
    fun startTransfer(
        targetDevice: DeviceItem,
        items: List<ContentItem>,
        myDeviceName: String,
        onComplete: (ActiveTransfer) -> Unit
    )
    fun cancelTransfer()
    fun retryTransfer()
}
