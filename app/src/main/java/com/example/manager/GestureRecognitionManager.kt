package com.example.manager

import com.example.model.ActiveTransfer
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceItem
import com.example.model.GestureOperationMode
import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.example.model.SessionStatus
import com.example.model.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GestureSessionContext(
    val stagedItems: List<ContentItem> = emptyList(),
    val discoveredDevices: List<DeviceItem> = emptyList(),
    val selectedDevice: DeviceItem? = null,
    val connectedDevice: DeviceItem? = null,
    val connectedDevices: List<DeviceItem> = emptyList(),
    val pendingSession: ConnectionSession? = null,
    val activeTransfer: ActiveTransfer? = null,
    val multiSelectRecipients: List<DeviceItem> = emptyList(),
    val isMultiSelectMode: Boolean = false,
    val isModelAvailable: Boolean = true,
    val modelUnavailableReason: String? = null
)

sealed class GestureActionEffect {
    data class None(val reason: String, val isError: Boolean = false) : GestureActionEffect()
    data class GrabContentConfirmed(val itemCount: Int) : GestureActionEffect()
    data class SelectPeerDevice(val device: DeviceItem, val isAlreadyConnected: Boolean) : GestureActionEffect()
    data class DispatchTransferOffer(
        val targetDevice: DeviceItem,
        val targetDevices: List<DeviceItem> = listOf(targetDevice),
        val items: List<ContentItem>,
        val isDryRun: Boolean
    ) : GestureActionEffect()
    data class CancelActiveWorkflow(val reason: String) : GestureActionEffect()
}

interface GestureRecognitionManager {
    val sessionState: StateFlow<GestureSessionState>
    val currentGesture: StateFlow<GestureResult>
    val isTrackingHand: StateFlow<Boolean>
    val operationMode: StateFlow<GestureOperationMode>
    val sessionFeedback: StateFlow<String?>
    val commandLogs: StateFlow<List<String>>
    val isContentGrabbed: StateFlow<Boolean>
    val classifierInfo: String

    fun setOperationMode(mode: GestureOperationMode)
    fun clearFeedback()
    fun startSession()
    fun stopSession()
    fun resetSession()
    fun bindActiveTransferId(transferId: String?)
    fun syncWithSystemState(context: GestureSessionContext)
    fun processCommandGesture(
        gesture: GestureType,
        confidence: Float,
        context: GestureSessionContext
    ): GestureActionEffect

    fun simulateGestureForTesting(gesture: GestureType, confidence: Float = 0.95f)
}

/**
 * Full Phase 8–10 gesture session state machine integrating temporal gesture commands
 * with staged content, multi-device Nearby peer cycling (`POINT`), 6-digit pairing handshake,
 * optional multi-recipient group dispatch (`THREE_GUN`), and verified P2P transfer lifecycle.
 */
