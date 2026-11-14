package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AirShareDatabase
import com.example.data.RoomTransferHistoryRepository
import com.example.data.TransferHistoryRepository
import com.example.manager.ContentImportManager
import com.example.manager.DefaultContentImportManager
import com.example.manager.DefaultGestureRecognitionManager
import com.example.manager.DefaultTransferStateManager
import com.example.manager.GestureActionEffect
import com.example.manager.GestureRecognitionManager
import com.example.manager.GestureSessionContext
import com.example.manager.HaGridV2OnnxClassifier
import com.example.manager.HandTrackingState
import com.example.manager.LatencyStats
import com.example.manager.MediaPipeHandTracker
import com.example.manager.NearbyTransferManager
import com.example.manager.OnnxModelDiagnostics
import com.example.manager.PredictionHistoryEntry
import com.example.manager.RealNearbyTransferManager
import com.example.manager.StabilizedGestureDecision
import com.example.manager.TemporalGestureConfig
import com.example.manager.TransferStateManager
import com.example.model.ActiveTransfer
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.DeviceItem
import com.example.model.GestureOperationMode
import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.example.model.IncomingTransferOffer
import com.example.model.NearbyMessage
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

enum class ScreenRoute {
    HOME,
    CONTENT_SELECTION,
    NEARBY_DEVICES,
    GESTURE_CONTROL,
    TRANSFER,
    HISTORY,
    SETTINGS
}

