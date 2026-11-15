package com.example.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.DiagnosticsRepository
import com.example.model.ActiveTransfer
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.DeviceType
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticClassifier
import com.example.model.DiagnosticCodes
import com.example.model.IncomingTransferOffer
import com.example.model.NearbyMessage
import com.example.model.SessionStatus
import com.example.model.TransferDirection
import com.example.model.TransferItemMetadata
import com.example.model.TransferPipelineStage
import com.example.model.TransferStatus
import com.example.model.TransferVerificationException
import com.example.util.HashUtil
import com.example.util.PayloadManifestTracker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Production Nearby Connections manager using [Strategy.P2P_CLUSTER] for true multi-peer
 * discovery, concurrent authenticated connections, multi-recipient transfer orchestration,
 * ContentResolver URI scoped-storage payload reading (fixing Android Q+ EACCES),
 * streaming SHA-256 verification, MediaStore Downloads export, and Diagnostics telemetry.
 */
class RealNearbyTransferManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val diagnostics: DiagnosticsRepository? = null
) : NearbyTransferManager {

    companion object {
        private const val TAG = "RealNearbyTransfer"
        const val SERVICE_ID = "com.gestoshare.app.P2P_SERVICE"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
        private const val CTRL_PREFIX = "GESTOSHARE_CTRL:"
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    private val incomingStorage = IncomingPayloadStorage(context, diagnostics)

    private val _discoveredDevices = MutableStateFlow<List<DeviceItem>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceItem>> = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _pendingSession = MutableStateFlow<ConnectionSession?>(null)
    override val pendingSession: StateFlow<ConnectionSession?> = _pendingSession.asStateFlow()

    private val _pendingSessions = MutableStateFlow<List<ConnectionSession>>(emptyList())
    override val pendingSessions: StateFlow<List<ConnectionSession>> = _pendingSessions.asStateFlow()

    private val _connectedDevice = MutableStateFlow<DeviceItem?>(null)
    override val connectedDevice: StateFlow<DeviceItem?> = _connectedDevice.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<DeviceItem>>(emptyList())
    override val connectedDevices: StateFlow<List<DeviceItem>> = _connectedDevices.asStateFlow()

    private val _receivedMessages = MutableStateFlow<List<NearbyMessage>>(emptyList())
    override val receivedMessages: StateFlow<List<NearbyMessage>> = _receivedMessages.asStateFlow()

    private val _incomingTransferOffer = MutableStateFlow<IncomingTransferOffer?>(null)
    override val incomingTransferOffer: StateFlow<IncomingTransferOffer?> = _incomingTransferOffer.asStateFlow()

    private val _activeTransfer = MutableStateFlow<ActiveTransfer?>(null)
    override val activeTransfer: StateFlow<ActiveTransfer?> = _activeTransfer.asStateFlow()

    private val _endpointTransfers = MutableStateFlow<Map<String, ActiveTransfer>>(emptyMap())
    override val endpointTransfers: StateFlow<Map<String, ActiveTransfer>> = _endpointTransfers.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready for multi-peer P2P connection")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isNearbyAvailable = MutableStateFlow(true)
    override val isNearbyAvailable: StateFlow<Boolean> = _isNearbyAvailable.asStateFlow()

    private var currentDeviceName: String = "GestoShare Device"
    private var onTransferFinishedListener: ((ActiveTransfer) -> Unit)? = null

    // Multi-peer registries
    private val connectedDeviceMap = ConcurrentHashMap<String, DeviceItem>()
    private val pendingSessionMap = ConcurrentHashMap<String, ConnectionSession>()

    // Last transfer inputs for retryTransfer()
    private var lastTargetDevices: List<DeviceItem> = emptyList()
    private var lastTransferItems: List<ContentItem> = emptyList()
    private var lastOnComplete: ((ActiveTransfer) -> Unit)? = null

    private data class SendSession(
        val transferId: String,
        val endpointId: String,
        val targetDevice: DeviceItem,
        val items: List<ContentItem>,
        val senderName: String,
        val totalBytes: Long,
        val startTimeMs: Long,
        val shaSummary: String,
        val onComplete: (ActiveTransfer) -> Unit,
        val activePayloadIds: MutableSet<Long> = ConcurrentHashMap.newKeySet(),
        val payloadBytesProgress: ConcurrentHashMap<Long, Long> = ConcurrentHashMap(),
        val completedPayloads: MutableSet<Long> = ConcurrentHashMap.newKeySet(),
        var textBytesSent: Long = 0L,
        var expectedFilePayloadsCount: Int = 0,
        @Volatile var isCancelledOrFailed: Boolean = false
    )

    private data class ReceiveSession(
        val transferId: String,
        val endpointId: String,
        val senderName: String,
        val totalBytes: Long,
        val expectedItems: Map<Int, TransferItemMetadata>,
        val startTimeMs: Long,
        val manifestTracker: PayloadManifestTracker = PayloadManifestTracker(transferId),
        val payloadMetaByPayloadId: ConcurrentHashMap<Long, TransferItemMetadata> = ConcurrentHashMap(),
        val receivedFilePayloads: ConcurrentHashMap<Long, Payload> = ConcurrentHashMap(),
        val payloadBytesProgress: ConcurrentHashMap<Long, Long> = ConcurrentHashMap(),
        val verifiedItems: ConcurrentHashMap<Int, ContentItem> = ConcurrentHashMap(),
        val verifiedHashes: ConcurrentHashMap<Int, String> = ConcurrentHashMap(),
        var latestSavedUri: Uri? = null,
        var latestSavedMime: String? = null,
        var allMediaStoreExported: Boolean = true,
        var latestMediaStoreNote: String? = null,
        @Volatile var isFinishedOrCancelled: Boolean = false
    )

    private val sendSessionsByTransferId = ConcurrentHashMap<String, SendSession>()
    private val sendSessionsByPayloadId = ConcurrentHashMap<Long, SendSession>()
    private var pendingOfferReceived: IncomingTransferOffer? = null
    private var activeReceiveSession: ReceiveSession? = null

    init {
        checkGooglePlayServices()
    }

    override fun setOnTransferFinishedListener(listener: (ActiveTransfer) -> Unit) {
        onTransferFinishedListener = listener
    }

    private fun checkGooglePlayServices() {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        if (resultCode != ConnectionResult.SUCCESS) {
            _isNearbyAvailable.value = false
            val msg = "Google Play services unavailable or outdated (code $resultCode)."
            _statusMessage.value = msg
            Log.e(TAG, msg)
            diagnostics?.recordError(
                code = DiagnosticCodes.GS_NET_001,
                category = DiagnosticCategory.NEARBY,
                sourceComponent = TAG,
                operation = "checkGooglePlayServices",
                summary = msg,
                technicalMessage = "GoogleApiAvailability code=$resultCode"
            )
        } else {
            _isNearbyAvailable.value = true
        }
    }

    private fun syncPeerLists() {
        val connected = connectedDeviceMap.values.sortedBy { it.name.lowercase() }
        _connectedDevices.value = connected
        _connectedDevice.value = connected.firstOrNull()

        val sessions = pendingSessionMap.values
            .filter { it.status == SessionStatus.INITIATED || it.status == SessionStatus.ACCEPTED_LOCALLY }
            .sortedBy { it.endpointName.lowercase() }
        _pendingSessions.value = sessions
        _pendingSession.value = sessions.firstOrNull()
    }

    private fun updateTransferState(endpointId: String, transfer: ActiveTransfer) {
        _endpointTransfers.value = _endpointTransfers.value + (endpointId to transfer)
        _activeTransfer.value = transfer
    }

    // =========================================================================
    // PAYLOAD CALLBACK
    // =========================================================================
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val rawBytes = payload.asBytes() ?: return
                    val text = String(rawBytes, StandardCharsets.UTF_8)
                    if (text.startsWith(CTRL_PREFIX)) {
                        handleControlMessage(endpointId, text.removePrefix(CTRL_PREFIX))
                    } else {
                        handleChatTextMessage(endpointId, text)
                    }
                }

                Payload.Type.FILE -> {
                    val session = activeReceiveSession
                    if (session == null || session.isFinishedOrCancelled || session.endpointId != endpointId) {
                        Log.w(TAG, "Received unexpected FILE payload ${payload.id} from $endpointId; cleaning up.")
                        IncomingPayloadStorage.safeDeletePayload(context, payload)
                        return
                    }
                    session.receivedFilePayloads[payload.id] = payload
                    diagnostics?.recordLog(
                        category = DiagnosticCategory.TRANSFER,
                        tag = TAG,
                        message = "Incoming FILE payload registered (id=${payload.id}) from $endpointId"
                    )
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val sendSess = sendSessionsByPayloadId[update.payloadId]
                ?: sendSessionsByTransferId.values.find {
                    it.endpointId == endpointId && it.activePayloadIds.contains(update.payloadId)
                }
            if (sendSess != null) {
                handleSenderPayloadUpdate(sendSess, update)
                return
            }

            val recvSess = activeReceiveSession
            if (recvSess != null && recvSess.endpointId == endpointId &&
                (recvSess.receivedFilePayloads.containsKey(update.payloadId) ||
                    recvSess.manifestTracker.hasMetadata(update.payloadId))
            ) {
                handleReceiverPayloadUpdate(recvSess, update)
            }
        }
    }

    private fun handleChatTextMessage(endpointId: String, text: String) {
        val senderName = connectedDeviceMap[endpointId]?.name ?: endpointId
        if (text.startsWith("ACK: ")) {
            val ackMessage = NearbyMessage(
                senderEndpointId = endpointId,
                senderName = senderName,
                messageText = text,
                isIncoming = true,
                isAcknowledgment = true
            )
            _receivedMessages.value = _receivedMessages.value + ackMessage
            _statusMessage.value = "Acknowledgment from $senderName"
        } else {
            val incomingMessage = NearbyMessage(
                senderEndpointId = endpointId,
                senderName = senderName,
                messageText = text,
                isIncoming = true,
                isAcknowledgment = false
            )
            _receivedMessages.value = _receivedMessages.value + incomingMessage
            _statusMessage.value = "Message from $senderName: $text"
            val ackPayloadText = "ACK: Received message"
            try {
                connectionsClient.sendPayload(
                    endpointId,
                    Payload.fromBytes(ackPayloadText.toByteArray(StandardCharsets.UTF_8))
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error returning ACK to $endpointId", e)
            }
        }
    }

    // =========================================================================
    // CONTROL PROTOCOL HANDLER
    // =========================================================================
    private fun handleControlMessage(endpointId: String, jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            when (val type = json.optString("type")) {
                "TRANSFER_OFFER" -> {
                    val transferId = json.getString("transferId")
                    val senderName = json.optString(
                        "senderName",
                        connectedDeviceMap[endpointId]?.name ?: "Peer"
                    )
                    val totalBytes = json.optLong("totalBytes", 0L)
                    val itemsJson = json.getJSONArray("items")
                    val items = mutableListOf<TransferItemMetadata>()
                    for (i in 0 until itemsJson.length()) {
                        val obj = itemsJson.getJSONObject(i)
                        val contentType = runCatching {
                            ContentType.valueOf(obj.optString("contentType", ContentType.GENERIC_FILE.name))
                        }.getOrDefault(ContentType.GENERIC_FILE)
                        items.add(
                            TransferItemMetadata(
                                itemIndex = obj.getInt("itemIndex"),
                                name = obj.getString("name"),
                                mimeType = obj.optString("mimeType", "application/octet-stream"),
                                sizeBytes = obj.optLong("sizeBytes", 0L),
                                contentType = contentType,
                                sha256 = obj.optString("sha256", "")
                            )
                        )
                    }
                    val offer = IncomingTransferOffer(
                        transferId = transferId,
                        endpointId = endpointId,
                        senderName = senderName,
                        totalBytes = totalBytes,
                        items = items
                    )
                    if (activeReceiveSession != null || _incomingTransferOffer.value != null) {
                        Log.w(TAG, "Receiver busy; rejecting $transferId from $endpointId")
                        try {
                            sendControlJson(
                                endpointId,
                                JSONObject().apply {
                                    put("type", "TRANSFER_REJECT")
                                    put("transferId", transferId)
                                    put("reason", "Receiver busy with another incoming transfer")
                                }
                            )
                        } catch (_: Exception) {
                        }
                        return
                    }
                    pendingOfferReceived = offer
                    _incomingTransferOffer.value = offer
                    _statusMessage.value = "Incoming transfer request from $senderName"
                    diagnostics?.recordLog(
                        category = DiagnosticCategory.TRANSFER,
                        tag = TAG,
                        message = "Incoming TRANSFER_OFFER $transferId (${items.size} items, $totalBytes B) from $senderName [$endpointId]"
                    )
                }

                "TRANSFER_ACCEPT" -> {
                    val transferId = json.getString("transferId")
                    val sendSess = sendSessionsByTransferId[transferId] ?: return
                    if (sendSess.endpointId == endpointId && !sendSess.isCancelledOrFailed) {
                        diagnostics?.recordLog(
                            category = DiagnosticCategory.TRANSFER,
                            tag = TAG,
                            message = "${sendSess.targetDevice.name} accepted transfer $transferId"
                        )
                        beginSendingAcceptedItems(sendSess)
                    }
                }

                "TRANSFER_REJECT" -> {
                    val transferId = json.getString("transferId")
                    val rejectReason = json.optString("reason", "Declined by receiver")
                    val sendSess = sendSessionsByTransferId[transferId] ?: return
                    if (sendSess.endpointId == endpointId) {
                        sendSess.isCancelledOrFailed = true
                        val existing = _endpointTransfers.value[endpointId]
                        val cancelled = (existing ?: ActiveTransfer(
                            id = transferId,
                            senderName = sendSess.senderName,
                            receiverName = sendSess.targetDevice.name,
                            recipientEndpointId = endpointId,
                            items = sendSess.items,
                            totalBytes = sendSess.totalBytes,
                            direction = TransferDirection.SEND
                        )).copy(
                            status = TransferStatus.CANCELLED,
                            pipelineStage = TransferPipelineStage.FAILED,
                            statusDetail = "${sendSess.targetDevice.name}: $rejectReason",
                            errorMessage = rejectReason,
                            lastErrorCode = DiagnosticCodes.GS_NET_004
                        )
                        updateTransferState(endpointId, cancelled)
                        _statusMessage.value = "Declined by ${sendSess.targetDevice.name}: $rejectReason"
                        diagnostics?.recordError(
                            code = DiagnosticCodes.GS_NET_004,
                            category = DiagnosticCategory.NEARBY,
                            sourceComponent = TAG,
                            operation = "TRANSFER_REJECT",
                            summary = "${sendSess.targetDevice.name} declined transfer: $rejectReason",
                            relatedTransferId = transferId,
                            relatedEndpointId = endpointId
                        )
                        cleanupSendSession(sendSess)
                        sendSess.onComplete(cancelled)
                        onTransferFinishedListener?.invoke(cancelled)
                    }
                }

                "TRANSFER_TEXT_ITEM" -> {
                    val transferId = json.getString("transferId")
                    val recvSess = activeReceiveSession ?: return
                    if (recvSess.transferId == transferId && recvSess.endpointId == endpointId && !recvSess.isFinishedOrCancelled) {
                        val meta = TransferItemMetadata(
                            itemIndex = json.getInt("itemIndex"),
                            name = json.getString("name"),
                            mimeType = json.optString("mimeType", "text/plain"),
                            sizeBytes = json.optLong("sizeBytes", 0L),
                            contentType = runCatching {
                                ContentType.valueOf(json.optString("contentType", ContentType.TEXT.name))
                            }.getOrDefault(ContentType.TEXT),
                            sha256 = json.optString("sha256", ""),
                            textContent = json.optString("textContent", "")
                        )
                        processIncomingTextItem(recvSess, meta)
                    }
                }

                "TRANSFER_FILE_MAP" -> {
                    val transferId = json.getString("transferId")
                    val recvSess = activeReceiveSession ?: return
                    if (recvSess.transferId == transferId && recvSess.endpointId == endpointId && !recvSess.isFinishedOrCancelled) {
                        val payloadId = json.getLong("payloadId")
                        val rawName = json.getString("name")
                        val meta = TransferItemMetadata(
                            itemIndex = json.getInt("itemIndex"),
                            name = HashUtil.sanitizeFileName(rawName, "received_file"),
                            mimeType = json.optString("mimeType", "application/octet-stream"),
                            sizeBytes = json.optLong("sizeBytes", 0L),
                            contentType = runCatching {
                                ContentType.valueOf(json.optString("contentType", ContentType.GENERIC_FILE.name))
                            }.getOrDefault(ContentType.GENERIC_FILE),
                            sha256 = json.optString("sha256", "")
                        )
                        recvSess.payloadMetaByPayloadId[payloadId] = meta
                        recvSess.manifestTracker.registerManifest(transferId, payloadId, meta)
                    }
                }

                "TRANSFER_VERIFIED" -> {
                    val transferId = json.getString("transferId")
                    val verified = json.optBoolean("verified", false)
                    val shaSummary = json.optString("sha256Summary", "")
                    val errorMsg = json.optString("errorMessage", "Receiver verification failed")
                    val errorCode = json.optString("errorCode", DiagnosticCodes.GS_FILE_003)
                    val sendSess = sendSessionsByTransferId[transferId] ?: return
                    if (sendSess.endpointId != endpointId) return

                    val elapsedSeconds = ((System.currentTimeMillis() - sendSess.startTimeMs).coerceAtLeast(250L)) / 1000f
                    val finalSpeed = (sendSess.totalBytes / 1_000_000f) / elapsedSeconds
                    val existing = _endpointTransfers.value[endpointId]
                    val finalized = if (verified) {
                        (existing ?: ActiveTransfer(
                            id = transferId,
                            senderName = sendSess.senderName,
                            receiverName = sendSess.targetDevice.name,
                            recipientEndpointId = endpointId,
                            items = sendSess.items,
                            totalBytes = sendSess.totalBytes,
                            direction = TransferDirection.SEND
                        )).copy(
                            progress = 1.0f,
                            bytesTransferred = sendSess.totalBytes,
                            speedMbps = finalSpeed,
                            status = TransferStatus.SUCCESS,
                            pipelineStage = TransferPipelineStage.COMPLETED,
                            transportCompleted = true,
                            fileCopyCompleted = true,
                            sha256Checksum = shaSummary.ifBlank { sendSess.shaSummary },
                            isIntegrityVerified = true,
                            statusDetail = "Verified by ${sendSess.targetDevice.name} (SHA-256 matched)"
                        )
                    } else {
                        (existing ?: ActiveTransfer(
                            id = transferId,
                            senderName = sendSess.senderName,
                            receiverName = sendSess.targetDevice.name,
                            recipientEndpointId = endpointId,
                            items = sendSess.items,
                            totalBytes = sendSess.totalBytes,
                            direction = TransferDirection.SEND
                        )).copy(
                            status = TransferStatus.FAILED,
                            pipelineStage = TransferPipelineStage.FAILED,
                            isIntegrityVerified = false,
                            errorMessage = errorMsg,
                            statusDetail = errorMsg,
                            lastErrorCode = errorCode
                        )
                    }
                    updateTransferState(endpointId, finalized)
                    _statusMessage.value = finalized.statusDetail ?: finalized.status.label
                    cleanupSendSession(sendSess)
                    sendSess.onComplete(finalized)
                    onTransferFinishedListener?.invoke(finalized)
                }

                "TRANSFER_CANCEL" -> {
                    val transferId = json.getString("transferId")
                    if (pendingOfferReceived?.transferId == transferId) {
                        pendingOfferReceived = null
                        _incomingTransferOffer.value = null
                    }
                    val recvSess = activeReceiveSession
                    if (recvSess != null && recvSess.transferId == transferId) {
                        recvSess.isFinishedOrCancelled = true
                        cleanupReceivePayloads(recvSess)
                        val cancelled = (_endpointTransfers.value[recvSess.endpointId] ?: _activeTransfer.value)?.copy(
                            status = TransferStatus.CANCELLED,
                            pipelineStage = TransferPipelineStage.FAILED,
                            statusDetail = "Cancelled by ${recvSess.senderName}",
                            errorMessage = "Sender cancelled transfer"
                        )
                        if (cancelled != null) {
                            updateTransferState(recvSess.endpointId, cancelled)
                            onTransferFinishedListener?.invoke(cancelled)
                        }
                        activeReceiveSession = null
                        _statusMessage.value = "Transfer cancelled by peer"
                    }
                    val sendSess = sendSessionsByTransferId[transferId]
                    if (sendSess != null && sendSess.endpointId == endpointId) {
                        sendSess.isCancelledOrFailed = true
                        val cancelled = _endpointTransfers.value[endpointId]?.copy(
                            status = TransferStatus.CANCELLED,
                            pipelineStage = TransferPipelineStage.FAILED,
                            statusDetail = "Cancelled by ${sendSess.targetDevice.name}",
                            errorMessage = "Receiver cancelled transfer"
                        )
                        if (cancelled != null) {
                            updateTransferState(endpointId, cancelled)
                            sendSess.onComplete(cancelled)
                            onTransferFinishedListener?.invoke(cancelled)
                        }
                        cleanupSendSession(sendSess)
                        _statusMessage.value = "Transfer cancelled by ${sendSess.targetDevice.name}"
                    }
                }

                else -> Log.w(TAG, "Unknown control type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling control message from $endpointId", e)
            diagnostics?.recordException(
                throwable = e,
                sourceComponent = TAG,
                operation = "handleControlMessage",
                defaultCode = DiagnosticCodes.GS_APP_002,
                defaultCategory = DiagnosticCategory.TRANSFER,
                relatedEndpointId = endpointId
            )
        }
    }

    private fun cleanupSendSession(sendSess: SendSession) {
        sendSessionsByTransferId.remove(sendSess.transferId)
        sendSess.activePayloadIds.forEach { sendSessionsByPayloadId.remove(it) }
    }

    private fun sendControlJson(endpointId: String, json: JSONObject) {
        val raw = (CTRL_PREFIX + json.toString()).toByteArray(StandardCharsets.UTF_8)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(raw))
    }

    // =========================================================================
    // SENDER FLOW (SINGLE OR MULTI-RECIPIENT)
    // =========================================================================
    override fun startTransfer(
        targetDevice: DeviceItem,
        items: List<ContentItem>,
        myDeviceName: String,
        onComplete: (ActiveTransfer) -> Unit
    ) {
        startMultiRecipientTransfer(
            targetDevices = listOf(targetDevice),
            items = items,
            myDeviceName = myDeviceName,
            onRecipientComplete = onComplete
        )
    }

    override fun startMultiRecipientTransfer(
        targetDevices: List<DeviceItem>,
        items: List<ContentItem>,
        myDeviceName: String,
        onRecipientComplete: (ActiveTransfer) -> Unit
    ) {
        if (items.isEmpty()) {
            _statusMessage.value = "No content staged to transfer."
            return
        }
        val distinctTargets = targetDevices.distinctBy { it.endpointId }
        if (distinctTargets.isEmpty()) {
            _statusMessage.value = "No target peer selected."
            return
        }

        lastTargetDevices = distinctTargets
        lastTransferItems = items
        lastOnComplete = onRecipientComplete

        scope.launch {
            // Prepare & compute hashes once in IO
            val prepStatus = ActiveTransfer(
                senderName = myDeviceName,
                receiverName = if (distinctTargets.size == 1) distinctTargets.first().name else "${distinctTargets.size} recipients",
                recipientEndpointId = distinctTargets.first().endpointId,
                items = items,
                totalBytes = items.sumOf { it.sizeBytes },
                status = TransferStatus.PREPARING,
                pipelineStage = TransferPipelineStage.PREPARING,
                direction = TransferDirection.SEND,
                statusDetail = "Staging & computing SHA-256 digests..."
            )
            _activeTransfer.value = prepStatus

            val preparedItems = withContext(Dispatchers.IO) {
                prepareItemsForSending(items)
            }

            if (preparedItems == null) {
                val err = "Unable to open or stage one or more selected items."
                distinctTargets.forEach { target ->
                    val failed = prepStatus.copy(
                        id = UUID.randomUUID().toString(),
                        receiverName = target.name,
                        recipientEndpointId = target.endpointId,
                        status = TransferStatus.FAILED,
                        pipelineStage = TransferPipelineStage.FAILED,
                        errorMessage = err,
                        statusDetail = "Failed preparing source file stream",
                        lastErrorCode = DiagnosticCodes.GS_FILE_007
                    )
                    updateTransferState(target.endpointId, failed)
                    onRecipientComplete(failed)
                    onTransferFinishedListener?.invoke(failed)
                }
                return@launch
            }

            val totalBytes = preparedItems.sumOf { it.sizeBytes.coerceAtLeast(1L) }
            val shaSummary = if (preparedItems.size == 1) {
                preparedItems.first().sha256Hash.orEmpty()
            } else {
                val joined = preparedItems.joinToString(":") { it.sha256Hash.orEmpty() }
                HashUtil.calculateSha256(joined.byteInputStream(StandardCharsets.UTF_8))
            }

            for (target in distinctTargets) {
                val endpointConnected = connectedDeviceMap.containsKey(target.endpointId)
                if (!endpointConnected) {
                    val failed = ActiveTransfer(
                        senderName = myDeviceName,
                        receiverName = target.name,
                        recipientEndpointId = target.endpointId,
                        items = preparedItems,
                        totalBytes = totalBytes,
                        status = TransferStatus.FAILED,
                        pipelineStage = TransferPipelineStage.FAILED,
                        direction = TransferDirection.SEND,
                        errorMessage = "Peer '${target.name}' is not connected. Complete Nearby pairing first.",
                        statusDetail = "No active Nearby connection to ${target.name}",
                        lastErrorCode = DiagnosticCodes.GS_NET_002
                    )
                    updateTransferState(target.endpointId, failed)
                    onRecipientComplete(failed)
                    onTransferFinishedListener?.invoke(failed)
                    continue
                }

                if (sendSessionsByTransferId.values.any { it.endpointId == target.endpointId && !it.isCancelledOrFailed }) {
                    _statusMessage.value = "Transfer already active for ${target.name}."
                    continue
                }

                val transferId = UUID.randomUUID().toString()
                val session = SendSession(
                    transferId = transferId,
                    endpointId = target.endpointId,
                    targetDevice = target,
                    items = preparedItems,
                    senderName = myDeviceName,
                    totalBytes = totalBytes,
                    startTimeMs = System.currentTimeMillis(),
                    shaSummary = shaSummary,
                    onComplete = onRecipientComplete
                )
                sendSessionsByTransferId[transferId] = session

                val offerTransfer = ActiveTransfer(
                    id = transferId,
                    senderName = myDeviceName,
                    receiverName = target.name,
                    recipientEndpointId = target.endpointId,
                    items = preparedItems,
                    progress = 0f,
                    bytesTransferred = 0L,
                    totalBytes = totalBytes,
                    status = TransferStatus.AWAITING_ACCEPTANCE,
                    pipelineStage = TransferPipelineStage.AWAITING_ACCEPTANCE,
                    direction = TransferDirection.SEND,
                    sha256Checksum = shaSummary.ifBlank { "Pending" },
                    statusDetail = "Waiting for ${target.name} to accept offer..."
                )
                updateTransferState(target.endpointId, offerTransfer)

                val offerJson = JSONObject().apply {
                    put("type", "TRANSFER_OFFER")
                    put("transferId", transferId)
                    put("senderName", myDeviceName)
                    put("totalBytes", totalBytes)
                    val arr = JSONArray()
                    preparedItems.forEachIndexed { idx, item ->
                        arr.put(JSONObject().apply {
                            put("itemIndex", idx)
                            put("name", item.name)
                            put("mimeType", item.mimeType)
                            put("sizeBytes", item.sizeBytes)
                            put("contentType", item.type.name)
                            put("sha256", item.sha256Hash ?: "")
                        })
                    }
                    put("items", arr)
                }

                try {
                    sendControlJson(target.endpointId, offerJson)
                    diagnostics?.recordLog(
                        category = DiagnosticCategory.TRANSFER,
                        tag = TAG,
                        message = "Sent TRANSFER_OFFER $transferId to ${target.name} [${target.endpointId}]"
                    )
                } catch (e: Exception) {
                    cleanupSendSession(session)
                    diagnostics?.recordException(
                        throwable = e,
                        sourceComponent = TAG,
                        operation = "sendTransferOffer",
                        defaultCode = DiagnosticCodes.GS_NET_002,
                        defaultCategory = DiagnosticCategory.NEARBY,
                        relatedTransferId = transferId,
                        relatedEndpointId = target.endpointId
                    )
                    val failed = offerTransfer.copy(
                        status = TransferStatus.FAILED,
                        pipelineStage = TransferPipelineStage.FAILED,
                        errorMessage = e.localizedMessage ?: "Failed sending offer",
                        statusDetail = "Offer failed to send",
                        lastErrorCode = DiagnosticCodes.GS_NET_002
                    )
                    updateTransferState(target.endpointId, failed)
                    onRecipientComplete(failed)
                    onTransferFinishedListener?.invoke(failed)
                }
            }
            _statusMessage.value = "Transfer offer dispatched to ${distinctTargets.size} recipient(s)."
        }
    }

    private fun prepareItemsForSending(items: List<ContentItem>): List<ContentItem>? {
        val result = mutableListOf<ContentItem>()
        for (item in items) {
            try {
                if (item.textContent != null && (item.type == ContentType.TEXT || item.type == ContentType.URL)) {
                    val bytes = item.textContent.toByteArray(StandardCharsets.UTF_8)
                    val hash = if (!item.sha256Hash.isNullOrBlank()) item.sha256Hash else {
                        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    }
                    result.add(item.copy(sizeBytes = bytes.size.toLong(), sha256Hash = hash))
                } else {
                    val existingPath = item.cachedFilePath
                    val file = if (!existingPath.isNullOrBlank() && File(existingPath).exists()) {
                        File(existingPath)
                    } else if (item.uri != null) {
                        val cacheDir = File(context.cacheDir, "outgoing").apply { mkdirs() }
                        if (item.sizeBytes > 0L && cacheDir.usableSpace < item.sizeBytes + 512L * 1024L) {
                            diagnostics?.recordError(
                                code = DiagnosticCodes.GS_FILE_005,
                                category = DiagnosticCategory.STORAGE,
                                sourceComponent = TAG,
                                operation = "prepareItemsForSending",
                                summary = "Insufficient cache storage for ${item.name}"
                            )
                            return null
                        }
                        val safeName = HashUtil.sanitizeFileName(item.name, "file")
                        val copied = File(cacheDir, "${UUID.randomUUID().toString().take(8)}_$safeName")
                        val opened = context.contentResolver.openInputStream(item.uri)?.use { input ->
                            FileOutputStream(copied).use { output -> input.copyTo(output, 64 * 1024) }
                            true
                        } ?: false
                        if (!opened) return null
                        copied
                    } else {
                        return null
                    }

                    val hash = if (!item.sha256Hash.isNullOrBlank()) {
                        item.sha256Hash
                    } else {
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buf = ByteArray(64 * 1024)
                        FileInputStream(file).use { fis ->
                            var r: Int
                            while (fis.read(buf).also { r = it } != -1) {
                                digest.update(buf, 0, r)
                            }
                        }
                        digest.digest().joinToString("") { "%02x".format(it) }
                    }
                    result.add(
                        item.copy(
                            sizeBytes = file.length(),
                            cachedFilePath = file.absolutePath,
                            sha256Hash = hash
                        )
                    )
                }
            } catch (e: Exception) {
                diagnostics?.recordException(
                    throwable = e,
                    sourceComponent = TAG,
                    operation = "prepareItemsForSending",
                    defaultCode = DiagnosticCodes.GS_FILE_007,
                    defaultCategory = DiagnosticCategory.FILE_IO
                )
                return null
            }
        }
        return result
    }

    private fun beginSendingAcceptedItems(sendSess: SendSession) {
        scope.launch(Dispatchers.IO) {
            try {
                val fileItems = mutableListOf<Pair<Int, ContentItem>>()
                val textItems = mutableListOf<Pair<Int, ContentItem>>()
                sendSess.items.forEachIndexed { idx, item ->
                    if (item.textContent != null && (item.type == ContentType.TEXT || item.type == ContentType.URL)) {
                        textItems.add(idx to item)
                    } else {
                        fileItems.add(idx to item)
                    }
                }
                sendSess.expectedFilePayloadsCount = fileItems.size

                withContext(Dispatchers.Main) {
                    val current = _endpointTransfers.value[sendSess.endpointId] ?: _activeTransfer.value
                    current?.let {
                        updateTransferState(
                            sendSess.endpointId,
                            it.copy(
                                status = TransferStatus.TRANSFERRING,
                                pipelineStage = TransferPipelineStage.TRANSFERRING,
                                statusDetail = "Streaming ${sendSess.items.size} item(s) to ${sendSess.targetDevice.name}..."
                            )
                        )
                    }
                    _statusMessage.value = "Transferring to ${sendSess.targetDevice.name}..."
                }

                for ((idx, item) in textItems) {
                    if (sendSess.isCancelledOrFailed) return@launch
                    val json = JSONObject().apply {
                        put("type", "TRANSFER_TEXT_ITEM")
                        put("transferId", sendSess.transferId)
                        put("itemIndex", idx)
                        put("name", item.name)
                        put("mimeType", item.mimeType)
                        put("sizeBytes", item.sizeBytes)
                        put("contentType", item.type.name)
                        put("sha256", item.sha256Hash ?: "")
                        put("textContent", item.textContent ?: "")
                    }
                    sendControlJson(sendSess.endpointId, json)
                    sendSess.textBytesSent += item.sizeBytes.coerceAtLeast(1L)
                    updateSenderProgress(sendSess)
                }

                for ((idx, item) in fileItems) {
                    if (sendSess.isCancelledOrFailed) return@launch
                    val file = File(item.cachedFilePath ?: continue)
                    val filePayload = Payload.fromFile(file)
                    sendSess.activePayloadIds.add(filePayload.id)
                    sendSessionsByPayloadId[filePayload.id] = sendSess
                    sendSess.payloadBytesProgress[filePayload.id] = 0L

                    val mapJson = JSONObject().apply {
                        put("type", "TRANSFER_FILE_MAP")
                        put("transferId", sendSess.transferId)
                        put("payloadId", filePayload.id)
                        put("itemIndex", idx)
                        put("name", item.name)
                        put("mimeType", item.mimeType)
                        put("sizeBytes", file.length())
                        put("contentType", item.type.name)
                        put("sha256", item.sha256Hash ?: "")
                    }
                    sendControlJson(sendSess.endpointId, mapJson)
                    connectionsClient.sendPayload(sendSess.endpointId, filePayload)
                }

                if (fileItems.isEmpty() && !sendSess.isCancelledOrFailed) {
                    withContext(Dispatchers.Main) {
                        val current = _endpointTransfers.value[sendSess.endpointId]
                        if (current != null) {
                            updateTransferState(
                                sendSess.endpointId,
                                current.copy(
                                    progress = 1.0f,
                                    bytesTransferred = sendSess.totalBytes,
                                    status = TransferStatus.VERIFYING,
                                    pipelineStage = TransferPipelineStage.VERIFYING_INTEGRITY,
                                    transportCompleted = true,
                                    statusDetail = "Waiting for ${sendSess.targetDevice.name} SHA-256 verification ACK..."
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error streaming transfer items to ${sendSess.targetDevice.name}", e)
                if (!sendSess.isCancelledOrFailed) {
                    sendSess.isCancelledOrFailed = true
                    diagnostics?.recordException(
                        throwable = e,
                        sourceComponent = TAG,
                        operation = "beginSendingAcceptedItems",
                        defaultCode = DiagnosticCodes.GS_NET_002,
                        defaultCategory = DiagnosticCategory.TRANSFER,
                        relatedTransferId = sendSess.transferId,
                        relatedEndpointId = sendSess.endpointId
                    )
                    withContext(Dispatchers.Main) {
                        val failed = (_endpointTransfers.value[sendSess.endpointId] ?: _activeTransfer.value)?.copy(
                            status = TransferStatus.FAILED,
                            pipelineStage = TransferPipelineStage.FAILED,
                            errorMessage = e.localizedMessage ?: "Payload send error",
                            statusDetail = "Transfer failed while streaming",
                            lastErrorCode = DiagnosticCodes.GS_NET_002
                        )
                        if (failed != null) {
                            updateTransferState(sendSess.endpointId, failed)
                            sendSess.onComplete(failed)
                            onTransferFinishedListener?.invoke(failed)
                        }
                        cleanupSendSession(sendSess)
                    }
                }
            }
        }
    }

    private fun handleSenderPayloadUpdate(sendSess: SendSession, update: PayloadTransferUpdate) {
        if (sendSess.isCancelledOrFailed) return
        when (update.status) {
            PayloadTransferUpdate.Status.IN_PROGRESS -> {
                sendSess.payloadBytesProgress[update.payloadId] = update.bytesTransferred
                updateSenderProgress(sendSess)
            }

            PayloadTransferUpdate.Status.SUCCESS -> {
                sendSess.payloadBytesProgress[update.payloadId] = update.totalBytes.coerceAtLeast(update.bytesTransferred)
                sendSess.completedPayloads.add(update.payloadId)
                updateSenderProgress(sendSess)
                if (sendSess.completedPayloads.size >= sendSess.expectedFilePayloadsCount) {
                    val current = _endpointTransfers.value[sendSess.endpointId]
                    if (current != null) {
                        updateTransferState(
                            sendSess.endpointId,
                            current.copy(
                                progress = 1.0f,
                                bytesTransferred = sendSess.totalBytes,
                                status = TransferStatus.VERIFYING,
                                pipelineStage = TransferPipelineStage.VERIFYING_INTEGRITY,
                                transportCompleted = true,
                                statusDetail = "100% bytes sent. Awaiting receiver verification & save..."
                            )
                        )
                    }
                }
            }

            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                sendSess.isCancelledOrFailed = true
                val newStatus = if (update.status == PayloadTransferUpdate.Status.CANCELED) {
                    TransferStatus.CANCELLED
                } else {
                    TransferStatus.FAILED
                }
                if (newStatus == TransferStatus.FAILED) {
                    diagnostics?.recordError(
                        code = DiagnosticCodes.GS_NET_003,
                        category = DiagnosticCategory.TRANSFER,
                        sourceComponent = TAG,
                        operation = "handleSenderPayloadUpdate",
                        summary = "Payload transmission failed to ${sendSess.targetDevice.name}",
                        relatedTransferId = sendSess.transferId,
                        relatedEndpointId = sendSess.endpointId
                    )
                }
                val failed = _endpointTransfers.value[sendSess.endpointId]?.copy(
                    status = newStatus,
                    pipelineStage = TransferPipelineStage.FAILED,
                    errorMessage = if (newStatus == TransferStatus.CANCELLED) "Transfer cancelled" else "Payload transmission interrupted",
                    statusDetail = "Payload ${newStatus.name.lowercase()} during P2P send",
                    lastErrorCode = if (newStatus == TransferStatus.FAILED) DiagnosticCodes.GS_NET_003 else null
                )
                if (failed != null) {
                    updateTransferState(sendSess.endpointId, failed)
                    sendSess.onComplete(failed)
                    onTransferFinishedListener?.invoke(failed)
                }
                cleanupSendSession(sendSess)
            }
        }
    }

    private fun updateSenderProgress(sendSess: SendSession) {
        val fileBytes = sendSess.payloadBytesProgress.values.sum()
        val totalTransferred = (sendSess.textBytesSent + fileBytes).coerceAtMost(sendSess.totalBytes)
        val progress = if (sendSess.totalBytes > 0L) {
            (totalTransferred.toFloat() / sendSess.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val elapsedSec = ((System.currentTimeMillis() - sendSess.startTimeMs).coerceAtLeast(200L)) / 1000f
        val speedMBps = (totalTransferred / 1_000_000f) / elapsedSec

        scope.launch(Dispatchers.Main) {
            val current = _endpointTransfers.value[sendSess.endpointId]
            if (current != null && current.id == sendSess.transferId &&
                (current.status == TransferStatus.TRANSFERRING || current.status == TransferStatus.AWAITING_ACCEPTANCE)
            ) {
                updateTransferState(
                    sendSess.endpointId,
                    current.copy(
                        progress = progress,
                        bytesTransferred = totalTransferred,
                        speedMbps = speedMBps,
                        status = TransferStatus.TRANSFERRING,
                        pipelineStage = TransferPipelineStage.TRANSFERRING
                    )
                )
            }
        }
    }

    // =========================================================================
    // RECEIVER FLOW (URI-SAFE STREAMING COPY -> SHA-256 -> PRIVATE SAVE -> MEDIASTORE EXPORT)
    // =========================================================================
    override fun acceptTransferOffer(transferId: String) {
        val offer = _incomingTransferOffer.value ?: pendingOfferReceived ?: return
        if (offer.transferId != transferId) return

        _incomingTransferOffer.value = null
        pendingOfferReceived = null

        val availableBytes = context.filesDir.usableSpace
        if (offer.totalBytes > 0L && availableBytes < offer.totalBytes + 512L * 1024L) {
            val storageMsg = "Insufficient storage space on receiver (need ${offer.totalBytes} bytes)"
            _statusMessage.value = storageMsg
            diagnostics?.recordError(
                code = DiagnosticCodes.GS_FILE_005,
                category = DiagnosticCategory.STORAGE,
                sourceComponent = TAG,
                operation = "acceptTransferOffer",
                summary = storageMsg,
                relatedTransferId = transferId,
                relatedEndpointId = offer.endpointId
            )
            val failed = ActiveTransfer(
                id = offer.transferId,
                senderName = offer.senderName,
                receiverName = currentDeviceName,
                recipientEndpointId = offer.endpointId,
                items = offer.items.map {
                    ContentItem(name = it.name, mimeType = it.mimeType, sizeBytes = it.sizeBytes, type = it.contentType)
                },
                totalBytes = offer.totalBytes,
                status = TransferStatus.FAILED,
                pipelineStage = TransferPipelineStage.FAILED,
                direction = TransferDirection.RECEIVE,
                errorMessage = storageMsg,
                statusDetail = storageMsg,
                lastErrorCode = DiagnosticCodes.GS_FILE_005
            )
            updateTransferState(offer.endpointId, failed)
            onTransferFinishedListener?.invoke(failed)
            try {
                sendControlJson(
                    offer.endpointId,
                    JSONObject().apply {
                        put("type", "TRANSFER_REJECT")
                        put("transferId", transferId)
                        put("reason", storageMsg)
                    }
                )
            } catch (_: Exception) {
            }
            return
        }

        val metadataMap = offer.items.associateBy { it.itemIndex }
        val session = ReceiveSession(
            transferId = offer.transferId,
            endpointId = offer.endpointId,
            senderName = offer.senderName,
            totalBytes = offer.totalBytes.coerceAtLeast(1L),
            expectedItems = metadataMap,
            startTimeMs = System.currentTimeMillis()
        )
        activeReceiveSession = session

        val placeholderItems = offer.items.map { meta ->
            ContentItem(
                name = meta.name,
                mimeType = meta.mimeType,
                sizeBytes = meta.sizeBytes,
                type = meta.contentType,
                sha256Hash = meta.sha256
            )
        }

        updateTransferState(
            offer.endpointId,
            ActiveTransfer(
                id = offer.transferId,
                senderName = offer.senderName,
                receiverName = currentDeviceName,
                recipientEndpointId = offer.endpointId,
                items = placeholderItems,
                progress = 0f,
                bytesTransferred = 0L,
                totalBytes = session.totalBytes,
                status = TransferStatus.TRANSFERRING,
                pipelineStage = TransferPipelineStage.TRANSFERRING,
                direction = TransferDirection.RECEIVE,
                sha256Checksum = offer.items.firstOrNull()?.sha256 ?: "Pending Verification",
                statusDetail = "Receiving stream from ${offer.senderName}..."
            )
        )
        _statusMessage.value = "Accepted transfer from ${offer.senderName}"

        val acceptJson = JSONObject().apply {
            put("type", "TRANSFER_ACCEPT")
            put("transferId", transferId)
        }
        try {
            sendControlJson(offer.endpointId, acceptJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending TRANSFER_ACCEPT", e)
            diagnostics?.recordException(
                throwable = e,
                sourceComponent = TAG,
                operation = "acceptTransferOffer",
                defaultCode = DiagnosticCodes.GS_NET_002,
                defaultCategory = DiagnosticCategory.NEARBY,
                relatedTransferId = transferId,
                relatedEndpointId = offer.endpointId
            )
        }
    }

    override fun rejectTransferOffer(transferId: String) {
        val offer = _incomingTransferOffer.value ?: pendingOfferReceived ?: return
        if (offer.transferId != transferId) return

        _incomingTransferOffer.value = null
        pendingOfferReceived = null
        _statusMessage.value = "Declined incoming transfer from ${offer.senderName}"
        diagnostics?.recordLog(
            category = DiagnosticCategory.TRANSFER,
            tag = TAG,
            message = "User declined incoming transfer $transferId from ${offer.senderName}"
        )

        val rejectJson = JSONObject().apply {
            put("type", "TRANSFER_REJECT")
            put("transferId", transferId)
            put("reason", "Declined by receiver")
        }
        try {
            sendControlJson(offer.endpointId, rejectJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending TRANSFER_REJECT", e)
        }
    }

    private fun processIncomingTextItem(recvSess: ReceiveSession, meta: TransferItemMetadata) {
        scope.launch(Dispatchers.IO) {
            try {
                val text = meta.textContent ?: ""
                val textBytes = text.toByteArray(StandardCharsets.UTF_8)
                val actualHash = HashUtil.calculateSha256(textBytes.inputStream())

                if (meta.sha256.isNotBlank() && !HashUtil.verifyHash(actualHash, meta.sha256)) {
                    failReceiveSession(
                        recvSess,
                        reason = "SHA-256 mismatch on text/link '${meta.name}'",
                        errorCode = DiagnosticCodes.GS_FILE_003
                    )
                    return@launch
                }

                val receivedDir = File(context.filesDir, "received").apply { mkdirs() }
                val safeBase = HashUtil.sanitizeFileName(meta.name, "note").take(36)
                val fileName = "${safeBase}_${System.currentTimeMillis() % 10000}.txt"
                val destFile = incomingStorage.createUniqueFile(receivedDir, fileName)
                destFile.writeText(text, StandardCharsets.UTF_8)

                val (msUri, msNote) = incomingStorage.exportToPublicDownloads(destFile, destFile.name, "text/plain")
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile
                )
                recvSess.latestSavedUri = msUri ?: fileUri
                recvSess.latestSavedMime = if (meta.contentType == ContentType.URL) "text/uri-list" else "text/plain"
                recvSess.allMediaStoreExported = recvSess.allMediaStoreExported && (msUri != null && msNote == null)
                if (msNote != null) recvSess.latestMediaStoreNote = msNote

                recvSess.verifiedHashes[meta.itemIndex] = actualHash
                recvSess.verifiedItems[meta.itemIndex] = ContentItem(
                    uri = recvSess.latestSavedUri,
                    name = meta.name,
                    mimeType = meta.mimeType,
                    sizeBytes = textBytes.size.toLong(),
                    textContent = text,
                    type = meta.contentType,
                    cachedFilePath = destFile.absolutePath,
                    sha256Hash = actualHash
                )

                val notePreview = if (meta.contentType == ContentType.URL) "Shared Link: $text" else "Shared Note: $text"
                _receivedMessages.value = _receivedMessages.value + NearbyMessage(
                    senderEndpointId = recvSess.endpointId,
                    senderName = recvSess.senderName,
                    messageText = notePreview,
                    isIncoming = true,
                    isAcknowledgment = false
                )

                updateReceiverProgress(recvSess, textBytes.size.toLong())
                checkReceiveSessionCompletion(recvSess)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving incoming text item", e)
                failReceiveSession(
                    recvSess,
                    reason = e.localizedMessage ?: "Failed saving text item",
                    errorCode = DiagnosticCodes.GS_FILE_001,
                    throwable = e
                )
            }
        }
    }

    private fun handleReceiverPayloadUpdate(recvSess: ReceiveSession, update: PayloadTransferUpdate) {
        if (recvSess.isFinishedOrCancelled) return
        when (update.status) {
            PayloadTransferUpdate.Status.IN_PROGRESS -> {
                recvSess.payloadBytesProgress[update.payloadId] = update.bytesTransferred
                updateReceiverProgress(recvSess)
            }

            PayloadTransferUpdate.Status.SUCCESS -> {
                recvSess.payloadBytesProgress[update.payloadId] = update.totalBytes.coerceAtLeast(update.bytesTransferred)
                recvSess.manifestTracker.markPayloadCompleted(update.payloadId)
                updateReceiverProgress(recvSess)
                val payload = recvSess.receivedFilePayloads[update.payloadId] ?: return
                verifyAndSaveIncomingFilePayload(recvSess, payload)
            }

            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                val isCanceled = update.status == PayloadTransferUpdate.Status.CANCELED
                val msg = if (isCanceled) "Incoming transfer cancelled" else "Incoming file stream interrupted"
                failReceiveSession(
                    recvSess,
                    reason = msg,
                    isCancelled = isCanceled,
                    errorCode = if (isCanceled) null else DiagnosticCodes.GS_NET_003
                )
            }
        }
    }

    private fun verifyAndSaveIncomingFilePayload(recvSess: ReceiveSession, payload: Payload) {
        scope.launch(Dispatchers.IO) {
            try {
                // Wait briefly in case TRANSFER_FILE_MAP arrives slightly after FILE payload
                var meta = recvSess.manifestTracker.claimForVerification(payload.id)
                var waitAttempts = 0
                while (meta == null && !recvSess.manifestTracker.isProcessed(payload.id) &&
                    waitAttempts < 60 && !recvSess.isFinishedOrCancelled
                ) {
                    delay(50)
                    meta = recvSess.manifestTracker.claimForVerification(payload.id)
                    waitAttempts++
                }

                if (recvSess.isFinishedOrCancelled) {
                    IncomingPayloadStorage.safeDeletePayload(context, payload)
                    return@launch
                }

                if (recvSess.manifestTracker.isProcessed(payload.id) && meta == null) {
                    return@launch
                }

                if (meta == null) {
                    IncomingPayloadStorage.safeDeletePayload(context, payload)
                    failReceiveSession(
                        recvSess,
                        reason = "Missing metadata manifest for payload ${payload.id}",
                        errorCode = DiagnosticCodes.GS_FILE_001
                    )
                    return@launch
                }

                // Mark Nearby transport completed immediately before scoped-storage copy
                withContext(Dispatchers.Main) {
                    val current = _endpointTransfers.value[recvSess.endpointId] ?: _activeTransfer.value
                    current?.let {
                        updateTransferState(
                            recvSess.endpointId,
                            it.copy(
                                status = TransferStatus.VERIFYING,
                                pipelineStage = TransferPipelineStage.TRANSPORT_COMPLETED,
                                transportCompleted = true,
                                statusDetail = "Transport 100% received. Opening ContentResolver stream..."
                            )
                        )
                    }
                }

                val payloadSource = IncomingPayloadStorage.resolvePayloadSource(payload)
                    ?: throw TransferVerificationException(
                        code = DiagnosticCodes.GS_FILE_001,
                        category = DiagnosticCategory.FILE_IO,
                        message = "Nearby payload ${payload.id} exposed no readable URI or PFD source.",
                        suggestedAction = "Ensure payload uses ContentResolver URI stream and retry."
                    )

                val saved = incomingStorage.copyVerifyAndSave(
                    transferId = recvSess.transferId,
                    meta = meta,
                    source = payloadSource,
                    onStageChange = { stage, detail ->
                        scope.launch(Dispatchers.Main) {
                            val cur = _endpointTransfers.value[recvSess.endpointId] ?: _activeTransfer.value
                            if (cur != null && cur.id == recvSess.transferId) {
                                updateTransferState(
                                    recvSess.endpointId,
                                    cur.copy(
                                        status = TransferStatus.VERIFYING,
                                        pipelineStage = stage,
                                        transportCompleted = true,
                                        fileCopyCompleted = stage.ordinal >= TransferPipelineStage.VERIFYING_INTEGRITY.ordinal,
                                        statusDetail = detail
                                    )
                                )
                            }
                        }
                    }
                )

                recvSess.latestSavedUri = saved.shareableUri
                recvSess.latestSavedMime = meta.mimeType
                if (!saved.mediaStoreExported) {
                    recvSess.allMediaStoreExported = false
                    recvSess.latestMediaStoreNote = saved.mediaStoreNote
                }

                recvSess.verifiedHashes[meta.itemIndex] = saved.sha256Hash
                recvSess.verifiedItems[meta.itemIndex] = ContentItem(
                    uri = saved.shareableUri,
                    name = saved.permanentFile.name,
                    mimeType = meta.mimeType,
                    sizeBytes = saved.bytesCopied,
                    thumbnailUri = if (meta.contentType == ContentType.IMAGE || meta.contentType == ContentType.VIDEO) {
                        saved.shareableUri
                    } else null,
                    type = meta.contentType,
                    cachedFilePath = saved.permanentFile.absolutePath,
                    sha256Hash = saved.sha256Hash
                )

                checkReceiveSessionCompletion(recvSess)
            } catch (e: TransferVerificationException) {
                Log.e(TAG, "Verification/Scoped Storage error [${e.code}]: ${e.message}", e)
                IncomingPayloadStorage.safeDeletePayload(context, payload)
                failReceiveSession(
                    recvSess,
                    reason = e.message,
                    errorCode = e.code,
                    throwable = e
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying/saving incoming file", e)
                IncomingPayloadStorage.safeDeletePayload(context, payload)
                val classified = DiagnosticClassifier.classifyThrowable(
                    throwable = e,
                    sourceComponent = TAG,
                    operation = "verifyAndSaveIncomingFilePayload",
                    defaultCode = DiagnosticCodes.GS_FILE_001,
                    defaultCategory = DiagnosticCategory.FILE_IO,
                    relatedTransferId = recvSess.transferId,
                    relatedEndpointId = recvSess.endpointId
                )
                failReceiveSession(
                    recvSess,
                    reason = classified.summary,
                    errorCode = classified.code,
                    throwable = e
                )
            }
        }
    }

    private suspend fun checkReceiveSessionCompletion(recvSess: ReceiveSession) {
        if (recvSess.isFinishedOrCancelled) return
        if (recvSess.verifiedItems.size < recvSess.expectedItems.size) return

        recvSess.isFinishedOrCancelled = true
        val elapsedSec = ((System.currentTimeMillis() - recvSess.startTimeMs).coerceAtLeast(250L)) / 1000f
        val speedMBps = (recvSess.totalBytes / 1_000_000f) / elapsedSec

        val orderedItems = recvSess.expectedItems.keys.sorted().mapNotNull { recvSess.verifiedItems[it] }
        val shaSummary = if (recvSess.verifiedHashes.size == 1) {
            recvSess.verifiedHashes.values.first()
        } else {
            val joined = recvSess.expectedItems.keys.sorted().joinToString(":") { recvSess.verifiedHashes[it].orEmpty() }
            HashUtil.calculateSha256(joined.byteInputStream(StandardCharsets.UTF_8))
        }

        val ackJson = JSONObject().apply {
            put("type", "TRANSFER_VERIFIED")
            put("transferId", recvSess.transferId)
            put("verified", true)
            put("sha256Summary", shaSummary)
        }
        try {
            sendControlJson(recvSess.endpointId, ackJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed sending TRANSFER_VERIFIED ACK", e)
        }

        val detailMsg = if (recvSess.allMediaStoreExported) {
            "SHA-256 Verified · Exported to Downloads/GestoShare"
        } else {
            "SHA-256 Verified in App Storage (Downloads export note: ${recvSess.latestMediaStoreNote ?: "fallback"})"
        }

        diagnostics?.recordLog(
            category = DiagnosticCategory.VERIFICATION,
            tag = TAG,
            message = "Receive transfer ${recvSess.transferId} verified (${orderedItems.size} items, $detailMsg)"
        )

        withContext(Dispatchers.Main) {
            val completed = (_endpointTransfers.value[recvSess.endpointId] ?: _activeTransfer.value ?: ActiveTransfer(
                id = recvSess.transferId,
                senderName = recvSess.senderName,
                receiverName = currentDeviceName,
                recipientEndpointId = recvSess.endpointId,
                items = orderedItems,
                totalBytes = recvSess.totalBytes,
                direction = TransferDirection.RECEIVE
            )).copy(
                items = orderedItems,
                progress = 1.0f,
                bytesTransferred = recvSess.totalBytes,
                speedMbps = speedMBps,
                status = TransferStatus.SUCCESS,
                pipelineStage = TransferPipelineStage.COMPLETED,
                transportCompleted = true,
                fileCopyCompleted = true,
                mediaStoreExported = recvSess.allMediaStoreExported,
                mediaStoreExportNote = recvSess.latestMediaStoreNote,
                sha256Checksum = shaSummary,
                isIntegrityVerified = true,
                statusDetail = detailMsg,
                savedUriString = recvSess.latestSavedUri?.toString(),
                savedMimeType = recvSess.latestSavedMime
            )
            updateTransferState(recvSess.endpointId, completed)
            _statusMessage.value = "Received & verified ${orderedItems.size} item(s) from ${recvSess.senderName}"
            activeReceiveSession = null
            onTransferFinishedListener?.invoke(completed)
        }
    }

    private fun failReceiveSession(
        recvSess: ReceiveSession,
        reason: String,
        isCancelled: Boolean = false,
        errorCode: String? = null,
        throwable: Throwable? = null
    ) {
        if (recvSess.isFinishedOrCancelled) return
        recvSess.isFinishedOrCancelled = true
        cleanupReceivePayloads(recvSess)

        if (!isCancelled) {
            val code = errorCode ?: DiagnosticCodes.GS_FILE_001
            if (throwable != null) {
                diagnostics?.recordException(
                    throwable = throwable,
                    sourceComponent = TAG,
                    operation = "receiveTransfer",
                    defaultCode = code,
                    defaultCategory = DiagnosticCategory.FILE_IO,
                    relatedTransferId = recvSess.transferId,
                    relatedEndpointId = recvSess.endpointId
                )
            } else {
                diagnostics?.recordError(
                    code = code,
                    category = DiagnosticCategory.FILE_IO,
                    sourceComponent = TAG,
                    operation = "receiveTransfer",
                    summary = reason,
                    relatedTransferId = recvSess.transferId,
                    relatedEndpointId = recvSess.endpointId
                )
            }

            val failJson = JSONObject().apply {
                put("type", "TRANSFER_VERIFIED")
                put("transferId", recvSess.transferId)
                put("verified", false)
                put("errorMessage", reason)
                put("errorCode", code)
            }
            try {
                sendControlJson(recvSess.endpointId, failJson)
            } catch (_: Exception) {
            }
        }

        scope.launch(Dispatchers.Main) {
            val failed = (_endpointTransfers.value[recvSess.endpointId] ?: _activeTransfer.value ?: ActiveTransfer(
                id = recvSess.transferId,
                senderName = recvSess.senderName,
                receiverName = currentDeviceName,
                recipientEndpointId = recvSess.endpointId,
                items = emptyList(),
                totalBytes = recvSess.totalBytes,
                direction = TransferDirection.RECEIVE
            )).copy(
                status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED,
                pipelineStage = TransferPipelineStage.FAILED,
                isIntegrityVerified = false,
                errorMessage = reason,
                statusDetail = reason,
                lastErrorCode = errorCode
            )
            updateTransferState(recvSess.endpointId, failed)
            _statusMessage.value = reason
            activeReceiveSession = null
            onTransferFinishedListener?.invoke(failed)
        }
    }

    private fun updateReceiverProgress(recvSess: ReceiveSession, extraTextBytes: Long = 0L) {
        val fileBytes = recvSess.payloadBytesProgress.values.sum()
        val textVerifiedBytes = recvSess.verifiedItems.values
            .filter { it.type == ContentType.TEXT || it.type == ContentType.URL }
            .sumOf { it.sizeBytes }
        val totalTransferred = (fileBytes + textVerifiedBytes + extraTextBytes).coerceAtMost(recvSess.totalBytes)
        val progress = if (recvSess.totalBytes > 0L) {
            (totalTransferred.toFloat() / recvSess.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val elapsedSec = ((System.currentTimeMillis() - recvSess.startTimeMs).coerceAtLeast(200L)) / 1000f
        val speedMBps = (totalTransferred / 1_000_000f) / elapsedSec

        scope.launch(Dispatchers.Main) {
            val current = _endpointTransfers.value[recvSess.endpointId] ?: _activeTransfer.value
            if (current != null && current.id == recvSess.transferId &&
                current.status == TransferStatus.TRANSFERRING
            ) {
                updateTransferState(
                    recvSess.endpointId,
                    current.copy(
                        progress = progress,
                        bytesTransferred = totalTransferred,
                        speedMbps = speedMBps
                    )
                )
            }
        }
    }

    private fun cleanupReceivePayloads(recvSess: ReceiveSession) {
        recvSess.receivedFilePayloads.values.forEach { payload ->
            IncomingPayloadStorage.safeDeletePayload(context, payload)
        }
    }

    override fun cancelTransfer() {
        val allSends = sendSessionsByTransferId.values.toList()
        if (allSends.isNotEmpty()) {
            allSends.forEach { cancelSendSession(it) }
            _statusMessage.value = "Active send transfer(s) cancelled"
            return
        }

        val recvSess = activeReceiveSession
        if (recvSess != null) {
            recvSess.receivedFilePayloads.keys.forEach { payloadId ->
                runCatching { connectionsClient.cancelPayload(payloadId) }
            }
            try {
                sendControlJson(
                    recvSess.endpointId,
                    JSONObject().apply {
                        put("type", "TRANSFER_CANCEL")
                        put("transferId", recvSess.transferId)
                    }
                )
            } catch (_: Exception) {
            }
            failReceiveSession(recvSess, "Transfer cancelled by receiver", isCancelled = true)
            return
        }

        _activeTransfer.value = _activeTransfer.value?.copy(
            status = TransferStatus.CANCELLED,
            pipelineStage = TransferPipelineStage.FAILED,
            statusDetail = "Transfer cancelled"
        )
    }

    override fun cancelTransferForEndpoint(endpointId: String) {
        val sendSess = sendSessionsByTransferId.values.find { it.endpointId == endpointId }
        if (sendSess != null) {
            cancelSendSession(sendSess)
            return
        }
        val recvSess = activeReceiveSession
        if (recvSess != null && recvSess.endpointId == endpointId) {
            cancelTransfer()
        }
    }

    private fun cancelSendSession(sendSess: SendSession) {
        sendSess.isCancelledOrFailed = true
        sendSess.activePayloadIds.forEach { payloadId ->
            runCatching { connectionsClient.cancelPayload(payloadId) }
        }
        try {
            sendControlJson(
                sendSess.endpointId,
                JSONObject().apply {
                    put("type", "TRANSFER_CANCEL")
                    put("transferId", sendSess.transferId)
                }
            )
        } catch (_: Exception) {
        }
        val cancelled = (_endpointTransfers.value[sendSess.endpointId] ?: _activeTransfer.value)?.copy(
            status = TransferStatus.CANCELLED,
            pipelineStage = TransferPipelineStage.FAILED,
            statusDetail = "Transfer cancelled by user",
            errorMessage = "Cancelled by user"
        )
        if (cancelled != null) {
            updateTransferState(sendSess.endpointId, cancelled)
            sendSess.onComplete(cancelled)
            onTransferFinishedListener?.invoke(cancelled)
        }
        cleanupSendSession(sendSess)
    }

    override fun retryTransfer() {
        val targets = lastTargetDevices
        val items = lastTransferItems
        val onComplete = lastOnComplete
        if (targets.isNotEmpty() && items.isNotEmpty() && onComplete != null) {
            _statusMessage.value = "Retrying transfer to ${targets.joinToString { it.name }}..."
            startMultiRecipientTransfer(targets, items, currentDeviceName, onComplete)
        } else {
            _statusMessage.value = "Nothing to retry."
        }
    }

    // =========================================================================
    // CONNECTION LIFECYCLE CALLBACK (MULTI-PEER AUTH HANDSHAKE)
    // =========================================================================
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with $endpointId (${connectionInfo.endpointName})")
            val session = ConnectionSession(
                endpointId = endpointId,
                endpointName = connectionInfo.endpointName,
                authDigits = connectionInfo.authenticationDigits,
                isIncoming = connectionInfo.isIncomingConnection,
                status = SessionStatus.INITIATED
            )
            pendingSessionMap[endpointId] = session
            syncPeerLists()

            _statusMessage.value = "Verify 6-digit code ${connectionInfo.authenticationDigits} with ${connectionInfo.endpointName}"
            diagnostics?.recordLog(
                category = DiagnosticCategory.NEARBY,
                tag = TAG,
                message = "Handshake initiated with '${connectionInfo.endpointName}' [$endpointId] (incoming=${connectionInfo.isIncomingConnection})"
            )
            updateDeviceConnectionState(
                endpointId,
                DeviceConnectionState.AUTHENTICATING,
                connectionInfo.endpointName,
                "Verify code ${connectionInfo.authenticationDigits}"
            )
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val session = pendingSessionMap.remove(endpointId)
            val endpointName = session?.endpointName
                ?: _discoveredDevices.value.find { it.endpointId == endpointId }?.name
                ?: "Remote Device"

            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                Log.d(TAG, "Connected to $endpointId ($endpointName)")
                val connected = DeviceItem(
                    endpointId = endpointId,
                    name = endpointName,
                    deviceType = DeviceType.PHONE,
                    connectionState = DeviceConnectionState.CONNECTED,
                    statusDetail = "Authenticated · P2P Cluster Active"
                )
                connectedDeviceMap[endpointId] = connected
                syncPeerLists()
                diagnostics?.recordRecentDeviceConnection(endpointName, endpointId)
                diagnostics?.recordLog(
                    category = DiagnosticCategory.NEARBY,
                    tag = TAG,
                    message = "Authenticated & connected to '$endpointName' [$endpointId] (total connected=${connectedDeviceMap.size})"
                )
                _statusMessage.value = "Connected to $endpointName (${connectedDeviceMap.size} peer(s) active)"
                updateDeviceConnectionState(endpointId, DeviceConnectionState.CONNECTED, endpointName, "Connected & authenticated")
            } else {
                val friendlyReason = when (resolution.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                        "Connection rejected by $endpointName."
                    ConnectionsStatusCodes.STATUS_ENDPOINT_IO_ERROR ->
                        "Bluetooth/Wi-Fi I/O error connecting to $endpointName. Move devices closer and retry."
                    ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ->
                        "Bluetooth radio error. Ensure Bluetooth is turned on."
                    ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN ->
                        "Receiver unavailable or out of range. Refresh discovery and retry."
                    else ->
                        "Connection failed (${resolution.status.statusMessage ?: resolution.status.statusCode})."
                }
                val code = if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                    DiagnosticCodes.GS_NET_004
                } else {
                    DiagnosticCodes.GS_NET_002
                }
                connectedDeviceMap.remove(endpointId)
                syncPeerLists()
                _statusMessage.value = friendlyReason
                diagnostics?.recordError(
                    code = code,
                    category = DiagnosticCategory.NEARBY,
                    sourceComponent = TAG,
                    operation = "onConnectionResult",
                    summary = friendlyReason,
                    technicalMessage = "statusCode=${resolution.status.statusCode}, msg=${resolution.status.statusMessage}",
                    relatedEndpointId = endpointId
                )
                updateDeviceConnectionState(endpointId, DeviceConnectionState.FAILED, endpointName, friendlyReason)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val prevName = connectedDeviceMap.remove(endpointId)?.name
                ?: pendingSessionMap.remove(endpointId)?.endpointName
                ?: endpointId
            syncPeerLists()

            if (_incomingTransferOffer.value?.endpointId == endpointId) {
                _incomingTransferOffer.value = null
                pendingOfferReceived = null
            }

            // Fail only send session(s) bound to this disconnected endpoint; keep other concurrent transfers alive!
            sendSessionsByTransferId.values.filter { it.endpointId == endpointId && !it.isCancelledOrFailed }
                .forEach { sendSess ->
                    sendSess.isCancelledOrFailed = true
                    diagnostics?.recordError(
                        code = DiagnosticCodes.GS_NET_003,
                        category = DiagnosticCategory.NEARBY,
                        sourceComponent = TAG,
                        operation = "onDisconnected",
                        summary = "$prevName disconnected unexpectedly during send transfer",
                        relatedTransferId = sendSess.transferId,
                        relatedEndpointId = endpointId
                    )
                    val failed = (_endpointTransfers.value[endpointId] ?: _activeTransfer.value)?.copy(
                        status = TransferStatus.FAILED,
                        pipelineStage = TransferPipelineStage.FAILED,
                        errorMessage = "$prevName disconnected unexpectedly",
                        statusDetail = "Connection lost during transfer",
                        lastErrorCode = DiagnosticCodes.GS_NET_003
                    )
                    if (failed != null) {
                        updateTransferState(endpointId, failed)
                        sendSess.onComplete(failed)
                        onTransferFinishedListener?.invoke(failed)
                    }
                    cleanupSendSession(sendSess)
                }

            val recvSess = activeReceiveSession
            if (recvSess != null && recvSess.endpointId == endpointId && !recvSess.isFinishedOrCancelled) {
                failReceiveSession(
                    recvSess,
                    reason = "Sender '$prevName' disconnected during transfer",
                    errorCode = DiagnosticCodes.GS_NET_003
                )
            }

            diagnostics?.recordLog(
                category = DiagnosticCategory.NEARBY,
                tag = TAG,
                message = "Disconnected from '$prevName' [$endpointId] (remaining connected=${connectedDeviceMap.size})"
            )
            _statusMessage.value = "Disconnected from $prevName"
            updateDeviceConnectionState(endpointId, DeviceConnectionState.DISCONNECTED, prevName, "Disconnected")
        }
    }

    // =========================================================================
    // ENDPOINT DISCOVERY CALLBACK
    // =========================================================================
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            val isAlreadyConnected = connectedDeviceMap.containsKey(endpointId)
            val newDevice = DeviceItem(
                endpointId = endpointId,
                name = info.endpointName,
                deviceType = DeviceType.PHONE,
                connectionState = if (isAlreadyConnected) DeviceConnectionState.CONNECTED else DeviceConnectionState.DISCOVERED,
                statusDetail = if (isAlreadyConnected) "Connected & authenticated" else "Ready to connect"
            )
            if (isAlreadyConnected) {
                connectedDeviceMap[endpointId] = newDevice
                syncPeerLists()
            }
            val currentList = _discoveredDevices.value.toMutableList()
            currentList.removeAll { it.endpointId == endpointId }
            currentList.add(newDevice)
            _discoveredDevices.value = currentList
            _statusMessage.value = "Discovered peer: ${info.endpointName} (${currentList.size} visible)"
            diagnostics?.recordLog(
                category = DiagnosticCategory.NEARBY,
                tag = TAG,
                message = "Endpoint found: '${info.endpointName}' [$endpointId]"
            )
        }

        override fun onEndpointLost(endpointId: String) {
            val lostDevice = _discoveredDevices.value.find { it.endpointId == endpointId }
            val lostName = lostDevice?.name ?: connectedDeviceMap[endpointId]?.name
            Log.d(TAG, "Endpoint lost: $endpointId ($lostName)")

            if (connectedDeviceMap.containsKey(endpointId)) {
                // Keep active connected peer visible with note until transport disconnects
                updateDeviceConnectionState(endpointId, DeviceConnectionState.CONNECTED, lostName, "Connected (beacon paused)")
            } else {
                _discoveredDevices.value = _discoveredDevices.value.filterNot { it.endpointId == endpointId }
            }

            pendingSessionMap[endpointId]?.let { sess ->
                if (!connectedDeviceMap.containsKey(endpointId)) {
                    pendingSessionMap.remove(endpointId)
                    syncPeerLists()
                }
            }

            _statusMessage.value = if (!lostName.isNullOrBlank()) {
                "Peer '$lostName' went out of range."
            } else {
                "Peer endpoint lost: $endpointId"
            }
            diagnostics?.recordLog(
                category = DiagnosticCategory.NEARBY,
                tag = TAG,
                message = "Endpoint lost: '${lostName ?: endpointId}' [$endpointId]"
            )
        }
    }

    // =========================================================================
    // ADVERTISING & DISCOVERY
    // =========================================================================
    override fun startAdvertising(myDeviceName: String) {
        if (_isAdvertising.value) return
        currentDeviceName = myDeviceName.ifBlank { "GestoShare Receiver" }

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        _statusMessage.value = "Starting multi-peer beacon as '$currentDeviceName'..."
        connectionsClient.startAdvertising(
            currentDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            _isAdvertising.value = true
            _statusMessage.value = "Receive Mode Active: '$currentDeviceName' visible to nearby peers"
            diagnostics?.recordLog(
                category = DiagnosticCategory.NEARBY,
                tag = TAG,
                message = "Advertising started as '$currentDeviceName' (P2P_CLUSTER)"
            )
        }.addOnFailureListener { e ->
            _isAdvertising.value = false
            val msg = "Failed to start Receive Mode: ${e.localizedMessage ?: e.javaClass.simpleName}"
            _statusMessage.value = msg
            diagnostics?.recordException(
                throwable = e,
                sourceComponent = TAG,
                operation = "startAdvertising",
                defaultCode = DiagnosticCodes.GS_NET_002,
                defaultCategory = DiagnosticCategory.NEARBY
            )
        }
    }

    override fun stopAdvertising() {
        if (!_isAdvertising.value) return
        try {
            connectionsClient.stopAdvertising()
            _isAdvertising.value = false
            _statusMessage.value = "Receive Mode Stopped"
            diagnostics?.recordLog(category = DiagnosticCategory.NEARBY, tag = TAG, message = "Advertising stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }

    override fun toggleAdvertising(myDeviceName: String) {
        if (_isAdvertising.value) stopAdvertising() else startAdvertising(myDeviceName)
    }

    override fun startDiscovery(myDeviceName: String) {
        if (_isDiscovering.value) return
        currentDeviceName = myDeviceName.ifBlank { "GestoShare Sender" }

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        // Preserve any currently connected devices in the list while refreshing undiscovered peers
        _discoveredDevices.value = connectedDeviceMap.values.toList()
        _statusMessage.value = "Scanning for nearby GestoShare devices (P2P_CLUSTER)..."

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            _isDiscovering.value = true
            _statusMessage.value = "Scanning nearby devices (supports multiple concurrent peers)..."
            diagnostics?.recordLog(
                category = DiagnosticCategory.NEARBY,
                tag = TAG,
                message = "Discovery started (P2P_CLUSTER)"
            )
        }.addOnFailureListener { e ->
            _isDiscovering.value = false
            val msg = "Discovery failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            _statusMessage.value = msg
            diagnostics?.recordException(
                throwable = e,
                sourceComponent = TAG,
                operation = "startDiscovery",
                defaultCode = DiagnosticCodes.GS_NET_002,
                defaultCategory = DiagnosticCategory.NEARBY
            )
        }
    }

    override fun stopDiscovery() {
        if (!_isDiscovering.value) return
        try {
            connectionsClient.stopDiscovery()
            _isDiscovering.value = false
            _statusMessage.value = "Discovery stopped"
            diagnostics?.recordLog(category = DiagnosticCategory.NEARBY, tag = TAG, message = "Discovery stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }

    // =========================================================================
    // CONNECTION REQUEST & AUTH HANDSHAKE
    // =========================================================================
    override fun requestConnection(device: DeviceItem, myDeviceName: String) {
        if (connectedDeviceMap.containsKey(device.endpointId)) {
            _statusMessage.value = "Already connected to ${device.name}."
            return
        }

        currentDeviceName = myDeviceName.ifBlank { "GestoShare Peer" }
        _statusMessage.value = "Requesting connection to ${device.name}..."
        updateDeviceConnectionState(device.endpointId, DeviceConnectionState.CONNECTING, device.name, "Requesting link...")

        connectionsClient.requestConnection(
            currentDeviceName,
            device.endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            _statusMessage.value = "Connection requested to ${device.name}. Awaiting 6-digit code..."
        }.addOnFailureListener { e ->
            val msg = "Connection request to ${device.name} failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            updateDeviceConnectionState(device.endpointId, DeviceConnectionState.FAILED, device.name, msg)
            _statusMessage.value = msg
            diagnostics?.recordException(
                throwable = e,
                sourceComponent = TAG,
                operation = "requestConnection",
                defaultCode = DiagnosticCodes.GS_NET_002,
                defaultCategory = DiagnosticCategory.NEARBY,
                relatedEndpointId = device.endpointId
            )
        }
    }

    override fun acceptConnection(endpointId: String) {
        _statusMessage.value = "Accepting security handshake with $endpointId..."
        pendingSessionMap[endpointId]?.let {
            pendingSessionMap[endpointId] = it.copy(status = SessionStatus.ACCEPTED_LOCALLY)
            syncPeerLists()
        }

        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                _statusMessage.value = "Accepted locally. Waiting for peer confirmation..."
            }
            .addOnFailureListener { e ->
                _statusMessage.value = "Failed to accept: ${e.localizedMessage ?: e.javaClass.simpleName}"
                diagnostics?.recordException(
                    throwable = e,
                    sourceComponent = TAG,
                    operation = "acceptConnection",
                    defaultCode = DiagnosticCodes.GS_NET_002,
                    defaultCategory = DiagnosticCategory.NEARBY,
                    relatedEndpointId = endpointId
                )
            }
    }

    override fun rejectConnection(endpointId: String) {
        _statusMessage.value = "Rejecting connection with $endpointId..."
        pendingSessionMap.remove(endpointId)
        syncPeerLists()
        connectionsClient.rejectConnection(endpointId)
            .addOnSuccessListener {
                _statusMessage.value = "Connection rejected"
                updateDeviceConnectionState(endpointId, DeviceConnectionState.DISCONNECTED, detail = "Rejected locally")
            }
    }

    override fun disconnectDevice(endpointId: String) {
        try {
            connectionsClient.disconnectFromEndpoint(endpointId)
            val removed = connectedDeviceMap.remove(endpointId)
            pendingSessionMap.remove(endpointId)
            syncPeerLists()
            _statusMessage.value = "Disconnected from ${removed?.name ?: endpointId}"
            updateDeviceConnectionState(endpointId, DeviceConnectionState.DISCONNECTED, detail = "Disconnected by user")
            diagnostics?.recordLog(
                category = DiagnosticCategory.NEARBY,
                tag = TAG,
                message = "User disconnected endpoint '${removed?.name ?: endpointId}' [$endpointId]"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from $endpointId", e)
        }
    }

    override fun forgetDevice(endpointId: String) {
        runCatching { connectionsClient.disconnectFromEndpoint(endpointId) }
        val removed = connectedDeviceMap.remove(endpointId)
        pendingSessionMap.remove(endpointId)
        syncPeerLists()
        _discoveredDevices.value = _discoveredDevices.value.filterNot { it.endpointId == endpointId }
        _endpointTransfers.value = _endpointTransfers.value - endpointId
        if (!removed?.name.isNullOrBlank()) {
            diagnostics?.forgetRecentDevice(removed!!.name)
        }
        _statusMessage.value = "Forgot device ${removed?.name ?: endpointId}"
    }

    override fun stopAllEndpoints() {
        try {
            connectionsClient.stopAllEndpoints()
            connectedDeviceMap.clear()
            pendingSessionMap.clear()
            syncPeerLists()
            _incomingTransferOffer.value = null
            _discoveredDevices.value = emptyList()
            _isAdvertising.value = false
            _isDiscovering.value = false
            _statusMessage.value = "All connections and discovery stopped."
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping all endpoints", e)
        }
    }

    // =========================================================================
    // MESSAGING & PING
    // =========================================================================
    override fun sendTextMessage(message: String): Boolean {
        val target = _connectedDevice.value ?: run {
            _statusMessage.value = "Cannot send: No device connected."
            return false
        }
        return sendTextMessageTo(target.endpointId, message)
    }

    override fun sendTextMessageTo(endpointId: String, message: String): Boolean {
        val target = connectedDeviceMap[endpointId] ?: run {
            _statusMessage.value = "Cannot send: Peer $endpointId not connected."
            return false
        }
        return try {
            val bytes = message.toByteArray(StandardCharsets.UTF_8)
            connectionsClient.sendPayload(target.endpointId, Payload.fromBytes(bytes))
            val outgoing = NearbyMessage(
                senderEndpointId = "ME",
                senderName = currentDeviceName,
                messageText = message,
                isIncoming = false,
                isAcknowledgment = false
            )
            _receivedMessages.value = _receivedMessages.value + outgoing
            _statusMessage.value = "Sent message to ${target.name}"
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message to $endpointId", e)
            _statusMessage.value = "Send error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            false
        }
    }

    override fun sendPingTest(): Boolean {
        return sendTextMessage("Hello from GestoShare (${System.currentTimeMillis() % 10000})")
    }

    private fun updateDeviceConnectionState(
        endpointId: String,
        state: DeviceConnectionState,
        nameOverride: String? = null,
        detail: String? = null
    ) {
        val existing = _discoveredDevices.value.find { it.endpointId == endpointId }
        if (existing != null) {
            _discoveredDevices.value = _discoveredDevices.value.map { device ->
                if (device.endpointId == endpointId) {
                    device.copy(
                        connectionState = state,
                        name = nameOverride ?: device.name,
                        statusDetail = detail ?: device.statusDetail,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                } else {
                    device
                }
            }
        } else if (nameOverride != null) {
            _discoveredDevices.value = _discoveredDevices.value + DeviceItem(
                endpointId = endpointId,
                name = nameOverride,
                deviceType = DeviceType.PHONE,
                connectionState = state,
                statusDetail = detail,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }
    }

    override fun retryDeviceConnection(deviceId: String) {
        val target = _discoveredDevices.value.find { it.endpointId == deviceId }
        if (target != null) {
            requestConnection(target, currentDeviceName)
        }
    }
}