class DefaultGestureRecognitionManager(
    private val classifier: GestureClassifier = HaGridV2OnnxClassifierPlaceholder()
) : GestureRecognitionManager {

    private val lock = Any()

    private val _sessionState = MutableStateFlow(GestureSessionState.IDLE)
    override val sessionState: StateFlow<GestureSessionState> = _sessionState.asStateFlow()

    private val _currentGesture = MutableStateFlow(GestureResult(GestureType.NONE, 0.0f))
    override val currentGesture: StateFlow<GestureResult> = _currentGesture.asStateFlow()

    private val _isTrackingHand = MutableStateFlow(false)
    override val isTrackingHand: StateFlow<Boolean> = _isTrackingHand.asStateFlow()

    private val _operationMode = MutableStateFlow(GestureOperationMode.DEBUG_DRY_RUN)
    override val operationMode: StateFlow<GestureOperationMode> = _operationMode.asStateFlow()

    private val _sessionFeedback = MutableStateFlow<String?>(null)
    override val sessionFeedback: StateFlow<String?> = _sessionFeedback.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<String>>(emptyList())
    override val commandLogs: StateFlow<List<String>> = _commandLogs.asStateFlow()

    private val _isContentGrabbed = MutableStateFlow(false)
    override val isContentGrabbed: StateFlow<Boolean> = _isContentGrabbed.asStateFlow()

    override val classifierInfo: String
        get() = classifier.getModelInfo()

    private var offerDispatchedForSession: Boolean = false
    private var trackedTransferId: String? = null
    private var wasConnectedDuringSession: Boolean = false

    override fun setOperationMode(mode: GestureOperationMode) {
        synchronized(lock) {
            _operationMode.value = mode
            appendLog("Mode switched to ${mode.label}")
            _sessionFeedback.value = "${mode.label}: ${mode.subtitle}"
        }
    }

    override fun clearFeedback() {
        _sessionFeedback.value = null
    }

    override fun startSession() {
        synchronized(lock) {
            val state = _sessionState.value
            if (state == GestureSessionState.IDLE ||
                state == GestureSessionState.CANCELLED ||
                state == GestureSessionState.FAILED ||
                state == GestureSessionState.COMPLETED
            ) {
                _sessionState.value = GestureSessionState.ACTIVATED
                offerDispatchedForSession = false
                wasConnectedDuringSession = false
                trackedTransferId = null
                appendLog("Session manually activated -> ACTIVATED")
            }
        }
    }

    override fun stopSession() {
        synchronized(lock) {
            _sessionState.value = GestureSessionState.IDLE
            _currentGesture.value = GestureResult(GestureType.NONE, 0f)
            _isTrackingHand.value = false
            _isContentGrabbed.value = false
            offerDispatchedForSession = false
            wasConnectedDuringSession = false
            trackedTransferId = null
        }
    }

    override fun resetSession() {
        synchronized(lock) {
            _sessionState.value = GestureSessionState.IDLE
            _currentGesture.value = GestureResult(GestureType.NONE, 0f)
            _isTrackingHand.value = false
            _isContentGrabbed.value = false
            _sessionFeedback.value = null
            offerDispatchedForSession = false
            wasConnectedDuringSession = false
            trackedTransferId = null
            appendLog("Session reset -> IDLE")
        }
    }

    override fun bindActiveTransferId(transferId: String?) {
        synchronized(lock) {
            trackedTransferId = transferId
        }
    }

    override fun processCommandGesture(
        gesture: GestureType,
        confidence: Float,
        context: GestureSessionContext
    ): GestureActionEffect {
        synchronized(lock) {
            _isTrackingHand.value = (gesture != GestureType.NONE)
            _currentGesture.value = GestureResult(
                gesture = gesture,
                confidence = confidence,
                predictedClass = gesture.name.lowercase(Locale.US),
                isHandDetected = (gesture != GestureType.NONE),
                isAccepted = (gesture != GestureType.NONE && gesture != GestureType.OTHER)
            )

            val current = _sessionState.value
            val allConnected = context.connectedDevices.ifEmpty {
                listOfNotNull(context.connectedDevice)
            }

            return when (gesture) {
                GestureType.NONE, GestureType.OTHER -> {
                    GestureActionEffect.None("Neutral / no action")
                }

                GestureType.STOP -> {
                    if (current.isActiveSession) {
                        _sessionState.value = GestureSessionState.CANCELLED
                        _isContentGrabbed.value = false
                        offerDispatchedForSession = false
                        wasConnectedDuringSession = false
                        val reason = "Session & active transfer cancelled via STOP gesture"
                        _sessionFeedback.value = reason
                        appendLog("STOP (${current.name} -> CANCELLED)")
                        GestureActionEffect.CancelActiveWorkflow(reason)
                    } else {
                        val msg = "STOP ignored (no active session in ${current.name})"
                        _sessionFeedback.value = msg
                        GestureActionEffect.None(msg)
                    }
                }

                GestureType.LIKE -> {
                    if (current == GestureSessionState.IDLE ||
                        current == GestureSessionState.CANCELLED ||
                        current == GestureSessionState.FAILED ||
                        current == GestureSessionState.COMPLETED
                    ) {
                        _sessionState.value = GestureSessionState.ACTIVATED
                        _isContentGrabbed.value = false
                        offerDispatchedForSession = false
                        wasConnectedDuringSession = false
                        trackedTransferId = null
                        val msg = "Gesture session activated. Show FIST to grab staged content."
                        _sessionFeedback.value = msg
                        appendLog("LIKE ($current -> ACTIVATED)")
                        GestureActionEffect.None(msg)
                    } else {
                        val msg = "LIKE ignored: session already active ($current)"
                        appendLog(msg)
                        GestureActionEffect.None(msg)
                    }
                }

                GestureType.FIST -> {
                    if (current != GestureSessionState.ACTIVATED) {
                        val msg = when (current) {
                            GestureSessionState.IDLE,
                            GestureSessionState.CANCELLED,
                            GestureSessionState.FAILED,
                            GestureSessionState.COMPLETED -> "Show LIKE first to activate the gesture session."
                            else -> "Content already grabbed ($current)."
                        }
                        _sessionFeedback.value = msg
                        appendLog("FIST rejected in $current")
                        GestureActionEffect.None(msg, isError = current == GestureSessionState.IDLE)
                    } else if (context.stagedItems.isEmpty()) {
                        val msg = "No content staged. Select files, text, or a URL first."
                        _sessionFeedback.value = msg
                        appendLog("FIST blocked: 0 staged items")
                        GestureActionEffect.None(msg, isError = true)
                    } else {
                        _isContentGrabbed.value = true
                        _sessionState.value = GestureSessionState.CONTENT_HELD
                        val msg = "Grabbed ${context.stagedItems.size} staged item(s). Show POINT to select peer."
                        _sessionFeedback.value = msg
                        appendLog("FIST -> CONTENT_HELD (${context.stagedItems.size} item(s))")
                        GestureActionEffect.GrabContentConfirmed(context.stagedItems.size)
                    }
                }

                GestureType.POINT -> {
                    if (current != GestureSessionState.CONTENT_HELD &&
                        current != GestureSessionState.TARGET_SELECTED &&
                        current != GestureSessionState.CONNECTED
                    ) {
                        val msg = when (current) {
                            GestureSessionState.IDLE,
                            GestureSessionState.CANCELLED,
                            GestureSessionState.FAILED,
                            GestureSessionState.COMPLETED -> "Activate session with LIKE and grab content with FIST first."
                            GestureSessionState.ACTIVATED -> "Show FIST to grab staged content before selecting a peer."
                            else -> "POINT ignored in state $current."
                        }
                        _sessionFeedback.value = msg
                        appendLog("POINT rejected in $current")
                        GestureActionEffect.None(msg, isError = current == GestureSessionState.IDLE || current == GestureSessionState.ACTIVATED)
                    } else {
                        // Prefer cycling connected devices when >=1 peer is connected; fall back to discovered peers
                        val pool = if (allConnected.isNotEmpty()) allConnected else context.discoveredDevices
                        if (pool.isEmpty()) {
                            val msg = "No nearby devices discovered. Enable Scan Nearby & Receive Mode on peer."
                            _sessionFeedback.value = msg
                            appendLog("POINT blocked: 0 discovered peers")
                            GestureActionEffect.None(msg, isError = true)
                        } else {
                            val currentIndex = pool.indexOfFirst { it.endpointId == context.selectedDevice?.endpointId }
                            val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % pool.size
                            val chosen = pool[nextIndex]

                            val alreadyConnected = allConnected.any { it.endpointId == chosen.endpointId }
                            val isHandshakePending = context.pendingSession?.endpointId == chosen.endpointId &&
                                (context.pendingSession.status == SessionStatus.INITIATED ||
                                    context.pendingSession.status == SessionStatus.ACCEPTED_LOCALLY)

                            val nextState = when {
                                alreadyConnected -> {
                                    wasConnectedDuringSession = true
                                    GestureSessionState.CONNECTED
                                }
                                isHandshakePending -> GestureSessionState.CONNECTION_PENDING
                                else -> GestureSessionState.TARGET_SELECTED
                            }
                            _sessionState.value = nextState

                            val positionLabel = if (pool.size == 1 && alreadyConnected) {
                                "Only connected device"
                            } else {
                                "${nextIndex + 1} of ${pool.size}"
                            }
                            val msg = if (alreadyConnected) {
                                "Selected ${chosen.name} ($positionLabel). Show THREE_GUN to send."
                            } else {
                                "Selected '${chosen.name}' (${chosen.endpointId}). Accept Nearby pairing to connect."
                            }
                            _sessionFeedback.value = msg
                            appendLog("POINT -> $nextState (${chosen.name} [$positionLabel])")
                            GestureActionEffect.SelectPeerDevice(chosen, alreadyConnected)
                        }
                    }
                }

                GestureType.THREE_GUN -> {
                    if (context.stagedItems.isEmpty()) {
                        val msg = "No content staged. Select files, text, or a URL first."
                        _sessionFeedback.value = msg
                        appendLog("THREE_GUN blocked: no staged content")
                        return GestureActionEffect.None(msg, isError = true)
                    }

                    val groupTargets = if (context.isMultiSelectMode && context.multiSelectRecipients.isNotEmpty()) {
                        context.multiSelectRecipients.filter { candidate ->
                            allConnected.any { it.endpointId == candidate.endpointId }
                        }
                    } else {
                        emptyList()
                    }

                    if (context.isMultiSelectMode) {
                        if (groupTargets.isEmpty()) {
                            val msg = "No connected group recipients selected. Verify peer connections first."
                            _sessionFeedback.value = msg
                            appendLog("THREE_GUN blocked: 0 connected group recipients")
                            return GestureActionEffect.None(msg, isError = true)
                        }
                    }

                    val singleTarget = context.selectedDevice?.let { sel ->
                        allConnected.find { it.endpointId == sel.endpointId } ?: sel
                    } ?: allConnected.firstOrNull()

                    if (singleTarget == null && groupTargets.isEmpty()) {
                        val msg = "No connected recipient selected. Point to choose a device."
                        _sessionFeedback.value = msg
                        appendLog("THREE_GUN blocked: no target device")
                        return GestureActionEffect.None(msg, isError = true)
                    }

                    if (offerDispatchedForSession ||
                        current == GestureSessionState.TRANSFER_OFFERED ||
                        current == GestureSessionState.WAITING_FOR_RECEIVER ||
                        current == GestureSessionState.TRANSFERRING ||
                        current == GestureSessionState.VERIFYING
                    ) {
                        val msg = "Duplicate transfer request blocked: offer already active."
                        _sessionFeedback.value = msg
                        appendLog("THREE_GUN blocked: duplicate offer in $current")
                        return GestureActionEffect.None(msg, isError = true)
                    }

                    val activeStatus = context.activeTransfer?.status
                    if (activeStatus == TransferStatus.PREPARING ||
                        activeStatus == TransferStatus.AWAITING_ACCEPTANCE ||
                        activeStatus == TransferStatus.TRANSFERRING ||
                        activeStatus == TransferStatus.VERIFYING
                    ) {
                        val msg = "A transfer is already active (${activeStatus.label})."
                        _sessionFeedback.value = msg
                        appendLog("THREE_GUN blocked: activeTransfer=$activeStatus")
                        return GestureActionEffect.None(msg, isError = true)
                    }

                    val effectiveTargets = if (context.isMultiSelectMode) groupTargets else {
                        val t = singleTarget!!
                        val isConnectedToTarget = allConnected.any { it.endpointId == t.endpointId }
                        if (!isConnectedToTarget || current != GestureSessionState.CONNECTED) {
                            val msg = if (!isConnectedToTarget) {
                                "Receiver '${t.name}' is not connected yet. Complete Nearby pairing handshake first."
                            } else {
                                "THREE_GUN requires CONNECTED state (currently $current)."
                            }
                            _sessionFeedback.value = msg
                            appendLog("THREE_GUN blocked: not connected (state=$current, target=${t.endpointId})")
                            return GestureActionEffect.None(msg, isError = true)
                        }
                        listOf(t)
                    }

                    val primaryTarget = effectiveTargets.first()
                    val isDryRun = _operationMode.value == GestureOperationMode.DEBUG_DRY_RUN
                    offerDispatchedForSession = true
                    _sessionState.value = GestureSessionState.TRANSFER_OFFERED

                    val targetDesc = if (effectiveTargets.size > 1) {
                        "${effectiveTargets.size} selected peers (${effectiveTargets.joinToString { it.name }})"
                    } else {
                        "'${primaryTarget.name}'"
                    }
                    val msg = if (isDryRun) {
                        "DEBUG / DRY RUN: THREE_GUN recognized for $targetDesc — real Nearby send disabled."
                    } else {
                        "Sending real TRANSFER_OFFER to $targetDesc (receiver consent required)..."
                    }
                    _sessionFeedback.value = msg
                    appendLog(if (isDryRun) "THREE_GUN -> TRANSFER_OFFERED (DRY RUN)" else "THREE_GUN -> TRANSFER_OFFERED (REAL)")
                    GestureActionEffect.DispatchTransferOffer(
                        targetDevice = primaryTarget,
                        targetDevices = effectiveTargets,
                        items = context.stagedItems,
                        isDryRun = isDryRun
                    )
                }
            }
        }
    }

    override fun syncWithSystemState(context: GestureSessionContext) {
        synchronized(lock) {
            val current = _sessionState.value
            val selected = context.selectedDevice
            val allConnected = context.connectedDevices.ifEmpty { listOfNotNull(context.connectedDevice) }
            val connectedForTarget = if (selected != null) {
                allConnected.find { it.endpointId == selected.endpointId }
            } else {
                allConnected.firstOrNull()
            }
            val pending = context.pendingSession
            val active = context.activeTransfer

            if (context.stagedItems.isEmpty() && _isContentGrabbed.value) {
                _isContentGrabbed.value = false
                if (current == GestureSessionState.CONTENT_HELD || current == GestureSessionState.TARGET_SELECTED) {
                    _sessionState.value = GestureSessionState.ACTIVATED
                    _sessionFeedback.value = "Staged content cleared — select content and show FIST again."
                }
            }

            if (connectedForTarget != null) {
                wasConnectedDuringSession = true
                if (current == GestureSessionState.TARGET_SELECTED || current == GestureSessionState.CONNECTION_PENDING) {
                    _sessionState.value = GestureSessionState.CONNECTED
                    _sessionFeedback.value = "Connected to ${connectedForTarget.name}. Show THREE_GUN to send."
                    appendLog("Peer paired -> CONNECTED (${connectedForTarget.name})")
                }
            } else if (pending != null && (selected == null || pending.endpointId == selected.endpointId)) {
                when (pending.status) {
                    SessionStatus.INITIATED, SessionStatus.ACCEPTED_LOCALLY -> {
                        if (current == GestureSessionState.TARGET_SELECTED ||
                            current == GestureSessionState.ACTIVATED ||
                            current == GestureSessionState.CONTENT_HELD
                        ) {
                            _sessionState.value = GestureSessionState.CONNECTION_PENDING
                            _sessionFeedback.value = "Confirm pairing code ${pending.authDigits} on both phones."
                        }
                    }
                    SessionStatus.FAILED -> {
                        if (current == GestureSessionState.CONNECTION_PENDING || current == GestureSessionState.TARGET_SELECTED) {
                            _sessionState.value = GestureSessionState.FAILED
                            offerDispatchedForSession = false
                            _sessionFeedback.value = "Connection rejected or failed. Verify Bluetooth/Wi-Fi and retry."
                        }
                    }
                    else -> Unit
                }
            } else if (wasConnectedDuringSession && connectedForTarget == null) {
                if (current == GestureSessionState.CONNECTED ||
                    (_operationMode.value == GestureOperationMode.GESTURE_TRANSFER &&
                        (current == GestureSessionState.TRANSFER_OFFERED ||
                            current == GestureSessionState.WAITING_FOR_RECEIVER ||
                            current == GestureSessionState.TRANSFERRING ||
                            current == GestureSessionState.VERIFYING))
                ) {
                    wasConnectedDuringSession = false
                    offerDispatchedForSession = false
                    _sessionState.value = GestureSessionState.FAILED
                    _sessionFeedback.value = "Connection lost: selected recipient disconnected."
                    appendLog("Selected peer disconnected -> FAILED")
                }
            }

            if (active != null && (trackedTransferId == null || trackedTransferId == active.id)) {
                val shouldBindNew = trackedTransferId == null &&
                    (active.status == TransferStatus.PREPARING ||
                        active.status == TransferStatus.AWAITING_ACCEPTANCE ||
                        active.status == TransferStatus.TRANSFERRING ||
                        active.status == TransferStatus.VERIFYING) &&
                    current.isActiveSession
                if (shouldBindNew) {
                    trackedTransferId = active.id
                }

                if (trackedTransferId == active.id) {
                    when (active.status) {
                        TransferStatus.PREPARING -> {
                            if (current.isActiveSession) {
                                _sessionState.value = GestureSessionState.TRANSFER_OFFERED
                            }
                        }
                        TransferStatus.AWAITING_ACCEPTANCE -> {
                            _sessionState.value = GestureSessionState.WAITING_FOR_RECEIVER
                        }
                        TransferStatus.TRANSFERRING -> {
                            _sessionState.value = GestureSessionState.TRANSFERRING
                        }
                        TransferStatus.VERIFYING -> {
                            _sessionState.value = GestureSessionState.VERIFYING
                        }
                        TransferStatus.SUCCESS -> {
                            if (active.isIntegrityVerified) {
                                _sessionState.value = GestureSessionState.COMPLETED
                                _isContentGrabbed.value = false
                                offerDispatchedForSession = false
                                _sessionFeedback.value = "Transfer verified via SHA-256 and completed."
                                appendLog("Nearby SHA-256 verified -> COMPLETED")
                            } else {
                                _sessionState.value = GestureSessionState.FAILED
                                offerDispatchedForSession = false
                                _sessionFeedback.value = "Receiver did not confirm SHA-256 verification."
                            }
                        }
                        TransferStatus.CANCELLED -> {
                            _sessionState.value = GestureSessionState.CANCELLED
                            _isContentGrabbed.value = false
                            offerDispatchedForSession = false
                            _sessionFeedback.value = active.errorMessage ?: active.statusDetail ?: "Transfer cancelled."
                            appendLog("Nearby callback -> CANCELLED")
                        }
                        TransferStatus.FAILED -> {
                            _sessionState.value = GestureSessionState.FAILED
                            offerDispatchedForSession = false
                            _sessionFeedback.value = active.errorMessage ?: active.statusDetail ?: "Transfer failed."
                            appendLog("Nearby callback -> FAILED (${active.errorMessage})")
                        }
                    }
                }
            }
        }
    }

    override fun simulateGestureForTesting(gesture: GestureType, confidence: Float) {
        val dummyItem = ContentItem(
            name = "TestFile.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1024L,
            type = ContentType.DOCUMENT_PDF
        )
        val dummyDevice = DeviceItem(endpointId = "TEST_ENDPOINT", name = "Test Peer")
        processCommandGesture(
            gesture = gesture,
            confidence = confidence,
            context = GestureSessionContext(
                stagedItems = listOf(dummyItem),
                discoveredDevices = listOf(dummyDevice),
                selectedDevice = dummyDevice,
                connectedDevice = dummyDevice,
                connectedDevices = listOf(dummyDevice)
            )
        )
    }

    private fun appendLog(entry: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val updated = (listOf("$stamp · $entry") + _commandLogs.value).take(30)
        _commandLogs.value = updated
    }
}
