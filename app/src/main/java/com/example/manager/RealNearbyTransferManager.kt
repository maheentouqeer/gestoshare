package com.example.manager

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.ActiveTransfer
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.DeviceType
import com.example.model.IncomingTransferOffer
import com.example.model.NearbyMessage
import com.example.model.SessionStatus
import com.example.model.TransferDirection
import com.example.model.TransferItemMetadata
import com.example.model.TransferStatus
import com.example.util.HashUtil
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Production Nearby Connections manager supporting:
 * - Phase 3: Offline P2P_POINT_TO_POINT discovery, advertising, 6-digit auth handshake & text ping
 * - Phase 4: Real file, photo, video, PDF, text note & URL transfer with receiver consent
 * - Phase 5: Streaming SHA-256 verification before marking transfer complete + Room history trigger
 */
class RealNearbyTransferManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : NearbyTransferManager {

    companion object {
        private const val TAG = "RealNearbyTransfer"
        const val SERVICE_ID = "com.gestoshare.app.P2P_SERVICE"
        val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
        private const val CTRL_PREFIX = "GESTOSHARE_CTRL:"
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    private val _discoveredDevices = MutableStateFlow<List<DeviceItem>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceItem>> = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _pendingSession = MutableStateFlow<ConnectionSession?>(null)
    override val pendingSession: StateFlow<ConnectionSession?> = _pendingSession.asStateFlow()

    private val _connectedDevice = MutableStateFlow<DeviceItem?>(null)
    override val connectedDevice: StateFlow<DeviceItem?> = _connectedDevice.asStateFlow()

    private val _receivedMessages = MutableStateFlow<List<NearbyMessage>>(emptyList())
    override val receivedMessages: StateFlow<List<NearbyMessage>> = _receivedMessages.asStateFlow()

    private val _incomingTransferOffer = MutableStateFlow<IncomingTransferOffer?>(null)
    override val incomingTransferOffer: StateFlow<IncomingTransferOffer?> = _incomingTransferOffer.asStateFlow()

    private val _activeTransfer = MutableStateFlow<ActiveTransfer?>(null)
    override val activeTransfer: StateFlow<ActiveTransfer?> = _activeTransfer.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready for P2P connection")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isNearbyAvailable = MutableStateFlow(true)
    override val isNearbyAvailable: StateFlow<Boolean> = _isNearbyAvailable.asStateFlow()

    private var currentDeviceName: String = "GestoShare Device"
    private var onTransferFinishedListener: ((ActiveTransfer) -> Unit)? = null

    // Last transfer inputs for retryTransfer()
    private var lastTargetDevice: DeviceItem? = null
    private var lastTransferItems: List<ContentItem> = emptyList()
    private var lastOnComplete: ((ActiveTransfer) -> Unit)? = null

    // Active outgoing session state
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

    // Active incoming session state
    private data class ReceiveSession(
        val transferId: String,
        val endpointId: String,
        val senderName: String,
        val totalBytes: Long,
        val expectedItems: Map<Int, TransferItemMetadata>,
        val startTimeMs: Long,
        val payloadMetaByPayloadId: ConcurrentHashMap<Long, TransferItemMetadata> = ConcurrentHashMap(),
        val receivedFilePayloads: ConcurrentHashMap<Long, Payload> = ConcurrentHashMap(),
        val payloadBytesProgress: ConcurrentHashMap<Long, Long> = ConcurrentHashMap(),
        val verifiedItems: ConcurrentHashMap<Int, ContentItem> = ConcurrentHashMap(),
        val verifiedHashes: ConcurrentHashMap<Int, String> = ConcurrentHashMap(),
        var latestSavedUri: Uri? = null,
        var latestSavedMime: String? = null,
        @Volatile var isFinishedOrCancelled: Boolean = false
    )

    private var pendingSendSession: SendSession? = null
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
            _statusMessage.value = "Google Play services unavailable or outdated on this device."
            Log.e(TAG, "Google Play Services not available: $resultCode")
        } else {
            _isNearbyAvailable.value = true
        }
    }

    // =========================================================================
    // PAYLOAD CALLBACK (Phase 3 Messages + Phase 4/5 Control & File Streams)
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
                        handlePhase3TextMessage(endpointId, text)
                    }
                }

                Payload.Type.FILE -> {
                    val session = activeReceiveSession
                    if (session == null || session.isFinishedOrCancelled) {
                        Log.w(TAG, "Received unexpected FILE payload ${payload.id}, deleting.")
                        try {
                            payload.asFile()?.asJavaFile()?.delete()
                        } catch (_: Exception) {
                        }
                        return
                    }
                    session.receivedFilePayloads[payload.id] = payload
                    Log.d(TAG, "Incoming FILE payload registered: id=${payload.id}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val sendSess = pendingSendSession
            if (sendSess != null && sendSess.endpointId == endpointId && sendSess.activePayloadIds.contains(update.payloadId)) {
                handleSenderPayloadUpdate(sendSess, update)
                return
            }

            val recvSess = activeReceiveSession
            if (recvSess != null && recvSess.endpointId == endpointId &&
                (recvSess.receivedFilePayloads.containsKey(update.payloadId) || recvSess.payloadMetaByPayloadId.containsKey(update.payloadId))
            ) {
                handleReceiverPayloadUpdate(recvSess, update)
            }
        }
    }

    private fun handlePhase3TextMessage(endpointId: String, text: String) {
        val senderName = _connectedDevice.value?.name ?: endpointId
        Log.d(TAG, "Payload message from $endpointId: $text")

        if (text.startsWith("ACK: ")) {
            val ackMessage = NearbyMessage(
                senderEndpointId = endpointId,
                senderName = senderName,
                messageText = text,
                isIncoming = true,
                isAcknowledgment = true
            )
            _receivedMessages.value = _receivedMessages.value + ackMessage
            _statusMessage.value = "Received Acknowledgment from $senderName"
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

            val ackPayloadText = "ACK: Received '$text'"
            try {
                connectionsClient.sendPayload(
                    endpointId,
                    Payload.fromBytes(ackPayloadText.toByteArray(StandardCharsets.UTF_8))
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error returning ACK", e)
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
                    val senderName = json.optString("senderName", _connectedDevice.value?.name ?: "Peer")
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
                        Log.w(TAG, "Ignoring/rejecting duplicate TRANSFER_OFFER $transferId while another receive is active")
                        try {
                            sendControlJson(
                                endpointId,
                                JSONObject().apply {
                                    put("type", "TRANSFER_REJECT")
                                    put("transferId", transferId)
                                    put("reason", "Receiver busy with another transfer")
                                }
                            )
                        } catch (_: Exception) {
                        }
                        return
                    }
                    pendingOfferReceived = offer
                    _incomingTransferOffer.value = offer
                    _statusMessage.value = "Incoming transfer request from $senderName"
                }

                "TRANSFER_ACCEPT" -> {
                    val transferId = json.getString("transferId")
                    val sendSess = pendingSendSession ?: return
                    if (sendSess.transferId == transferId && !sendSess.isCancelledOrFailed) {
                        beginSendingAcceptedItems(sendSess)
                    }
                }

                "TRANSFER_REJECT" -> {
                    val transferId = json.getString("transferId")
                    val rejectReason = json.optString("reason", "Declined by receiver")
                    val sendSess = pendingSendSession ?: return
                    if (sendSess.transferId == transferId) {
                        sendSess.isCancelledOrFailed = true
                        val cancelled = _activeTransfer.value?.copy(
                            status = TransferStatus.CANCELLED,
                            statusDetail = "${sendSess.targetDevice.name}: $rejectReason",
                            errorMessage = rejectReason
                        ) ?: ActiveTransfer(
                            id = transferId,
                            senderName = sendSess.senderName,
                            receiverName = sendSess.targetDevice.name,
                            items = sendSess.items,
                            totalBytes = sendSess.totalBytes,
                            status = TransferStatus.CANCELLED,
                            direction = TransferDirection.SEND,
                            errorMessage = rejectReason,
                            statusDetail = "${sendSess.targetDevice.name}: $rejectReason"
                        )
                        _activeTransfer.value = cancelled
                        _statusMessage.value = "Transfer declined by ${sendSess.targetDevice.name}: $rejectReason"
                        pendingSendSession = null
                        sendSess.onComplete(cancelled)
                        onTransferFinishedListener?.invoke(cancelled)
                    }
                }

                "TRANSFER_TEXT_ITEM" -> {
                    val transferId = json.getString("transferId")
                    val recvSess = activeReceiveSession ?: return
                    if (recvSess.transferId == transferId && !recvSess.isFinishedOrCancelled) {
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
                    if (recvSess.transferId == transferId && !recvSess.isFinishedOrCancelled) {
                        val payloadId = json.getLong("payloadId")
                        val meta = TransferItemMetadata(
                            itemIndex = json.getInt("itemIndex"),
                            name = json.getString("name"),
                            mimeType = json.optString("mimeType", "application/octet-stream"),
                            sizeBytes = json.optLong("sizeBytes", 0L),
                            contentType = runCatching {
                                ContentType.valueOf(json.optString("contentType", ContentType.GENERIC_FILE.name))
                            }.getOrDefault(ContentType.GENERIC_FILE),
                            sha256 = json.optString("sha256", "")
                        )
                        recvSess.payloadMetaByPayloadId[payloadId] = meta
                        Log.d(TAG, "Mapped payloadId=$payloadId -> ${meta.name} (sha=${meta.sha256.take(12)})")
                    }
                }

                "TRANSFER_VERIFIED" -> {
                    val transferId = json.getString("transferId")
                    val verified = json.optBoolean("verified", false)
                    val shaSummary = json.optString("sha256Summary", "")
                    val errorMsg = json.optString("errorMessage", "Receiver verification failed")
                    val sendSess = pendingSendSession ?: return
                    if (sendSess.transferId == transferId) {
                        val elapsedSeconds = ((System.currentTimeMillis() - sendSess.startTimeMs).coerceAtLeast(250L)) / 1000f
                        val finalSpeed = (sendSess.totalBytes / 1_000_000f) / elapsedSeconds
                        val finalized = if (verified) {
                            (_activeTransfer.value ?: ActiveTransfer(
                                id = transferId,
                                senderName = sendSess.senderName,
                                receiverName = sendSess.targetDevice.name,
                                items = sendSess.items,
                                totalBytes = sendSess.totalBytes,
                                direction = TransferDirection.SEND
                            )).copy(
                                progress = 1.0f,
                                bytesTransferred = sendSess.totalBytes,
                                speedMbps = finalSpeed,
                                status = TransferStatus.SUCCESS,
                                sha256Checksum = shaSummary.ifBlank { sendSess.shaSummary },
                                isIntegrityVerified = true,
                                statusDetail = "SHA-256 verified by ${sendSess.targetDevice.name}"
                            )
                        } else {
                            (_activeTransfer.value ?: ActiveTransfer(
                                id = transferId,
                                senderName = sendSess.senderName,
                                receiverName = sendSess.targetDevice.name,
                                items = sendSess.items,
                                totalBytes = sendSess.totalBytes,
                                direction = TransferDirection.SEND
                            )).copy(
                                status = TransferStatus.FAILED,
                                isIntegrityVerified = false,
                                errorMessage = errorMsg,
                                statusDetail = errorMsg
                            )
                        }
                        _activeTransfer.value = finalized
                        _statusMessage.value = finalized.statusDetail ?: finalized.status.label
                        pendingSendSession = null
                        sendSess.onComplete(finalized)
                        onTransferFinishedListener?.invoke(finalized)
                    }
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
                        val cancelled = _activeTransfer.value?.copy(
                            status = TransferStatus.CANCELLED,
                            statusDetail = "Cancelled by ${recvSess.senderName}",
                            errorMessage = "Sender cancelled transfer"
                        )
                        if (cancelled != null) {
                            _activeTransfer.value = cancelled
                            onTransferFinishedListener?.invoke(cancelled)
                        }
                        activeReceiveSession = null
                        _statusMessage.value = "Transfer cancelled by peer"
                    }
                    val sendSess = pendingSendSession
                    if (sendSess != null && sendSess.transferId == transferId) {
                        sendSess.isCancelledOrFailed = true
                        val cancelled = _activeTransfer.value?.copy(
                            status = TransferStatus.CANCELLED,
                            statusDetail = "Cancelled by ${sendSess.targetDevice.name}",
                            errorMessage = "Receiver cancelled transfer"
                        )
                        if (cancelled != null) {
                            _activeTransfer.value = cancelled
                            sendSess.onComplete(cancelled)
                            onTransferFinishedListener?.invoke(cancelled)
                        }
                        pendingSendSession = null
                        _statusMessage.value = "Transfer cancelled by receiver"
                    }
                }

                else -> Log.w(TAG, "Unknown control type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling control message", e)
        }
    }

    private fun sendControlJson(endpointId: String, json: JSONObject) {
        val raw = (CTRL_PREFIX + json.toString()).toByteArray(StandardCharsets.UTF_8)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(raw))
    }

    // =========================================================================
    // PHASE 4 & 5: SENDER FLOW
    // =========================================================================
    override fun startTransfer(
        targetDevice: DeviceItem,
        items: List<ContentItem>,
        myDeviceName: String,
        onComplete: (ActiveTransfer) -> Unit
    ) {
        if (items.isEmpty()) {
            _statusMessage.value = "No content staged to transfer."
            return
        }

        if (pendingSendSession != null) {
            _statusMessage.value = "Duplicate transfer request blocked: transfer already active."
            return
        }

        lastTargetDevice = targetDevice
        lastTransferItems = items
        lastOnComplete = onComplete

        val connected = _connectedDevice.value
        if (connected == null || connected.endpointId != targetDevice.endpointId) {
            val failed = ActiveTransfer(
                senderName = myDeviceName,
                receiverName = targetDevice.name,
                items = items,
                totalBytes = items.sumOf { it.sizeBytes },
                status = TransferStatus.FAILED,
                direction = TransferDirection.SEND,
                errorMessage = "Receiver '${targetDevice.name}' unavailable or not connected. Complete Nearby pairing first.",
                statusDetail = "No active Nearby connection to ${targetDevice.name}"
            )
            _activeTransfer.value = failed
            _statusMessage.value = failed.errorMessage ?: "Connection required"
            onComplete(failed)
            onTransferFinishedListener?.invoke(failed)
            return
        }

        scope.launch {
            _activeTransfer.value = ActiveTransfer(
                senderName = myDeviceName,
                receiverName = targetDevice.name,
                items = items,
                totalBytes = items.sumOf { it.sizeBytes },
                status = TransferStatus.PREPARING,
                direction = TransferDirection.SEND,
                statusDetail = "Computing SHA-256 checksums..."
            )

            val preparedItems = withContext(Dispatchers.IO) {
                prepareItemsForSending(items)
            }

            if (preparedItems == null) {
                val failed = _activeTransfer.value!!.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = "Unable to read one or more selected files.",
                    statusDetail = "Failed preparing source file stream"
                )
                _activeTransfer.value = failed
                onComplete(failed)
                onTransferFinishedListener?.invoke(failed)
                return@launch
            }

            val totalBytes = preparedItems.sumOf { it.sizeBytes.coerceAtLeast(1L) }
            val shaSummary = if (preparedItems.size == 1) {
                preparedItems.first().sha256Hash.orEmpty()
            } else {
                val joined = preparedItems.joinToString(":") { it.sha256Hash.orEmpty() }
                HashUtil.calculateSha256(joined.byteInputStream(StandardCharsets.UTF_8))
            }

            val transferId = UUID.randomUUID().toString()
            val session = SendSession(
                transferId = transferId,
                endpointId = targetDevice.endpointId,
                targetDevice = targetDevice,
                items = preparedItems,
                senderName = myDeviceName,
                totalBytes = totalBytes,
                startTimeMs = System.currentTimeMillis(),
                shaSummary = shaSummary,
                onComplete = onComplete
            )
            pendingSendSession = session

            _activeTransfer.value = ActiveTransfer(
                id = transferId,
                senderName = myDeviceName,
                receiverName = targetDevice.name,
                items = preparedItems,
                progress = 0f,
                bytesTransferred = 0L,
                totalBytes = totalBytes,
                status = TransferStatus.AWAITING_ACCEPTANCE,
                direction = TransferDirection.SEND,
                sha256Checksum = shaSummary.ifBlank { "Pending" },
                statusDetail = "Waiting for ${targetDevice.name} to accept incoming transfer..."
            )
            _statusMessage.value = "Offer sent to ${targetDevice.name}. Awaiting acceptance..."

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
                sendControlJson(targetDevice.endpointId, offerJson)
            } catch (e: Exception) {
                pendingSendSession = null
                val failed = _activeTransfer.value!!.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Failed sending offer",
                    statusDetail = "Offer failed to send"
                )
                _activeTransfer.value = failed
                onComplete(failed)
                onTransferFinishedListener?.invoke(failed)
            }
        }
    }

    private fun prepareItemsForSending(items: List<ContentItem>): List<ContentItem>? {
        val result = mutableListOf<ContentItem>()
        for (item in items) {
            if (item.textContent != null && (item.type == ContentType.TEXT || item.type == ContentType.URL)) {
                val bytes = item.textContent.toByteArray(StandardCharsets.UTF_8)
                val hash = if (!item.sha256Hash.isNullOrBlank()) item.sha256Hash else {
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                    digest.joinToString("") { "%02x".format(it) }
                }
                result.add(item.copy(sizeBytes = bytes.size.toLong(), sha256Hash = hash))
            } else {
                val existingPath = item.cachedFilePath
                val file = if (!existingPath.isNullOrBlank() && File(existingPath).exists()) {
                    File(existingPath)
                } else if (item.uri != null) {
                    val cacheDir = File(context.cacheDir, "outgoing").apply { mkdirs() }
                    val safeName = item.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").ifBlank { "file" }
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
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
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
                    _activeTransfer.value = _activeTransfer.value?.copy(
                        status = TransferStatus.TRANSFERRING,
                        statusDetail = "Streaming ${sendSess.items.size} item(s) over P2P link..."
                    )
                    _statusMessage.value = "Transferring to ${sendSess.targetDevice.name}..."
                }

                // Send text/URL items first
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

                // Send file payloads with metadata mapping
                for ((idx, item) in fileItems) {
                    if (sendSess.isCancelledOrFailed) return@launch
                    val file = File(item.cachedFilePath ?: continue)
                    val filePayload = Payload.fromFile(file)
                    sendSess.activePayloadIds.add(filePayload.id)
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
                        _activeTransfer.value = _activeTransfer.value?.copy(
                            progress = 1.0f,
                            bytesTransferred = sendSess.totalBytes,
                            status = TransferStatus.VERIFYING,
                            statusDetail = "Waiting for receiver SHA-256 verification ACK..."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error streaming transfer items", e)
                if (!sendSess.isCancelledOrFailed) {
                    sendSess.isCancelledOrFailed = true
                    withContext(Dispatchers.Main) {
                        val failed = _activeTransfer.value?.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = e.localizedMessage ?: "Payload send error",
                            statusDetail = "Transfer failed while streaming"
                        )
                        if (failed != null) {
                            _activeTransfer.value = failed
                            sendSess.onComplete(failed)
                            onTransferFinishedListener?.invoke(failed)
                        }
                        pendingSendSession = null
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
                    _activeTransfer.value = _activeTransfer.value?.copy(
                        progress = 1.0f,
                        bytesTransferred = sendSess.totalBytes,
                        status = TransferStatus.VERIFYING,
                        statusDetail = "Bytes delivered. Waiting for receiver SHA-256 verification..."
                    )
                }
            }

            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                sendSess.isCancelledOrFailed = true
                val newStatus = if (update.status == PayloadTransferUpdate.Status.CANCELED) {
                    TransferStatus.CANCELLED
                } else {
                    TransferStatus.FAILED
                }
                val failed = _activeTransfer.value?.copy(
                    status = newStatus,
                    errorMessage = if (newStatus == TransferStatus.CANCELLED) "Transfer cancelled" else "Payload transmission interrupted",
                    statusDetail = "Payload ${newStatus.name.lowercase()} during P2P send"
                )
                if (failed != null) {
                    _activeTransfer.value = failed
                    sendSess.onComplete(failed)
                    onTransferFinishedListener?.invoke(failed)
                }
                pendingSendSession = null
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
            val current = _activeTransfer.value
            if (current != null && current.id == sendSess.transferId &&
                (current.status == TransferStatus.TRANSFERRING || current.status == TransferStatus.AWAITING_ACCEPTANCE)
            ) {
                _activeTransfer.value = current.copy(
                    progress = progress,
                    bytesTransferred = totalTransferred,
                    speedMbps = speedMBps,
                    status = TransferStatus.TRANSFERRING
                )
            }
        }
    }

    // =========================================================================
    // PHASE 4 & 5: RECEIVER FLOW (ACCEPT, SAVE, SHA-256 VERIFY & ACK)
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
            val failed = ActiveTransfer(
                id = offer.transferId,
                senderName = offer.senderName,
                receiverName = currentDeviceName,
                items = offer.items.map {
                    ContentItem(name = it.name, mimeType = it.mimeType, sizeBytes = it.sizeBytes, type = it.contentType)
                },
                totalBytes = offer.totalBytes,
                status = TransferStatus.FAILED,
                direction = TransferDirection.RECEIVE,
                errorMessage = storageMsg,
                statusDetail = storageMsg
            )
            _activeTransfer.value = failed
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

        _activeTransfer.value = ActiveTransfer(
            id = offer.transferId,
            senderName = offer.senderName,
            receiverName = currentDeviceName,
            items = placeholderItems,
            progress = 0f,
            bytesTransferred = 0L,
            totalBytes = session.totalBytes,
            status = TransferStatus.TRANSFERRING,
            direction = TransferDirection.RECEIVE,
            sha256Checksum = offer.items.firstOrNull()?.sha256 ?: "Pending Verification",
            statusDetail = "Receiving from ${offer.senderName}..."
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
        }
    }

    override fun rejectTransferOffer(transferId: String) {
        val offer = _incomingTransferOffer.value ?: pendingOfferReceived ?: return
        if (offer.transferId != transferId) return

        _incomingTransferOffer.value = null
        pendingOfferReceived = null
        _statusMessage.value = "Declined incoming transfer from ${offer.senderName}"

        val rejectJson = JSONObject().apply {
            put("type", "TRANSFER_REJECT")
            put("transferId", transferId)
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
                        "SHA-256 mismatch on text/link '${meta.name}'"
                    )
                    return@launch
                }

                // Persist text/URL to app storage + Downloads/GestoShare as .txt
                val receivedDir = File(context.filesDir, "received").apply { mkdirs() }
                val safeBase = meta.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").take(36).ifBlank { "note" }
                val fileName = "${safeBase}_${System.currentTimeMillis() % 10000}.txt"
                val destFile = File(receivedDir, fileName)
                destFile.writeText(text, StandardCharsets.UTF_8)

                exportToPublicDownloads(destFile, fileName, "text/plain")
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile
                )
                recvSess.latestSavedUri = fileUri
                recvSess.latestSavedMime = if (meta.contentType == ContentType.URL) "text/uri-list" else "text/plain"

                recvSess.verifiedHashes[meta.itemIndex] = actualHash
                recvSess.verifiedItems[meta.itemIndex] = ContentItem(
                    uri = fileUri,
                    name = meta.name,
                    mimeType = meta.mimeType,
                    sizeBytes = textBytes.size.toLong(),
                    textContent = text,
                    type = meta.contentType,
                    cachedFilePath = destFile.absolutePath,
                    sha256Hash = actualHash
                )

                // Also append to phase 3 message feed so URL/note is immediately visible/readable
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
                failReceiveSession(recvSess, e.localizedMessage ?: "Failed saving text item")
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
                updateReceiverProgress(recvSess)
                val payload = recvSess.receivedFilePayloads[update.payloadId] ?: return
                verifyAndSaveIncomingFilePayload(recvSess, payload)
            }

            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                val msg = if (update.status == PayloadTransferUpdate.Status.CANCELED) {
                    "Incoming transfer cancelled"
                } else {
                    "Incoming file stream interrupted"
                }
                failReceiveSession(recvSess, msg, isCancelled = update.status == PayloadTransferUpdate.Status.CANCELED)
            }
        }
    }

    private fun verifyAndSaveIncomingFilePayload(recvSess: ReceiveSession, payload: Payload) {
        scope.launch(Dispatchers.IO) {
            var tempVerifyFile: File? = null
            try {
                // Wait briefly in case TRANSFER_FILE_MAP arrives slightly after FILE payload
                var meta = recvSess.payloadMetaByPayloadId[payload.id]
                var waitAttempts = 0
                while (meta == null && waitAttempts < 40 && !recvSess.isFinishedOrCancelled) {
                    delay(50)
                    meta = recvSess.payloadMetaByPayloadId[payload.id]
                    waitAttempts++
                }

                if (recvSess.isFinishedOrCancelled) {
                    payload.asFile()?.asJavaFile()?.delete()
                    return@launch
                }

                if (meta == null) {
                    payload.asFile()?.asJavaFile()?.delete()
                    failReceiveSession(recvSess, "Missing metadata manifest for payload ${payload.id}")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _activeTransfer.value = _activeTransfer.value?.copy(
                        status = TransferStatus.VERIFYING,
                        statusDetail = "Verifying SHA-256 of ${meta.name}..."
                    )
                }

                val verifyDir = File(context.cacheDir, "incoming_verify").apply { mkdirs() }
                val safeName = meta.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").ifBlank { "received_file" }
                tempVerifyFile = File(verifyDir, "${UUID.randomUUID().toString().take(8)}_$safeName")

                val nearbyPayloadFile = payload.asFile()
                val rawJavaFile = nearbyPayloadFile?.asJavaFile()
                if (rawJavaFile != null && rawJavaFile.exists()) {
                    FileInputStream(rawJavaFile).use { input ->
                        FileOutputStream(tempVerifyFile).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                } else {
                    val pfd = nearbyPayloadFile?.asParcelFileDescriptor()
                    if (pfd != null) {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                            FileOutputStream(tempVerifyFile).use { output ->
                                input.copyTo(output, bufferSize = 64 * 1024)
                            }
                        }
                    } else {
                        failReceiveSession(recvSess, "Unable to read received Nearby payload file")
                        return@launch
                    }
                }

                // Clean up Nearby's staging file
                try {
                    nearbyPayloadFile?.asJavaFile()?.delete()
                } catch (_: Exception) {
                }

                // Validate file size
                if (meta.sizeBytes > 0L && tempVerifyFile.length() != meta.sizeBytes) {
                    tempVerifyFile.delete()
                    failReceiveSession(
                        recvSess,
                        "File size mismatch for ${meta.name} (expected ${meta.sizeBytes}B, got ${tempVerifyFile.length()}B)"
                    )
                    return@launch
                }

                // Streaming SHA-256 verification
                val actualHash = HashUtil.calculateSha256(tempVerifyFile)
                if (meta.sha256.isNotBlank() && !HashUtil.verifyHash(actualHash, meta.sha256)) {
                    tempVerifyFile.delete()
                    failReceiveSession(
                        recvSess,
                        "SHA-256 integrity check failed for ${meta.name}! File deleted."
                    )
                    return@launch
                }

                // Save verified file to app storage (FileProvider accessible) & public Downloads/GestoShare
                val receivedDir = File(context.filesDir, "received").apply { mkdirs() }
                val permanentFile = createUniqueFile(receivedDir, safeName)
                FileInputStream(tempVerifyFile).use { input ->
                    FileOutputStream(permanentFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                tempVerifyFile.delete()

                exportToPublicDownloads(permanentFile, permanentFile.name, meta.mimeType)

                val shareableUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    permanentFile
                )
                recvSess.latestSavedUri = shareableUri
                recvSess.latestSavedMime = meta.mimeType

                recvSess.verifiedHashes[meta.itemIndex] = actualHash
                recvSess.verifiedItems[meta.itemIndex] = ContentItem(
                    uri = shareableUri,
                    name = permanentFile.name,
                    mimeType = meta.mimeType,
                    sizeBytes = permanentFile.length(),
                    thumbnailUri = if (meta.contentType == ContentType.IMAGE || meta.contentType == ContentType.VIDEO) shareableUri else null,
                    type = meta.contentType,
                    cachedFilePath = permanentFile.absolutePath,
                    sha256Hash = actualHash
                )

                checkReceiveSessionCompletion(recvSess)
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying/saving incoming file", e)
                tempVerifyFile?.delete()
                failReceiveSession(recvSess, e.localizedMessage ?: "Failed saving incoming file")
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

        // Send verified ACK back to sender before marking complete
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

        withContext(Dispatchers.Main) {
            val completed = (_activeTransfer.value ?: ActiveTransfer(
                id = recvSess.transferId,
                senderName = recvSess.senderName,
                receiverName = currentDeviceName,
                items = orderedItems,
                totalBytes = recvSess.totalBytes,
                direction = TransferDirection.RECEIVE
            )).copy(
                items = orderedItems,
                progress = 1.0f,
                bytesTransferred = recvSess.totalBytes,
                speedMbps = speedMBps,
                status = TransferStatus.SUCCESS,
                sha256Checksum = shaSummary,
                isIntegrityVerified = true,
                statusDetail = "SHA-256 Verified · Saved to Downloads/GestoShare",
                savedUriString = recvSess.latestSavedUri?.toString(),
                savedMimeType = recvSess.latestSavedMime
            )
            _activeTransfer.value = completed
            _statusMessage.value = "Received & verified ${orderedItems.size} item(s) from ${recvSess.senderName}"
            activeReceiveSession = null
            onTransferFinishedListener?.invoke(completed)
        }
    }

    private fun failReceiveSession(
        recvSess: ReceiveSession,
        reason: String,
        isCancelled: Boolean = false
    ) {
        if (recvSess.isFinishedOrCancelled) return
        recvSess.isFinishedOrCancelled = true
        cleanupReceivePayloads(recvSess)

        if (!isCancelled) {
            val failJson = JSONObject().apply {
                put("type", "TRANSFER_VERIFIED")
                put("transferId", recvSess.transferId)
                put("verified", false)
                put("errorMessage", reason)
            }
            try {
                sendControlJson(recvSess.endpointId, failJson)
            } catch (_: Exception) {
            }
        }

        scope.launch(Dispatchers.Main) {
            val failed = (_activeTransfer.value ?: ActiveTransfer(
                id = recvSess.transferId,
                senderName = recvSess.senderName,
                receiverName = currentDeviceName,
                items = emptyList(),
                totalBytes = recvSess.totalBytes,
                direction = TransferDirection.RECEIVE
            )).copy(
                status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED,
                isIntegrityVerified = false,
                errorMessage = reason,
                statusDetail = reason
            )
            _activeTransfer.value = failed
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
            val current = _activeTransfer.value
            if (current != null && current.id == recvSess.transferId &&
                current.status == TransferStatus.TRANSFERRING
            ) {
                _activeTransfer.value = current.copy(
                    progress = progress,
                    bytesTransferred = totalTransferred,
                    speedMbps = speedMBps
                )
            }
        }
    }

    private fun createUniqueFile(dir: File, originalName: String): File {
        var candidate = File(dir, originalName)
        if (!candidate.exists()) return candidate
        val dot = originalName.lastIndexOf('.')
        val base = if (dot > 0) originalName.substring(0, dot) else originalName
        val ext = if (dot > 0) originalName.substring(dot) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun exportToPublicDownloads(sourceFile: File, displayName: String, mimeType: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GestoShare")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "GestoShare"
                ).apply { mkdirs() }
                val dest = createUniqueFile(downloadsDir, displayName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public Downloads export fallback skipped: ${e.message}")
        }
    }

    private fun cleanupReceivePayloads(recvSess: ReceiveSession) {
        recvSess.receivedFilePayloads.values.forEach { payload ->
            try {
                payload.asFile()?.asJavaFile()?.delete()
            } catch (_: Exception) {
            }
        }
    }

    override fun cancelTransfer() {
        val sendSess = pendingSendSession
        if (sendSess != null) {
            sendSess.isCancelledOrFailed = true
            sendSess.activePayloadIds.forEach { payloadId ->
                try {
                    connectionsClient.cancelPayload(payloadId)
                } catch (_: Exception) {
                }
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
            sendSess.items.forEach { item ->
                item.cachedFilePath?.let { path ->
                    if (path.contains("${File.separator}outgoing${File.separator}")) {
                        runCatching { File(path).delete() }
                    }
                }
            }
            val cancelled = _activeTransfer.value?.copy(
                status = TransferStatus.CANCELLED,
                statusDetail = "Transfer cancelled by user",
                errorMessage = "Cancelled by user"
            )
            if (cancelled != null) {
                _activeTransfer.value = cancelled
                sendSess.onComplete(cancelled)
                onTransferFinishedListener?.invoke(cancelled)
            }
            pendingSendSession = null
            _statusMessage.value = "Transfer cancelled"
            return
        }

        val recvSess = activeReceiveSession
        if (recvSess != null) {
            recvSess.receivedFilePayloads.keys.forEach { payloadId ->
                try {
                    connectionsClient.cancelPayload(payloadId)
                } catch (_: Exception) {
                }
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
            statusDetail = "Transfer cancelled"
        )
    }

    override fun retryTransfer() {
        val target = lastTargetDevice
        val items = lastTransferItems
        val onComplete = lastOnComplete
        if (target != null && items.isNotEmpty() && onComplete != null) {
            _statusMessage.value = "Retrying transfer to ${target.name}..."
            startTransfer(target, items, currentDeviceName, onComplete)
        } else {
            _statusMessage.value = "Nothing to retry."
        }
    }

    // =========================================================================
    // CONNECTION LIFECYCLE CALLBACK (Phase 3 Authentication Handshake)
    // =========================================================================
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated by $endpointId (${connectionInfo.endpointName}), auth: ${connectionInfo.authenticationDigits}")

            val session = ConnectionSession(
                endpointId = endpointId,
                endpointName = connectionInfo.endpointName,
                authDigits = connectionInfo.authenticationDigits,
                isIncoming = connectionInfo.isIncomingConnection,
                status = SessionStatus.INITIATED
            )
            _pendingSession.value = session
            _statusMessage.value = "Authentication code: ${connectionInfo.authenticationDigits}. Tap Accept on both devices."

            updateDeviceConnectionState(endpointId, DeviceConnectionState.CONNECTING, connectionInfo.endpointName)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val session = _pendingSession.value
            val endpointName = session?.endpointName ?: "Remote Device"

            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                Log.d(TAG, "Successfully connected to $endpointId ($endpointName)")
                val connectedDevice = DeviceItem(
                    endpointId = endpointId,
                    name = endpointName,
                    deviceType = DeviceType.PHONE,
                    connectionState = DeviceConnectionState.CONNECTED
                )
                _connectedDevice.value = connectedDevice
                _pendingSession.value = session?.copy(status = SessionStatus.CONNECTED)
                _statusMessage.value = "Connected to $endpointName. Ready for real file transfers."
                updateDeviceConnectionState(endpointId, DeviceConnectionState.CONNECTED, endpointName)
            } else {
                val friendlyReason = when (resolution.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                        "Connection rejected by $endpointName."
                    ConnectionsStatusCodes.STATUS_ENDPOINT_IO_ERROR ->
                        "Bluetooth/Wi-Fi I/O error connecting to $endpointName. Move phones closer and retry."
                    ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ->
                        "Bluetooth radio error. Ensure Bluetooth is turned on."
                    ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN ->
                        "Receiver unavailable or out of range. Refresh discovery and retry."
                    else ->
                        "Connection failed (${resolution.status.statusMessage ?: resolution.status.statusCode})."
                }
                Log.w(TAG, "Connection rejected or failed: $friendlyReason")
                _connectedDevice.value = null
                _pendingSession.value = session?.copy(status = SessionStatus.FAILED)
                _statusMessage.value = friendlyReason
                updateDeviceConnectionState(endpointId, DeviceConnectionState.FAILED, endpointName)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            if (_connectedDevice.value?.endpointId == endpointId) {
                _connectedDevice.value = null
            }
            if (_pendingSession.value?.endpointId == endpointId) {
                _pendingSession.value = _pendingSession.value?.copy(status = SessionStatus.DISCONNECTED)
            }
            if (_incomingTransferOffer.value?.endpointId == endpointId) {
                _incomingTransferOffer.value = null
                pendingOfferReceived = null
            }
            val sendSess = pendingSendSession
            if (sendSess != null && sendSess.endpointId == endpointId && !sendSess.isCancelledOrFailed) {
                sendSess.isCancelledOrFailed = true
                val failed = _activeTransfer.value?.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = "Peer disconnected unexpectedly",
                    statusDetail = "Connection lost during transfer"
                )
                if (failed != null) {
                    _activeTransfer.value = failed
                    sendSess.onComplete(failed)
                    onTransferFinishedListener?.invoke(failed)
                }
                pendingSendSession = null
            }
            val recvSess = activeReceiveSession
            if (recvSess != null && recvSess.endpointId == endpointId && !recvSess.isFinishedOrCancelled) {
                failReceiveSession(recvSess, "Sender disconnected during transfer")
            }
            _statusMessage.value = "Disconnected from device"
            updateDeviceConnectionState(endpointId, DeviceConnectionState.DISCONNECTED)
        }
    }

    // =========================================================================
    // ENDPOINT DISCOVERY CALLBACK (Sender Scan)
    // =========================================================================
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            val newDevice = DeviceItem(
                endpointId = endpointId,
                name = info.endpointName,
                deviceType = DeviceType.PHONE,
                connectionState = DeviceConnectionState.DISCOVERED
            )
            val currentList = _discoveredDevices.value.toMutableList()
            currentList.removeAll { it.endpointId == endpointId }
            currentList.add(newDevice)
            _discoveredDevices.value = currentList
            _statusMessage.value = "Discovered peer: ${info.endpointName}"
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            val currentList = _discoveredDevices.value.toMutableList()
            currentList.removeAll { it.endpointId == endpointId }
            _discoveredDevices.value = currentList
            if (_connectedDevice.value?.endpointId == endpointId) {
                _connectedDevice.value = null
            }
            _statusMessage.value = "Endpoint lost: $endpointId"
        }
    }

    // =========================================================================
    // ADVERTISING / RECEIVER MODE
    // =========================================================================
    override fun startAdvertising(myDeviceName: String) {
        if (_isAdvertising.value) return
        currentDeviceName = myDeviceName.ifBlank { "GestoShare Receiver" }

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        _statusMessage.value = "Starting advertising as '$currentDeviceName'..."

        connectionsClient.startAdvertising(
            currentDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            _isAdvertising.value = true
            _statusMessage.value = "Receive Mode Active: Advertising as '$currentDeviceName'"
            Log.d(TAG, "Advertising successfully started as $currentDeviceName")
        }.addOnFailureListener { e ->
            _isAdvertising.value = false
            _statusMessage.value = "Failed to start advertising: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.e(TAG, "Failed to start advertising", e)
        }
    }

    override fun stopAdvertising() {
        if (!_isAdvertising.value) return
        try {
            connectionsClient.stopAdvertising()
            _isAdvertising.value = false
            _statusMessage.value = "Receive Mode Stopped"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }

    override fun toggleAdvertising(myDeviceName: String) {
        if (_isAdvertising.value) stopAdvertising() else startAdvertising(myDeviceName)
    }

    // =========================================================================
    // DISCOVERY / SENDER MODE
    // =========================================================================
    override fun startDiscovery(myDeviceName: String) {
        if (_isDiscovering.value) return
        currentDeviceName = myDeviceName.ifBlank { "GestoShare Sender" }

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        _discoveredDevices.value = emptyList()
        _statusMessage.value = "Scanning for nearby GestoShare peers..."

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            _isDiscovering.value = true
            _statusMessage.value = "Scanning for peers using P2P Point-to-Point..."
        }.addOnFailureListener { e ->
            _isDiscovering.value = false
            _statusMessage.value = "Discovery failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    override fun stopDiscovery() {
        if (!_isDiscovering.value) return
        try {
            connectionsClient.stopDiscovery()
            _isDiscovering.value = false
            _statusMessage.value = "Discovery stopped"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }

    // =========================================================================
    // CONNECTION REQUEST & AUTHENTICATION HANDSHAKE
    // =========================================================================
    override fun requestConnection(device: DeviceItem, myDeviceName: String) {
        currentDeviceName = myDeviceName.ifBlank { "GestoShare Peer" }
        _statusMessage.value = "Requesting connection to ${device.name}..."
        updateDeviceConnectionState(device.endpointId, DeviceConnectionState.CONNECTING)

        connectionsClient.requestConnection(
            currentDeviceName,
            device.endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            _statusMessage.value = "Connection requested to ${device.name}. Awaiting handshake..."
        }.addOnFailureListener { e ->
            updateDeviceConnectionState(device.endpointId, DeviceConnectionState.FAILED)
            _statusMessage.value = "Connection request failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    override fun acceptConnection(endpointId: String) {
        _statusMessage.value = "Accepting connection with $endpointId..."
        _pendingSession.value = _pendingSession.value?.copy(status = SessionStatus.ACCEPTED_LOCALLY)

        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                _statusMessage.value = "Accepted locally. Waiting for peer confirmation..."
            }
            .addOnFailureListener { e ->
                _statusMessage.value = "Failed to accept: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
    }

    override fun rejectConnection(endpointId: String) {
        _statusMessage.value = "Rejecting connection with $endpointId..."
        _pendingSession.value = null
        connectionsClient.rejectConnection(endpointId)
            .addOnSuccessListener {
                _statusMessage.value = "Connection rejected"
                updateDeviceConnectionState(endpointId, DeviceConnectionState.DISCONNECTED)
            }
    }

    override fun disconnectDevice(endpointId: String) {
        try {
            connectionsClient.disconnectFromEndpoint(endpointId)
            _connectedDevice.value = null
            _pendingSession.value = null
            _statusMessage.value = "Disconnected from $endpointId"
            updateDeviceConnectionState(endpointId, DeviceConnectionState.DISCONNECTED)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from $endpointId", e)
        }
    }

    override fun stopAllEndpoints() {
        try {
            connectionsClient.stopAllEndpoints()
            _connectedDevice.value = null
            _pendingSession.value = null
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
    // REAL TEXT PAYLOAD TEST (Phase 3)
    // =========================================================================
    override fun sendTextMessage(message: String): Boolean {
        val target = _connectedDevice.value ?: run {
            _statusMessage.value = "Cannot send: No device connected."
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
            Log.e(TAG, "Error sending text message", e)
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
        nameOverride: String? = null
    ) {
        _discoveredDevices.value = _discoveredDevices.value.map { device ->
            if (device.endpointId == endpointId) {
                device.copy(
                    connectionState = state,
                    name = nameOverride ?: device.name,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            } else {
                device
            }
        }
    }

    override fun retryDeviceConnection(deviceId: String) {
        val target = _discoveredDevices.value.find { it.endpointId == deviceId }
        if (target != null) {
            requestConnection(target, currentDeviceName)
        }
    }
}