class AirShareViewModel(
    application: Application
) : AndroidViewModel(application) {

    val contentImportManager: ContentImportManager = DefaultContentImportManager()
    val onnxClassifier = HaGridV2OnnxClassifier(application.applicationContext)
    val gestureManager: GestureRecognitionManager = DefaultGestureRecognitionManager(onnxClassifier)
    val nearbyManager: NearbyTransferManager = RealNearbyTransferManager(application.applicationContext, viewModelScope)
    val transferStateManager: TransferStateManager = DefaultTransferStateManager()
    val handTracker: MediaPipeHandTracker = MediaPipeHandTracker(application.applicationContext, onnxClassifier)

    private val database = AirShareDatabase.getDatabase(application)
    val historyRepository: TransferHistoryRepository = RoomTransferHistoryRepository(database.transferDao())

    private val recordedTransferIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val _currentScreen = MutableStateFlow(ScreenRoute.HOME)
    val currentScreen: StateFlow<ScreenRoute> = _currentScreen.asStateFlow()

    private val _deviceName = MutableStateFlow("GestoShare (${Build.MODEL ?: "Device"})")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val navigationBackStack = mutableListOf(ScreenRoute.HOME)

    val historyTransfers: StateFlow<List<TransferRecord>> = historyRepository.getAllTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stagedItems: StateFlow<List<ContentItem>> = transferStateManager.stagedItems
    val targetDevice: StateFlow<DeviceItem?> = transferStateManager.targetDevice
    val discoveredDevices: StateFlow<List<DeviceItem>> = nearbyManager.discoveredDevices
    val activeTransfer: StateFlow<ActiveTransfer?> = nearbyManager.activeTransfer
    val incomingTransferOffer: StateFlow<IncomingTransferOffer?> = nearbyManager.incomingTransferOffer

    val pendingSession: StateFlow<ConnectionSession?> = nearbyManager.pendingSession
    val connectedDevice: StateFlow<DeviceItem?> = nearbyManager.connectedDevice
    val receivedMessages: StateFlow<List<NearbyMessage>> = nearbyManager.receivedMessages
    val nearbyStatusMessage: StateFlow<String> = nearbyManager.statusMessage
    val isNearbyAvailable: StateFlow<Boolean> = nearbyManager.isNearbyAvailable

    val handTrackingState: StateFlow<HandTrackingState> = handTracker.trackingState
    val liveOnnxPrediction: StateFlow<GestureResult> = handTracker.onnxPrediction
    val onnxConfidenceThreshold: StateFlow<Float> = handTracker.confidenceThreshold
    val onnxLatencyStats: StateFlow<LatencyStats> = handTracker.latencyStats
    val predictionHistory: StateFlow<List<PredictionHistoryEntry>> = handTracker.predictionHistory
    val stabilizerDecision: StateFlow<StabilizedGestureDecision> = handTracker.stabilizerDecision
    val stabilizerConfig: StateFlow<TemporalGestureConfig> = handTracker.stabilizerConfig

    val operationMode: StateFlow<GestureOperationMode> = gestureManager.operationMode
    val sessionFeedback: StateFlow<String?> = gestureManager.sessionFeedback
    val commandLogs: StateFlow<List<String>> = gestureManager.commandLogs
    val isContentGrabbed: StateFlow<Boolean> = gestureManager.isContentGrabbed

    val onnxDiagnostics: OnnxModelDiagnostics
        get() = onnxClassifier.getDiagnostics()

    init {
        nearbyManager.setOnTransferFinishedListener { completedTransfer ->
            recordCompletedTransfer(completedTransfer)
        }

        // Handle temporal-stabilized gesture commands off the UI thread
        handTracker.setOnStabilizedCommandListener { gesture, confidence ->
            viewModelScope.launch(Dispatchers.Default) {
                executeGestureCommand(gesture, confidence)
            }
        }

        // Continuously synchronize gesture session state with real Nearby callbacks & staged state
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                stagedItems,
                discoveredDevices,
                targetDevice,
                connectedDevice,
                pendingSession,
                activeTransfer
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                GestureSessionContext(
                    stagedItems = values[0] as List<ContentItem>,
                    discoveredDevices = values[1] as List<DeviceItem>,
                    selectedDevice = values[2] as DeviceItem?,
                    connectedDevice = values[3] as DeviceItem?,
                    pendingSession = values[4] as ConnectionSession?,
                    activeTransfer = values[5] as ActiveTransfer?,
                    isModelAvailable = onnxClassifier.isModelLoaded(),
                    modelUnavailableReason = onnxDiagnostics.statusMessage
                )
            }.collect { ctx ->
                gestureManager.syncWithSystemState(ctx)
            }
        }
    }

    private fun buildCurrentGestureContext(): GestureSessionContext {
        return GestureSessionContext(
            stagedItems = stagedItems.value,
            discoveredDevices = discoveredDevices.value,
            selectedDevice = targetDevice.value,
            connectedDevice = connectedDevice.value,
            pendingSession = pendingSession.value,
            activeTransfer = activeTransfer.value,
            isModelAvailable = onnxClassifier.isModelLoaded(),
            modelUnavailableReason = onnxDiagnostics.statusMessage
        )
    }

    private suspend fun executeGestureCommand(gesture: GestureType, confidence: Float) {
        val effect = gestureManager.processCommandGesture(
            gesture = gesture,
            confidence = confidence,
            context = buildCurrentGestureContext()
        )
        applyGestureEffect(effect)
    }

    private suspend fun applyGestureEffect(effect: GestureActionEffect) {
        when (effect) {
            is GestureActionEffect.GrabContentConfirmed -> {
                transferStateManager.prepareForGestureTransfer()
            }

            is GestureActionEffect.SelectPeerDevice -> {
                transferStateManager.setTargetDevice(effect.device)
            }

            is GestureActionEffect.DispatchTransferOffer -> {
                if (!effect.isDryRun) {
                    withContext(Dispatchers.Main) {
                        executeTransfer(effect.targetDevice)
                    }
                }
            }

            is GestureActionEffect.CancelActiveWorkflow -> {
                withContext(Dispatchers.Main) {
                    nearbyManager.cancelTransfer()
                }
            }

            is GestureActionEffect.None -> Unit
        }
    }

    fun navigateTo(route: ScreenRoute) {
        if (_currentScreen.value != route) {
            navigationBackStack.add(route)
            _currentScreen.value = route
        }
    }

    fun navigateBack(): Boolean {
        return if (navigationBackStack.size > 1) {
            navigationBackStack.removeAt(navigationBackStack.size - 1)
            _currentScreen.value = navigationBackStack.last()
            true
        } else {
            false
        }
    }

    fun updateDeviceName(newName: String) {
        if (newName.isNotBlank()) {
            _deviceName.value = newName.trim()
        }
    }

    fun setGestureOperationMode(mode: GestureOperationMode) {
        gestureManager.setOperationMode(mode)
    }

    fun updateConfidenceThreshold(threshold: Float) {
        handTracker.setConfidenceThreshold(threshold)
    }

    fun updateStabilizerConsecutiveFrames(frames: Int) {
        handTracker.updateStabilizerConfig {
            it.copy(
                requiredConsecutiveFrames = frames.coerceIn(2, 10),
                windowSize = maxOf(frames.coerceIn(2, 10), it.windowSize)
            )
        }
    }

    fun updateStabilizerHoldDurationMs(holdMs: Long) {
        handTracker.updateStabilizerConfig { it.copy(holdDurationMs = holdMs.coerceIn(0L, 1500L)) }
    }

    fun updateStabilizerCooldownMs(cooldownMs: Long) {
        handTracker.updateStabilizerConfig { it.copy(commandCooldownMs = cooldownMs.coerceIn(0L, 4000L)) }
    }

    fun clearPredictionHistory() {
        handTracker.clearPredictionHistory()
    }

    fun clearSessionFeedback() {
        gestureManager.clearFeedback()
    }

    fun importUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val item = contentImportManager.importFromUri(context, uri)
            item?.let {
                transferStateManager.stageItem(it)
            }
        }
    }

    fun importMultipleUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val items = contentImportManager.importMultipleFromUris(context, uris)
            if (items.isNotEmpty()) {
                transferStateManager.stageMultipleItems(items)
            }
        }
    }

    fun addTextContent(text: String) {
        if (text.isNotBlank()) {
            val item = contentImportManager.createFromText(text.trim())
            transferStateManager.stageItem(item)
        }
    }

    fun addUrlContent(url: String) {
        if (url.isNotBlank()) {
            val item = contentImportManager.createFromUrl(url.trim())
            transferStateManager.stageItem(item)
        }
    }

    fun removeStagedItem(id: String) {
        transferStateManager.removeItem(id)
    }

    fun clearStagedItems() {
        transferStateManager.clearStagedItems()
    }

    fun selectTargetDevice(device: DeviceItem) {
        transferStateManager.setTargetDevice(device)
    }

    fun executeTransfer(targetDevice: DeviceItem) {
        val items = stagedItems.value
        if (items.isEmpty()) return

        transferStateManager.setTargetDevice(targetDevice)
        navigateTo(ScreenRoute.TRANSFER)

        nearbyManager.startTransfer(
            targetDevice = targetDevice,
            items = items,
            myDeviceName = _deviceName.value
        ) { completedTransfer ->
            recordCompletedTransfer(completedTransfer)
        }
    }

    fun acceptIncomingTransfer(transferId: String) {
        nearbyManager.acceptTransferOffer(transferId)
        navigateTo(ScreenRoute.TRANSFER)
    }

    fun rejectIncomingTransfer(transferId: String) {
        nearbyManager.rejectTransferOffer(transferId)
    }

    fun openReceivedFile(context: Context, uriString: String, mimeType: String?) {
        try {
            val uri = Uri.parse(uriString)
            val resolvedMime = mimeType?.takeIf { it.isNotBlank() && it.contains("/") } ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, "Open Received File").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unsupported MIME type ($mimeType) or no viewer installed. File saved safely in Downloads/GestoShare.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun cancelActiveTransfer() {
        nearbyManager.cancelTransfer()
        if (gestureManager.sessionState.value.isActiveSession) {
            viewModelScope.launch(Dispatchers.Default) {
                executeGestureCommand(GestureType.STOP, 1.0f)
            }
        }
    }

    fun retryActiveTransfer() {
        nearbyManager.retryTransfer()
    }

    fun cancelGestureWorkflowManually() {
        viewModelScope.launch(Dispatchers.Default) {
            val current = gestureManager.sessionState.value
            if (current.isActiveSession) {
                executeGestureCommand(GestureType.STOP, 1.0f)
            } else {
                withContext(Dispatchers.Main) {
                    nearbyManager.cancelTransfer()
                }
                gestureManager.resetSession()
            }
            handTracker.resetStabilizer()
        }
    }

    fun resetGestureSession() {
        gestureManager.resetSession()
        handTracker.resetStabilizer()
    }

    private fun recordCompletedTransfer(transfer: ActiveTransfer) {
        if (!recordedTransferIds.add(transfer.id)) return
        viewModelScope.launch {
            val firstItem = transfer.items.firstOrNull()
            val fileName = when {
                transfer.items.size > 1 -> "${firstItem?.name ?: "Files"} + ${transfer.items.size - 1} more"
                firstItem != null -> firstItem.name
                else -> "Shared Content"
            }
            val mime = firstItem?.mimeType ?: "application/octet-stream"
            val verificationLabel = when {
                transfer.status == TransferStatus.SUCCESS && transfer.isIntegrityVerified ->
                    "SHA-256 Verified (${transfer.sha256Checksum.take(10)})"
                transfer.status == TransferStatus.SUCCESS ->
                    "Completed (${transfer.sha256Checksum.take(10)})"
                !transfer.errorMessage.isNullOrBlank() ->
                    transfer.errorMessage
                else -> "Unverified (${transfer.status.name})"
            }
            val record = TransferRecord(
                id = transfer.id,
                fileName = fileName,
                fileType = mime,
                fileSizeFormatted = contentImportManager.formatFileSize(transfer.totalBytes),
                senderName = transfer.senderName,
                receiverName = transfer.receiverName,
                direction = transfer.direction,
                status = transfer.status,
                timestamp = System.currentTimeMillis(),
                sha256Verification = verificationLabel
            )
            historyRepository.insertTransfer(record)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    fun deleteHistoryRecord(id: String) {
        viewModelScope.launch {
            historyRepository.deleteTransfer(id)
        }
    }

    fun startNearbyDiscovery() {
        nearbyManager.startDiscovery(_deviceName.value)
    }

    fun stopNearbyDiscovery() {
        nearbyManager.stopDiscovery()
    }

    fun toggleNearbyAdvertising() {
        nearbyManager.toggleAdvertising(_deviceName.value)
    }

    fun requestNearbyConnection(device: DeviceItem) {
        nearbyManager.requestConnection(device, _deviceName.value)
    }

    fun acceptNearbyConnection(endpointId: String) {
        nearbyManager.acceptConnection(endpointId)
    }

    fun rejectNearbyConnection(endpointId: String) {
        nearbyManager.rejectConnection(endpointId)
    }

    fun disconnectNearbyDevice(endpointId: String) {
        nearbyManager.disconnectDevice(endpointId)
    }

    fun sendNearbyTextMessage(message: String): Boolean {
        return nearbyManager.sendTextMessage(message)
    }

    fun sendNearbyPingTest(): Boolean {
        return nearbyManager.sendPingTest()
    }

    fun simulateGesture(gesture: GestureType) {
        viewModelScope.launch(Dispatchers.Default) {
            executeGestureCommand(gesture, 0.96f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        handTracker.setOnStabilizedCommandListener(null)
        nearbyManager.stopAllEndpoints()
        handTracker.close()
    }
}
