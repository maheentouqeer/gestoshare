package com.example

import com.example.manager.DefaultGestureRecognitionManager
import com.example.manager.GestureActionEffect
import com.example.manager.GestureSessionContext
import com.example.manager.RealNearbyTransferManager
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.DeviceType
import com.example.model.GestureOperationMode
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.google.android.gms.nearby.connection.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiDeviceGestureTest {

    private fun makeDevice(id: String, name: String, connected: Boolean = true): DeviceItem =
        DeviceItem(
            endpointId = id,
            name = name,
            deviceType = DeviceType.PHONE,
            connectionState = if (connected) DeviceConnectionState.CONNECTED else DeviceConnectionState.DISCOVERED
        )

    private val stagedSample = listOf(
        ContentItem(
            id = "doc-1",
            name = "lab_report.pdf",
            type = ContentType.DOCUMENT_PDF,
            mimeType = "application/pdf",
            sizeBytes = 240_000L,
            sha256Hash = "a".repeat(64)
        )
    )

    @Test
    fun `Nearby uses Strategy P2P_CLUSTER for multi-peer discovery and connections`() {
        assertEquals(Strategy.P2P_CLUSTER, RealNearbyTransferManager.STRATEGY)
    }

    @Test
    fun `POINT gesture cycles deterministically across 0, 1, 3, and 5 connected devices`() {
        val manager = DefaultGestureRecognitionManager()
        manager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)

        // 0 peers -> informative feedback, stays CONTENT_HELD
        val emptyCtx = GestureSessionContext(
            stagedItems = stagedSample,
            discoveredDevices = emptyList(),
            connectedDevices = emptyList()
        )
        manager.processCommandGesture(GestureType.LIKE, 0.94f, emptyCtx)
        manager.processCommandGesture(GestureType.FIST, 0.94f, emptyCtx)
        val zeroPoint = manager.processCommandGesture(GestureType.POINT, 0.94f, emptyCtx)
        assertTrue(zeroPoint is GestureActionEffect.None)
        assertTrue(manager.sessionFeedback.value?.contains("No nearby devices", ignoreCase = true) == true)

        // 1 connected peer -> locks onto single peer even on repeated POINT
        val onePeer = makeDevice("ep-1", "Redmi 1")
        val oneCtx = GestureSessionContext(
            stagedItems = stagedSample,
            discoveredDevices = listOf(onePeer),
            connectedDevice = onePeer,
            connectedDevices = listOf(onePeer)
        )
        val oneEffect = manager.processCommandGesture(GestureType.POINT, 0.94f, oneCtx)
        assertEquals(onePeer, (oneEffect as GestureActionEffect.SelectPeerDevice).device)

        // 3 connected peers -> POINT cycles ep-1 -> ep-2 -> ep-3 -> ep-1 deterministically
        val peers3 = listOf(
            makeDevice("ep-1", "Redmi 1"),
            makeDevice("ep-2", "Redmi 2"),
            makeDevice("ep-3", "Infinix 1")
        )
        var selected: DeviceItem? = peers3[0]
        val cycleResults = mutableListOf<String>()
        repeat(4) {
            val ctx3 = GestureSessionContext(
                stagedItems = stagedSample,
                discoveredDevices = peers3,
                selectedDevice = selected,
                connectedDevice = peers3[0],
                connectedDevices = peers3
            )
            val eff = manager.processCommandGesture(GestureType.POINT, 0.95f, ctx3) as GestureActionEffect.SelectPeerDevice
            selected = eff.device
            cycleResults.add(eff.device.endpointId)
        }
        assertEquals(listOf("ep-2", "ep-3", "ep-1", "ep-2"), cycleResults)

        // 5 peers (3 connected, 2 discovered-only) -> POINT prioritizes connected peers only
        val peers5Discovered = peers3 + listOf(
            makeDevice("ep-4", "Pixel Discovered", connected = false),
            makeDevice("ep-5", "Samsung Discovered", connected = false)
        )
        val ctx5 = GestureSessionContext(
            stagedItems = stagedSample,
            discoveredDevices = peers5Discovered,
            selectedDevice = peers3[2],
            connectedDevice = peers3[0],
            connectedDevices = peers3
        )
        val wrapToConnected = manager.processCommandGesture(GestureType.POINT, 0.95f, ctx5) as GestureActionEffect.SelectPeerDevice
        assertEquals("ep-1", wrapToConnected.device.endpointId)
    }

    @Test
    fun `partial disconnect during cluster session preserves workflow while remaining peer exists`() {
        val manager = DefaultGestureRecognitionManager()
        manager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)

        val peer1 = makeDevice("ep-1", "Redmi 1")
        val peer2 = makeDevice("ep-2", "Infinix 1")

        val ctxTwo = GestureSessionContext(
            stagedItems = stagedSample,
            discoveredDevices = listOf(peer1, peer2),
            selectedDevice = peer1,
            connectedDevice = peer1,
            connectedDevices = listOf(peer1, peer2)
        )
        manager.processCommandGesture(GestureType.LIKE, 0.95f, ctxTwo)
        manager.processCommandGesture(GestureType.FIST, 0.95f, ctxTwo)
        manager.processCommandGesture(GestureType.POINT, 0.95f, ctxTwo)

        // peer2 removed while peer1 remains connected and fallback selection cleared -> session remains CONNECTED
        val ctxOneLeft = GestureSessionContext(
            stagedItems = stagedSample,
            discoveredDevices = listOf(peer1),
            selectedDevice = null,
            connectedDevice = peer1,
            connectedDevices = listOf(peer1)
        )
        manager.syncWithSystemState(ctxOneLeft)
        assertEquals(GestureSessionState.CONNECTED, manager.sessionState.value)

        // Now last connected peer disconnects while in TRANSFER_OFFERED -> FAILED
        val ctxOffer = ctxOneLeft.copy(selectedDevice = peer1)
        manager.processCommandGesture(GestureType.POINT, 0.95f, ctxOffer)
        manager.processCommandGesture(GestureType.THREE_GUN, 0.95f, ctxOffer)
        assertEquals(GestureSessionState.TRANSFER_OFFERED, manager.sessionState.value)

        manager.syncWithSystemState(
            ctxOffer.copy(connectedDevice = null, connectedDevices = emptyList())
        )
        assertEquals(GestureSessionState.FAILED, manager.sessionState.value)
        assertTrue(manager.sessionFeedback.value?.contains("Connection lost", ignoreCase = true) == true)
    }

    @Test
    fun `THREE_GUN gesture dispatches to multi-selected authenticated recipients in group mode`() {
        val manager = DefaultGestureRecognitionManager()
        manager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)

        val p1 = makeDevice("ep-1", "Redmi 1")
        val p2 = makeDevice("ep-2", "Redmi 2")
        val p3 = makeDevice("ep-3", "Infinix 1")

        val multiCtx = GestureSessionContext(
            stagedItems = stagedSample,
            discoveredDevices = listOf(p1, p2, p3),
            selectedDevice = p1,
            connectedDevice = p1,
            connectedDevices = listOf(p1, p2, p3),
            multiSelectRecipients = listOf(p1, p3),
            isMultiSelectMode = true
        )

        manager.processCommandGesture(GestureType.LIKE, 0.95f, multiCtx)
        manager.processCommandGesture(GestureType.FIST, 0.95f, multiCtx)
        val effect = manager.processCommandGesture(GestureType.THREE_GUN, 0.95f, multiCtx)
        assertTrue(effect is GestureActionEffect.DispatchTransferOffer)
        val dispatch = effect as GestureActionEffect.DispatchTransferOffer
        assertEquals(listOf("ep-1", "ep-3"), dispatch.targetDevices.map { it.endpointId })
    }
}
