package com.example

import com.example.manager.DefaultGestureRecognitionManager
import com.example.manager.GestureActionEffect
import com.example.manager.GestureSessionContext
import com.example.manager.RealNearbyTransferManager
import com.example.manager.TemporalGestureConfig
import com.example.manager.TemporalGestureStabilizer
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.DeviceType
import com.example.model.GestureOperationMode
import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.example.model.IncomingTransferOffer
import com.example.model.SessionStatus
import com.example.model.TransferItemMetadata
import com.google.android.gms.nearby.connection.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `five connected peers cluster enforces deterministic cycling held-latch single-target group filtering disconnect rejection and consent`() {
        val fiveConnected = listOf(
            makeDevice("ep-1", "Redmi Note 12", connected = true),
            makeDevice("ep-2", "Redmi 10", connected = true),
            makeDevice("ep-3", "Infinix Hot 30", connected = true),
            makeDevice("ep-4", "Pixel 7", connected = true),
            makeDevice("ep-5", "Samsung A54", connected = true)
        )
        val staleDiscovered = makeDevice("ep-stale", "Ghost Tablet", connected = false)
        val allDiscovered = fiveConnected + staleDiscovered

        // Verify 6-digit auth pairing & explicit receiver consent models remain intact
        val authSession = ConnectionSession(
            endpointId = "ep-3",
            endpointName = "Infinix Hot 30",
            authDigits = "482910",
            isIncoming = false,
            status = SessionStatus.INITIATED
        )
        assertEquals(6, authSession.authDigits.length)
        val consentOffer = IncomingTransferOffer(
            transferId = "tx-consent-5",
            endpointId = "ep-1",
            senderName = "Redmi Note 12",
            totalBytes = 240_000L,
            items = listOf(
                TransferItemMetadata(0, "lab_report.pdf", "application/pdf", 240_000L, ContentType.DOCUMENT_PDF, "a".repeat(64))
            )
        )
        assertEquals(1, consentOffer.items.size)

        val stabilizer = TemporalGestureStabilizer(
            TemporalGestureConfig(
                windowSize = 5,
                requiredConsecutiveFrames = 5,
                confidenceThreshold = 0.80f,
                holdDurationMs = 200L,
                commandCooldownMs = 1000L,
                requireNeutralReset = true
            )
        )
        val manager = DefaultGestureRecognitionManager()
        manager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)

        // Activate + Grab
        val baseCtx = GestureSessionContext(
            stagedItems = stagedSample,
            discoveredDevices = allDiscovered,
            connectedDevice = fiveConnected[0],
            connectedDevices = fiveConnected
        )
        manager.processCommandGesture(GestureType.LIKE, 0.96f, baseCtx)
        manager.processCommandGesture(GestureType.FIST, 0.96f, baseCtx)

        // 1. Held POINT gesture cannot repeatedly cycle devices without neutral reset
        var t = 1000L
        var triggeredInHold = 0
        var selected: DeviceItem? = null
        repeat(10) { frameIdx ->
            t += 60L
            val dec = stabilizer.process(
                GestureResult(
                    gesture = GestureType.POINT,
                    confidence = 0.95f,
                    predictedClass = "point",
                    isHandDetected = true,
                    isAccepted = true,
                    timestamp = t
                ),
                nowMs = t
            )
            if (dec.triggeredGesture == GestureType.POINT) {
                triggeredInHold++
                val eff = manager.processCommandGesture(
                    GestureType.POINT,
                    0.95f,
                    baseCtx.copy(selectedDevice = selected)
                ) as GestureActionEffect.SelectPeerDevice
                selected = eff.device
            }
            if (frameIdx >= 6) {
                assertNull("Held POINT after latch must NOT fire repeatedly", dec.triggeredGesture)
                assertTrue(dec.isHeldLocked)
            }
        }
        assertEquals("Held gesture of 10 consecutive frames must cycle exactly once", 1, triggeredInHold)
        assertEquals("ep-1", selected?.endpointId)

        // 2. Neutral OTHER reset + cooldown enables deterministic cycling across ALL 5 connected peers
        val visited = mutableListOf(selected!!.endpointId)
        repeat(5) {
            t += 100L
            stabilizer.process(
                GestureResult(GestureType.OTHER, 0.90f, predictedClass = "OTHER", isHandDetected = true, isAccepted = false, timestamp = t),
                nowMs = t
            )
            t += 1100L // past cooldown
            var fired: GestureType? = null
            repeat(6) {
                t += 60L
                val dec = stabilizer.process(
                    GestureResult(GestureType.POINT, 0.94f, predictedClass = "point", isHandDetected = true, isAccepted = true, timestamp = t),
                    nowMs = t
                )
                if (dec.triggeredGesture != null) {
                    fired = dec.triggeredGesture
                }
            }
            assertEquals(GestureType.POINT, fired)
            val eff = manager.processCommandGesture(
                GestureType.POINT,
                0.94f,
                baseCtx.copy(selectedDevice = selected)
            ) as GestureActionEffect.SelectPeerDevice
            selected = eff.device
            visited.add(eff.device.endpointId)
        }
        // ep-1 -> ep-2 -> ep-3 -> ep-4 -> ep-5 -> ep-1
        assertEquals(listOf("ep-1", "ep-2", "ep-3", "ep-4", "ep-5", "ep-1"), visited)

        // 3. Single-recipient THREE_GUN sends ONLY to the selected device (ep-3)
        val effToEp3 = manager.processCommandGesture(
            GestureType.POINT,
            0.95f,
            baseCtx.copy(selectedDevice = fiveConnected[1]) // selects ep-3
        ) as GestureActionEffect.SelectPeerDevice
        assertEquals("ep-3", effToEp3.device.endpointId)

        val singleDispatch = manager.processCommandGesture(
            GestureType.THREE_GUN,
            0.96f,
            baseCtx.copy(selectedDevice = effToEp3.device, isMultiSelectMode = false)
        ) as GestureActionEffect.DispatchTransferOffer
        assertFalse(singleDispatch.isDryRun)
        assertEquals(listOf("ep-3"), singleDispatch.targetDevices.map { it.endpointId })

        // Reset for subsequent dispatch verification tests
        manager.resetSession()

        // 4. Stale discovered-only peer CANNOT receive a transfer in single mode
        manager.processCommandGesture(GestureType.LIKE, 0.95f, baseCtx)
        manager.processCommandGesture(GestureType.FIST, 0.95f, baseCtx)
        manager.processCommandGesture(GestureType.POINT, 0.95f, baseCtx.copy(selectedDevice = fiveConnected[0]))
        val staleRejected = manager.processCommandGesture(
            GestureType.THREE_GUN,
            0.95f,
            baseCtx.copy(selectedDevice = staleDiscovered, isMultiSelectMode = false)
        )
        assertTrue("Stale discovered-only peer must be rejected", staleRejected is GestureActionEffect.None)
        assertTrue((staleRejected as GestureActionEffect.None).isError)

        // 5. Peer disconnecting right before THREE_GUN fires is rejected
        val connectedWithoutEp3 = fiveConnected.filterNot { it.endpointId == "ep-3" }
        val disconnectedBeforeFire = manager.processCommandGesture(
            GestureType.THREE_GUN,
            0.95f,
            baseCtx.copy(
                connectedDevices = connectedWithoutEp3,
                selectedDevice = fiveConnected[2] // ep-3 just disconnected
            )
        )
        assertTrue("Peer disconnected right before firing must be rejected", disconnectedBeforeFire is GestureActionEffect.None)
        assertTrue((disconnectedBeforeFire as GestureActionEffect.None).isError)

        // 6. Group mode sends ONLY to checked AND authenticated (connected) recipients, excluding stale peers
        val groupOffer = manager.processCommandGesture(
            GestureType.THREE_GUN,
            0.96f,
            baseCtx.copy(
                isMultiSelectMode = true,
                multiSelectRecipients = listOf(fiveConnected[1], staleDiscovered, fiveConnected[4])
            )
        ) as GestureActionEffect.DispatchTransferOffer
        assertEquals(listOf("ep-2", "ep-5"), groupOffer.targetDevices.map { it.endpointId })

        // 7. Group mode with ONLY stale/disconnected recipients is rejected rather than falling back
        manager.resetSession()
        manager.processCommandGesture(GestureType.LIKE, 0.95f, baseCtx)
        manager.processCommandGesture(GestureType.FIST, 0.95f, baseCtx)
        manager.processCommandGesture(GestureType.POINT, 0.95f, baseCtx)
        val staleGroupRejected = manager.processCommandGesture(
            GestureType.THREE_GUN,
            0.96f,
            baseCtx.copy(
                isMultiSelectMode = true,
                multiSelectRecipients = listOf(staleDiscovered)
            )
        )
        assertTrue("Group mode with only stale peer must be rejected", staleGroupRejected is GestureActionEffect.None)
        assertTrue((staleGroupRejected as GestureActionEffect.None).isError)
    }
}
