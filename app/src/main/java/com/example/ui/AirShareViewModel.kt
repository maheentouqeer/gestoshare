package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AirShareDatabase
import com.example.data.AppPreferencesRepository
import com.example.data.AppSettingsState
import com.example.data.DiagnosticsRepository
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
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticCodes
import com.example.model.DiagnosticErrorEvent
import com.example.model.DiagnosticLogEntry
import com.example.model.GestureOperationMode
import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.example.model.IncomingTransferOffer
import com.example.model.NearbyMessage
import com.example.model.RecentDeviceRecord
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
import java.io.File
import java.util.Collections

enum class ScreenRoute {
    SPLASH,
    ONBOARDING,
    HOME,
    CONTENT_SELECTION,
    NEARBY_DEVICES,
    GESTURE_CONTROL,
    TRANSFER,
    HISTORY,
    SETTINGS,
    DIAGNOSTICS,
    ABOUT
}

class AirShareViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AirShareDatabase.getDatabase(application)
    val preferencesRepository = AppPreferencesRepository(application.applicationContext)
    val diagnosticsRepository = DiagnosticsRepository(database.diagnosticDao())
    val historyRepository: TransferHistoryRepository = RoomTransferHistoryRepository(database.transferDao())

    val contentImportManager: ContentImportManager = DefaultContentImportManager(diagnosticsRepository)
    val onnxClassifier = HaGridV2OnnxClassifier(
        context = application.applicationContext,
        diagnosticsRepository = diagnosticsRepository
    )
    val gestureManager: GestureRecognitionManager = DefaultGestureRecognitionManager(onnxClassifier)
    val nearbyManager: NearbyTransferManager = RealNearbyTransferManager(
        context = application.applicationContext,
        scope = viewModelScope,
        diagnostics = diagnosticsRepository
    )
    val transferStateManager: TransferStateManager = DefaultTransferStateManager()
    val handTracker: MediaPipeHandTracker = MediaPipeHandTracker(
        context = application.applicationContext,
        classifier = onnxClassifier,
        diagnosticsRepository = diagnosticsRepository
    )

    private val recordedTransferIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    val settingsState: StateFlow<AppSettingsState> = preferencesRepository.settings

    private val _currentScreen = MutableStateFlow(ScreenRoute.SPLASH)
    val currentScreen: StateFlow<ScreenRoute> = _currentScreen.asStateFlow()

    private val _deviceName = MutableStateFlow(preferencesRepository.settings.value.deviceName)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _multiSelectMode = MutableStateFlow(false)
    val multiSelectMode: StateFlow<Boolean> = _multiSelectMode.asStateFlow()

    private val _multiSelectRecipientIds = MutableStateFlow<Set<String>>(emptySet())
    val multiSelectRecipientIds: StateFlow<Set<String>> = _multiSelectRecipientIds.asStateFlow()

    private val navigationBackStack = mutableListOf(ScreenRoute.HOME)

    val historyTransfers: StateFlow<List<TransferRecord>> = historyRepository.getAllTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val errorEvents: StateFlow<List<DiagnosticErrorEvent>> = diagnosticsRepository.errorEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val operationalLogs: StateFlow<List<DiagnosticLogEntry>> = diagnosticsRepository.operationalLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDevices: StateFlow<List<RecentDeviceRecord>> = diagnosticsRepository.recentDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stagedItems: StateFlow<List<ContentItem>> = transferStateManager.stagedItems
    val targetDevice: StateFlow<DeviceItem?> = transferStateManager.targetDevice
    val discoveredDevices: StateFlow<List<DeviceItem>> = nearbyManager.discoveredDevices
    val activeTransfer: StateFlow<ActiveTransfer?> = nearbyManager.activeTransfer
    val endpointTransfers: StateFlow<Map<String, ActiveTransfer>> = nearbyManager.endpointTransfers
    val incomingTransferOffer: StateFlow<IncomingTransferOffer?> = nearbyManager.incomingTransferOffer

    val pendingSession: StateFlow<ConnectionSession?> = nearbyManager.pendingSession
    val pendingSessions: StateFlow<List<ConnectionSession>> = nearbyManager.pendingSessions
    val connectedDevice: StateFlow<DeviceItem?> = nearbyManager.connectedDevice
    val connectedDevices: StateFlow<List<DeviceItem>> = nearbyManager.connectedDevices
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
        applyPersistedSettings(preferencesRepository.settings.value)

        nearbyManager.setOnTransferFinishedListener { completedTransfer ->
            recordCompletedTransfer(completedTransfer)
        }

        handTracker.setOnStabilizedCommandListener { gesture, confidence ->
            triggerGestureFeedbackCue(gesture)
            viewModelScope.launch(Dispatchers.Default) {
                executeGestureCommand(gesture, confidence)
            }
        }

        // Continuously synchronize multi-device Nearby state with gesture session state machine
        viewModelScope.launch(Dispatchers.Default) {
            val nearbyFlow = combine(
                discoveredDevices,
                connectedDevice,
                connectedDevices,
                pendingSession,
                activeTransfer
            ) { disc, conn, connList, pend, active ->
                NearbySyncSnapshot(disc, conn, connList, pend, active)
            }
            val uiFlow = combine(
                stagedItems,
                targetDevice,
                multiSelectMode,
                multiSelectRecipientIds
            ) { staged, target, multi, multiIds ->
                UiSyncSnapshot(staged, target, multi, multiIds)
            }

            combine(nearbyFlow, uiFlow) { nearby, ui ->
                val connectedIds = nearby.connectedDevices.map { it.endpointId }.toSet()
                val cleanedMulti = ui.multiSelectRecipientIds.intersect(connectedIds)
                if (cleanedMulti != ui.multiSelectRecipientIds) {
                    _multiSelectRecipientIds.value = cleanedMulti
                }
                val multiList = nearby.connectedDevices.filter { it.endpointId in cleanedMulti }

                GestureSessionContext(
                    stagedItems = ui.stagedItems,
                    discoveredDevices = nearby.discoveredDevices,
                    selectedDevice = ui.targetDevice,
                    connectedDevice = nearby.connectedDevice,
                    connectedDevices = nearby.connectedDevices,
                    pendingSession = nearby.pendingSession,
                    activeTransfer = nearby.activeTransfer,
                    multiSelectRecipients = multiList,
                    isMultiSelectMode = ui.multiSelectMode,
                    isModelAvailable = onnxClassifier.isModelLoaded(),
                    modelUnavailableReason = onnxDiagnostics.statusMessage
                )
            }.collect { ctx ->
                val target = ctx.selectedDevice
                val isTransferOngoing = ctx.activeTransfer?.status == TransferStatus.PREPARING ||
                    ctx.activeTransfer?.status == TransferStatus.AWAITING_ACCEPTANCE ||
                    ctx.activeTransfer?.status == TransferStatus.TRANSFERRING ||
                    ctx.activeTransfer?.status == TransferStatus.VERIFYING
                if (target != null &&
                    !isTransferOngoing &&
                    ctx.connectedDevices.none { it.endpointId == target.endpointId } &&
                    ctx.discoveredDevices.none { it.endpointId == target.endpointId }
                ) {
                    transferStateManager.setTargetDevice(null)
                }
                gestureManager.syncWithSystemState(ctx)
            }
        }
    }

    private fun applyPersistedSettings(s: AppSettingsState) {
        _deviceName.value = s.deviceName
        handTracker.setConfidenceThreshold(s.gestureConfidenceThreshold)
        handTracker.updateStabilizerConfig {
            it.copy(
                requiredConsecutiveFrames = s.requiredConsecutiveFrames,
                windowSize = maxOf(s.requiredConsecutiveFrames, it.windowSize),
                commandCooldownMs = s.commandCooldownMs,
                holdDurationMs = s.holdDurationMs
            )
        }
        gestureManager.setOperationMode(
            if (s.gestureTransferModeEnabled) GestureOperationMode.GESTURE_TRANSFER
            else GestureOperationMode.DEBUG_DRY_RUN
        )
    }

    private fun buildCurrentGestureContext(): GestureSessionContext {
        val connectedList = connectedDevices.value
        val selectedMulti = if (_multiSelectMode.value) {
            connectedList.filter { it.endpointId in _multiSelectRecipientIds.value }
        } else {
            emptyList()
        }
        return GestureSessionContext(
            stagedItems = stagedItems.value,
            discoveredDevices = discoveredDevices.value,
            selectedDevice = targetDevice.value,
            connectedDevice = connectedDevice.value,
            connectedDevices = connectedList,
            pendingSession = pendingSession.value,
            activeTransfer = activeTransfer.value,
            multiSelectRecipients = selectedMulti,
            isMultiSelectMode = _multiSelectMode.value,
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
                        if (effect.targetDevices.size > 1) {
                            executeTransferToRecipients(effect.targetDevices)
                        } else {
                            executeTransfer(effect.targetDevice)
                        }
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

    private fun triggerGestureFeedbackCue(gesture: GestureType) {
        if (gesture == GestureType.NONE || gesture == GestureType.OTHER) return
        val prefs = settingsState.value
        val appContext = getApplication<Application>().applicationContext
        if (prefs.hapticFeedbackEnabled) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(38L, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(38L)
                    }
                }
            }
        }
        if (prefs.soundFeedbackEnabled) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 75)
            }
        }
    }

    // Navigation & Onboarding

    fun advanceFromSplash() {
        if (_currentScreen.value != ScreenRoute.SPLASH) return
        val next = if (settingsState.value.hasCompletedOnboarding) ScreenRoute.HOME else ScreenRoute.ONBOARDING
        navigationBackStack.clear()
        navigationBackStack.add(ScreenRoute.HOME)
        if (next == ScreenRoute.ONBOARDING) {
            navigationBackStack.add(ScreenRoute.ONBOARDING)
        }
        _currentScreen.value = next
    }

    fun completeOnboarding() {
        preferencesRepository.update { it.copy(hasCompletedOnboarding = true) }
        navigationBackStack.clear()
        navigationBackStack.add(ScreenRoute.HOME)
        _currentScreen.value = ScreenRoute.HOME
    }

    fun replayOnboarding() {
        navigateTo(ScreenRoute.ONBOARDING)
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

    // Settings & Theme

    fun toggleDarkTheme(isDark: Boolean) {
        preferencesRepository.update { it.copy(isDarkTheme = isDark) }
    }

    fun updateDeviceName(newName: String) {
        if (newName.isNotBlank()) {
            val trimmed = newName.trim()
            _deviceName.value = trimmed
            preferencesRepository.update { it.copy(deviceName = trimmed) }
        }
    }

    fun setGestureOperationMode(mode: GestureOperationMode) {
        gestureManager.setOperationMode(mode)
        preferencesRepository.update {
            it.copy(gestureTransferModeEnabled = (mode == GestureOperationMode.GESTURE_TRANSFER))
        }
    }

    fun updateConfidenceThreshold(threshold: Float) {
        handTracker.setConfidenceThreshold(threshold)
        preferencesRepository.update { it.copy(gestureConfidenceThreshold = threshold.coerceIn(0.50f, 0.95f)) }
    }

    fun updateStabilizerConsecutiveFrames(frames: Int) {
        val clamped = frames.coerceIn(2, 10)
        handTracker.updateStabilizerConfig {
            it.copy(
                requiredConsecutiveFrames = clamped,
                windowSize = maxOf(clamped, it.windowSize)
            )
        }
        preferencesRepository.update { it.copy(requiredConsecutiveFrames = clamped) }
    }

    fun updateStabilizerHoldDurationMs(holdMs: Long) {
        val clamped = holdMs.coerceIn(0L, 1500L)
        handTracker.updateStabilizerConfig { it.copy(holdDurationMs = clamped) }
        preferencesRepository.update { it.copy(holdDurationMs = clamped) }
    }

    fun updateStabilizerCooldownMs(cooldownMs: Long) {
        val clamped = cooldownMs.coerceIn(0L, 4000L)
        handTracker.updateStabilizerConfig { it.copy(commandCooldownMs = clamped) }
        preferencesRepository.update { it.copy(commandCooldownMs = clamped) }
    }

    fun toggleCameraFacing() {
        preferencesRepository.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        preferencesRepository.update { it.copy(hapticFeedbackEnabled = enabled) }
    }

    fun toggleSoundFeedback(enabled: Boolean) {
        preferencesRepository.update { it.copy(soundFeedbackEnabled = enabled) }
    }

    fun clearPredictionHistory() {
        handTracker.clearPredictionHistory()
    }

    fun clearSessionFeedback() {
        gestureManager.clearFeedback()
    }

    // Content Staging

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

    // Multi-Device Selection & Transfer

    fun selectTargetDevice(device: DeviceItem) {
        transferStateManager.setTargetDevice(device)
    }

    fun toggleMultiSelectMode() {
        val next = !_multiSelectMode.value
        _multiSelectMode.value = next
        if (!next) {
            _multiSelectRecipientIds.value = emptySet()
        } else {
            val currentTarget = targetDevice.value?.endpointId
            val connectedIds = connectedDevices.value.map { it.endpointId }.toSet()
            if (currentTarget != null && currentTarget in connectedIds) {
                _multiSelectRecipientIds.value = setOf(currentTarget)
            }
        }
    }

    fun toggleMultiSelectRecipient(device: DeviceItem) {
        val updated = _multiSelectRecipientIds.value.toMutableSet()
        if (!updated.add(device.endpointId)) {
            updated.remove(device.endpointId)
        }
        _multiSelectRecipientIds.value = updated
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

    fun executeMultiTransfer() {
        val items = stagedItems.value
        if (items.isEmpty()) return
        val targets = connectedDevices.value.filter { it.endpointId in _multiSelectRecipientIds.value }
        if (targets.isEmpty()) return
        executeTransferToRecipients(targets)
    }

    private fun executeTransferToRecipients(targets: List<DeviceItem>) {
        val items = stagedItems.value
        if (items.isEmpty() || targets.isEmpty()) return
        navigateTo(ScreenRoute.TRANSFER)
        nearbyManager.startMultiRecipientTransfer(
            targetDevices = targets,
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
            diagnosticsRepository.recordException(
                throwable = e,
                sourceComponent = "AirShareViewModel",
                operation = "openReceivedFile",
                defaultCode = DiagnosticCodes.GS_FILE_008,
                defaultCategory = DiagnosticCategory.FILE_IO
            )
            Toast.makeText(
                context,
                "No viewer installed for $mimeType. File saved safely in Downloads/GestoShare or app private storage.",
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

    fun cancelEndpointTransfer(endpointId: String) {
        nearbyManager.cancelTransferForEndpoint(endpointId)
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

    // Nearby Control

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

    fun forgetNearbyDevice(endpointId: String) {
        nearbyManager.forgetDevice(endpointId)
    }

    fun forgetRecentDevice(deviceKey: String) {
        diagnosticsRepository.forgetRecentDevice(deviceKey)
    }

    fun sendNearbyTextMessage(message: String): Boolean {
        return nearbyManager.sendTextMessage(message)
    }

    fun sendNearbyTextMessageTo(endpointId: String, message: String): Boolean {
        return nearbyManager.sendTextMessageTo(endpointId, message)
    }

    fun sendNearbyPingTest(): Boolean {
        return nearbyManager.sendPingTest()
    }

    fun simulateGesture(gesture: GestureType) {
        viewModelScope.launch(Dispatchers.Default) {
            executeGestureCommand(gesture, 0.96f)
        }
    }

    // Diagnostics Actions

    fun formatSingleErrorReport(event: DiagnosticErrorEvent): String {
        return diagnosticsRepository.formatSingleErrorReport(event)
    }

    fun deleteDiagnosticEvent(id: String) {
        viewModelScope.launch {
            diagnosticsRepository.deleteErrorEvent(id)
        }
    }

    fun clearAllDiagnostics() {
        viewModelScope.launch {
            diagnosticsRepository.clearAllErrors()
            diagnosticsRepository.clearAllLogs()
        }
    }

    suspend fun prepareDiagnosticsExportUri(context: Context, asJson: Boolean): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val modelSummary = onnxDiagnostics.statusMessage
                val nearbySummary = "${nearbyStatusMessage.value} (connected=${connectedDevices.value.size}, discovered=${discoveredDevices.value.size})"
                val content = if (asJson) {
                    diagnosticsRepository.exportDiagnosticsJson(historyTransfers.value, modelSummary, nearbySummary)
                } else {
                    diagnosticsRepository.exportDiagnosticsText(historyTransfers.value, modelSummary, nearbySummary)
                }
                val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
                val ext = if (asJson) "json" else "txt"
                val outFile = File(dir, "gestoshare_diagnostics_${System.currentTimeMillis()}.$ext")
                outFile.writeText(content)
                FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", outFile)
            } catch (e: Exception) {
                diagnosticsRepository.recordException(
                    throwable = e,
                    sourceComponent = "AirShareViewModel",
                    operation = "prepareDiagnosticsExportUri",
                    defaultCode = DiagnosticCodes.GS_FILE_001,
                    defaultCategory = DiagnosticCategory.FILE_IO
                )
                null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        handTracker.setOnStabilizedCommandListener(null)
        nearbyManager.stopAllEndpoints()
        handTracker.close()
    }

    private data class NearbySyncSnapshot(
        val discoveredDevices: List<DeviceItem>,
        val connectedDevice: DeviceItem?,
        val connectedDevices: List<DeviceItem>,
        val pendingSession: ConnectionSession?,
        val activeTransfer: ActiveTransfer?
    )

    private data class UiSyncSnapshot(
        val stagedItems: List<ContentItem>,
        val targetDevice: DeviceItem?,
        val multiSelectMode: Boolean,
        val multiSelectRecipientIds: Set<String>
    )
}
