package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppSettingsState
import com.example.ui.AirShareViewModel
import com.example.ui.ScreenRoute
import com.example.ui.components.AirShareBottomNav
import com.example.ui.components.AirShareTopBar
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ContentSelectionScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.GestureControlScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NearbyDevicesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransferHistoryScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.theme.GestoGlassBackground
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.glassSurface
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AirShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleIncomingShareIntent(intent)
        }

        setContent {
            val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = settingsState.isDarkTheme) {
                AirShareApp(
                    viewModel = viewModel,
                    settingsState = settingsState
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val clipData = intent.clipData
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                } ?: intent.data

                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

                if (streamUri != null) {
                    viewModel.importUri(this, streamUri)
                    viewModel.navigateTo(ScreenRoute.CONTENT_SELECTION)
                } else if (clipData != null && clipData.itemCount > 0) {
                    val uris = mutableListOf<Uri>()
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                    if (uris.isNotEmpty()) {
                        viewModel.importMultipleUris(this, uris)
                        viewModel.navigateTo(ScreenRoute.CONTENT_SELECTION)
                    }
                } else if (!sharedText.isNullOrBlank()) {
                    if (sharedText.startsWith("http://") || sharedText.startsWith("https://")) {
                        viewModel.addUrlContent(sharedText)
                    } else {
                        viewModel.addTextContent(sharedText)
                    }
                    viewModel.navigateTo(ScreenRoute.CONTENT_SELECTION)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }

                if (!streamUris.isNullOrEmpty()) {
                    viewModel.importMultipleUris(this, streamUris)
                    viewModel.navigateTo(ScreenRoute.CONTENT_SELECTION)
                }
            }
        }
    }
}

@Composable
fun AirShareApp(
    viewModel: AirShareViewModel,
    settingsState: AppSettingsState
) {
    val palette = GestoTheme.colors
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val stagedItems by viewModel.stagedItems.collectAsStateWithLifecycle()
    val targetDevice by viewModel.targetDevice.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.nearbyManager.isDiscovering.collectAsStateWithLifecycle()
    val isAdvertising by viewModel.nearbyManager.isAdvertising.collectAsStateWithLifecycle()
    val activeTransfer by viewModel.activeTransfer.collectAsStateWithLifecycle()
    val endpointTransfers by viewModel.endpointTransfers.collectAsStateWithLifecycle()
    val incomingTransferOffer by viewModel.incomingTransferOffer.collectAsStateWithLifecycle()
    val historyTransfers by viewModel.historyTransfers.collectAsStateWithLifecycle()

    val currentGesture by viewModel.gestureManager.currentGesture.collectAsStateWithLifecycle()
    val sessionState by viewModel.gestureManager.sessionState.collectAsStateWithLifecycle()
    val isTrackingHand by viewModel.gestureManager.isTrackingHand.collectAsStateWithLifecycle()
    val handTrackingState by viewModel.handTrackingState.collectAsStateWithLifecycle()
    val liveOnnxPrediction by viewModel.liveOnnxPrediction.collectAsStateWithLifecycle()
    val onnxConfidenceThreshold by viewModel.onnxConfidenceThreshold.collectAsStateWithLifecycle()
    val onnxLatencyStats by viewModel.onnxLatencyStats.collectAsStateWithLifecycle()
    val predictionHistory by viewModel.predictionHistory.collectAsStateWithLifecycle()
    val stabilizerDecision by viewModel.stabilizerDecision.collectAsStateWithLifecycle()
    val stabilizerConfig by viewModel.stabilizerConfig.collectAsStateWithLifecycle()
    val operationMode by viewModel.operationMode.collectAsStateWithLifecycle()
    val sessionFeedback by viewModel.sessionFeedback.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val isContentGrabbed by viewModel.isContentGrabbed.collectAsStateWithLifecycle()

    val pendingSession by viewModel.pendingSession.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val connectedDevices by viewModel.connectedDevices.collectAsStateWithLifecycle()
    val recentDevices by viewModel.recentDevices.collectAsStateWithLifecycle()
    val multiSelectMode by viewModel.multiSelectMode.collectAsStateWithLifecycle()
    val multiSelectRecipientIds by viewModel.multiSelectRecipientIds.collectAsStateWithLifecycle()
    val receivedMessages by viewModel.receivedMessages.collectAsStateWithLifecycle()
    val nearbyStatusMessage by viewModel.nearbyStatusMessage.collectAsStateWithLifecycle()
    val isNearbyAvailable by viewModel.isNearbyAvailable.collectAsStateWithLifecycle()

    val errorEvents by viewModel.errorEvents.collectAsStateWithLifecycle()
    val operationalLogs by viewModel.operationalLogs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val nearbyPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            else -> arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    val onboardingPermissions = remember(nearbyPermissions) {
        (nearbyPermissions.toList() + android.Manifest.permission.CAMERA).distinct().toTypedArray()
    }

    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            val allGranted = permissionsMap.values.all { it }
            if (allGranted) {
                pendingActionAfterPermission?.invoke()
            } else {
                Toast.makeText(
                    context,
                    "Nearby permissions are needed for offline device discovery.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingActionAfterPermission = null
        }
    )

    val onboardingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            val grantedCount = permissionsMap.values.count { it }
            Toast.makeText(
                context,
                "$grantedCount of ${permissionsMap.size} permissions granted.",
                Toast.LENGTH_SHORT
            ).show()
        }
    )

    fun checkAndExecuteWithNearbyPermissions(action: () -> Unit) {
        val allGranted = nearbyPermissions.all { perm ->
            ContextCompat.checkSelfPermission(
                context, perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            action()
        } else {
            pendingActionAfterPermission = action
            nearbyPermissionLauncher.launch(nearbyPermissions)
        }
    }

    fun copyErrorReportToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("GestoShare Error Report", text))
        Toast.makeText(context, "Error report copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareDiagnosticsExport(asJson: Boolean) {
        coroutineScope.launch {
            val uri = viewModel.prepareDiagnosticsExportUri(context, asJson)
            if (uri == null) {
                Toast.makeText(context, "Unable to create diagnostics export file.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val mimeType = if (asJson) "application/json" else "text/plain"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "GestoShare Diagnostics (${if (asJson) "JSON" else "TXT"})"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export GestoShare Diagnostics"))
        }
    }

    BackHandler(enabled = currentScreen != ScreenRoute.HOME && currentScreen != ScreenRoute.SPLASH) {
        viewModel.navigateBack()
    }

    val showChrome = currentScreen != ScreenRoute.SPLASH && currentScreen != ScreenRoute.ONBOARDING

    GestoGlassBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (showChrome) {
                    AirShareTopBar(
                        currentRoute = currentScreen,
                        isAdvertising = isAdvertising,
                        errorCount = errorEvents.size,
                        onBackClick = { viewModel.navigateBack() },
                        onSettingsClick = { viewModel.navigateTo(ScreenRoute.SETTINGS) },
                        onDiagnosticsClick = { viewModel.navigateTo(ScreenRoute.DIAGNOSTICS) },
                        onToggleAdvertising = {
                            checkAndExecuteWithNearbyPermissions {
                                viewModel.toggleNearbyAdvertising()
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (showChrome) {
                    AirShareBottomNav(
                        currentRoute = currentScreen,
                        onNavigate = { route -> viewModel.navigateTo(route) },
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    ScreenRoute.SPLASH -> {
                        SplashScreen(
                            onFinished = { viewModel.advanceFromSplash() }
                        )
                    }

                    ScreenRoute.ONBOARDING -> {
                        OnboardingScreen(
                            onComplete = { viewModel.completeOnboarding() },
                            onRequestPermissions = {
                                onboardingPermissionLauncher.launch(onboardingPermissions)
                            }
                        )
                    }

                    ScreenRoute.HOME -> {
                        HomeScreen(
                            deviceName = deviceName,
                            stagedItems = stagedItems,
                            discoveredDevices = discoveredDevices,
                            connectedDevices = connectedDevices,
                            errorCount = errorEvents.size,
                            isAdvertising = isAdvertising,
                            recentTransfers = historyTransfers,
                            onNavigate = { route -> viewModel.navigateTo(route) },
                            onToggleAdvertising = {
                                checkAndExecuteWithNearbyPermissions {
                                    viewModel.toggleNearbyAdvertising()
                                }
                            }
                        )
                    }

                    ScreenRoute.CONTENT_SELECTION -> {
                        ContentSelectionScreen(
                            stagedItems = stagedItems,
                            onImportUris = { uris ->
                                viewModel.importMultipleUris(context, uris)
                            },
                            onAddText = { text -> viewModel.addTextContent(text) },
                            onAddUrl = { url -> viewModel.addUrlContent(url) },
                            onRemoveItem = { id -> viewModel.removeStagedItem(id) },
                            onClearAll = { viewModel.clearStagedItems() },
                            onNavigate = { route -> viewModel.navigateTo(route) },
                            formatSize = { bytes -> viewModel.contentImportManager.formatFileSize(bytes) }
                        )
                    }

                    ScreenRoute.NEARBY_DEVICES -> {
                        NearbyDevicesScreen(
                            discoveredDevices = discoveredDevices,
                            selectedDevice = targetDevice,
                            connectedDevice = connectedDevice,
                            connectedDevices = connectedDevices,
                            pendingSession = pendingSession,
                            recentDevices = recentDevices,
                            receivedMessages = receivedMessages,
                            statusMessage = nearbyStatusMessage,
                            isDiscovering = isDiscovering,
                            isAdvertising = isAdvertising,
                            isNearbyAvailable = isNearbyAvailable,
                            stagedItems = stagedItems,
                            isMultiSelectMode = multiSelectMode,
                            multiSelectRecipientIds = multiSelectRecipientIds,
                            onStartDiscovery = {
                                checkAndExecuteWithNearbyPermissions {
                                    viewModel.startNearbyDiscovery()
                                }
                            },
                            onStopDiscovery = { viewModel.stopNearbyDiscovery() },
                            onToggleAdvertising = {
                                checkAndExecuteWithNearbyPermissions {
                                    viewModel.toggleNearbyAdvertising()
                                }
                            },
                            onRequestConnection = { dev ->
                                checkAndExecuteWithNearbyPermissions {
                                    viewModel.requestNearbyConnection(dev)
                                }
                            },
                            onAcceptConnection = { endpointId -> viewModel.acceptNearbyConnection(endpointId) },
                            onRejectConnection = { endpointId -> viewModel.rejectNearbyConnection(endpointId) },
                            onDisconnectDevice = { endpointId -> viewModel.disconnectNearbyDevice(endpointId) },
                            onForgetDevice = { endpointId -> viewModel.forgetNearbyDevice(endpointId) },
                            onForgetRecentDevice = { key -> viewModel.forgetRecentDevice(key) },
                            onSelectDevice = { dev -> viewModel.selectTargetDevice(dev) },
                            onToggleMultiSelectMode = { viewModel.toggleMultiSelectMode() },
                            onToggleMultiSelectRecipient = { dev -> viewModel.toggleMultiSelectRecipient(dev) },
                            onSendTextMessage = { msg -> viewModel.sendNearbyTextMessage(msg) },
                            onSendTextMessageTo = { endpointId, msg ->
                                viewModel.sendNearbyTextMessageTo(endpointId, msg)
                            },
                            onSendPingTest = { viewModel.sendNearbyPingTest() },
                            onExecuteTransfer = { dev -> viewModel.executeTransfer(dev) },
                            onExecuteMultiTransfer = { viewModel.executeMultiTransfer() },
                            onNavigate = { route -> viewModel.navigateTo(route) }
                        )
                    }

                    ScreenRoute.GESTURE_CONTROL -> {
                        GestureControlScreen(
                            currentGesture = currentGesture,
                            liveOnnxPrediction = liveOnnxPrediction,
                            onnxDiagnostics = viewModel.onnxDiagnostics,
                            confidenceThreshold = onnxConfidenceThreshold,
                            latencyStats = onnxLatencyStats,
                            predictionHistory = predictionHistory,
                            stabilizerDecision = stabilizerDecision,
                            stabilizerConfig = stabilizerConfig,
                            operationMode = operationMode,
                            sessionState = sessionState,
                            sessionFeedback = sessionFeedback,
                            commandLogs = commandLogs,
                            isContentGrabbed = isContentGrabbed,
                            stagedItems = stagedItems,
                            discoveredDevices = discoveredDevices,
                            selectedDevice = targetDevice,
                            connectedDevice = connectedDevice,
                            connectedDevices = connectedDevices,
                            isMultiSelectMode = multiSelectMode,
                            multiSelectRecipientIds = multiSelectRecipientIds,
                            useFrontCamera = settingsState.useFrontCamera,
                            pendingSession = pendingSession,
                            isTrackingHand = isTrackingHand,
                            handTrackingState = handTrackingState,
                            handTracker = viewModel.handTracker,
                            classifierInfo = viewModel.gestureManager.classifierInfo,
                            onSelectOperationMode = { viewModel.setGestureOperationMode(it) },
                            onUpdateConfidenceThreshold = { viewModel.updateConfidenceThreshold(it) },
                            onUpdateConsecutiveFrames = { viewModel.updateStabilizerConsecutiveFrames(it) },
                            onToggleCameraFacing = { viewModel.toggleCameraFacing() },
                            onClearPredictionHistory = { viewModel.clearPredictionHistory() },
                            onDismissFeedback = { viewModel.clearSessionFeedback() },
                            onStartDiscovery = {
                                checkAndExecuteWithNearbyPermissions {
                                    viewModel.startNearbyDiscovery()
                                }
                            },
                            onSelectDevice = { dev -> viewModel.selectTargetDevice(dev) },
                            onToggleMultiSelectMode = { viewModel.toggleMultiSelectMode() },
                            onToggleMultiRecipient = { dev -> viewModel.toggleMultiSelectRecipient(dev) },
                            onRequestConnection = { dev ->
                                checkAndExecuteWithNearbyPermissions {
                                    viewModel.requestNearbyConnection(dev)
                                }
                            },
                            onAcceptConnection = { endpointId -> viewModel.acceptNearbyConnection(endpointId) },
                            onRejectConnection = { endpointId -> viewModel.rejectNearbyConnection(endpointId) },
                            onExecuteManualTransfer = { dev -> viewModel.executeTransfer(dev) },
                            onManualCancel = { viewModel.cancelGestureWorkflowManually() },
                            onSimulateGesture = { gesture -> viewModel.simulateGesture(gesture) },
                            onResetSession = { viewModel.resetGestureSession() },
                            onNavigate = { route -> viewModel.navigateTo(route) }
                        )
                    }

                    ScreenRoute.TRANSFER -> {
                        TransferScreen(
                            activeTransfer = activeTransfer,
                            endpointTransfers = endpointTransfers,
                            onCancelTransfer = { viewModel.cancelActiveTransfer() },
                            onCancelEndpointTransfer = { endpointId ->
                                viewModel.cancelEndpointTransfer(endpointId)
                            },
                            onRetryTransfer = { viewModel.retryActiveTransfer() },
                            onOpenReceivedFile = { uriString, mimeType ->
                                viewModel.openReceivedFile(context, uriString, mimeType)
                            },
                            onNavigate = { route -> viewModel.navigateTo(route) },
                            formatSize = { bytes -> viewModel.contentImportManager.formatFileSize(bytes) }
                        )
                    }

                    ScreenRoute.HISTORY -> {
                        TransferHistoryScreen(
                            transfers = historyTransfers,
                            onClearHistory = { viewModel.clearHistory() },
                            onDeleteRecord = { id -> viewModel.deleteHistoryRecord(id) }
                        )
                    }

                    ScreenRoute.SETTINGS -> {
                        SettingsScreen(
                            currentDeviceName = deviceName,
                            settingsState = settingsState,
                            errorCount = errorEvents.size,
                            onUpdateDeviceName = { name -> viewModel.updateDeviceName(name) },
                            onToggleDarkTheme = { dark -> viewModel.toggleDarkTheme(dark) },
                            onUpdateConfidenceThreshold = { viewModel.updateConfidenceThreshold(it) },
                            onUpdateConsecutiveFrames = { viewModel.updateStabilizerConsecutiveFrames(it) },
                            onUpdateCooldownMs = { viewModel.updateStabilizerCooldownMs(it) },
                            onUpdateHoldDurationMs = { viewModel.updateStabilizerHoldDurationMs(it) },
                            onToggleCameraFacing = { viewModel.toggleCameraFacing() },
                            onToggleHapticFeedback = { viewModel.toggleHapticFeedback(it) },
                            onToggleSoundFeedback = { viewModel.toggleSoundFeedback(it) },
                            onToggleGestureTransferMode = { enabled ->
                                viewModel.setGestureOperationMode(
                                    if (enabled) com.example.model.GestureOperationMode.GESTURE_TRANSFER
                                    else com.example.model.GestureOperationMode.DEBUG_DRY_RUN
                                )
                            },
                            onOpenDiagnostics = { viewModel.navigateTo(ScreenRoute.DIAGNOSTICS) },
                            onOpenAbout = { viewModel.navigateTo(ScreenRoute.ABOUT) },
                            onReplayOnboarding = { viewModel.replayOnboarding() }
                        )
                    }

                    ScreenRoute.DIAGNOSTICS -> {
                        val nearbySummary = "${nearbyStatusMessage} (connected=${connectedDevices.size}, discovered=${discoveredDevices.size})"
                        DiagnosticsScreen(
                            errorEvents = errorEvents,
                            operationalLogs = operationalLogs,
                            modelSummary = viewModel.onnxDiagnostics.statusMessage,
                            nearbySummary = nearbySummary,
                            onCopyErrorReport = { event ->
                                copyErrorReportToClipboard(viewModel.formatSingleErrorReport(event))
                            },
                            onExportDiagnosticsTxt = { shareDiagnosticsExport(asJson = false) },
                            onExportDiagnosticsJson = { shareDiagnosticsExport(asJson = true) },
                            onDeleteErrorEvent = { id -> viewModel.deleteDiagnosticEvent(id) },
                            onClearAllDiagnostics = { viewModel.clearAllDiagnostics() }
                        )
                    }

                    ScreenRoute.ABOUT -> {
                        AboutScreen(
                            onNavigate = { route -> viewModel.navigateTo(route) },
                            onReplayOnboarding = { viewModel.replayOnboarding() }
                        )
                    }
                }
            }
        }

        // Global Incoming Transfer Consent Dialog (Phase 4 Receiver Acceptance)
        incomingTransferOffer?.let { offer ->
            AlertDialog(
                onDismissRequest = { /* User must explicitly Accept or Decline */ },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .glassSurface(
                                cornerRadius = 24.dp,
                                fillColor = palette.glassFillElevated,
                                borderAccent = palette.accentPrimary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Incoming Transfer",
                            tint = palette.accentPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Incoming File Transfer",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${offer.senderName} wants to send ${offer.items.size} item(s) (${viewModel.contentImportManager.formatFileSize(offer.totalBytes)}).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        offer.items.take(4).forEach { meta ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .glassSurface(
                                        cornerRadius = 8.dp,
                                        fillColor = palette.glassFill,
                                        borderAccent = palette.glassBorder
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = meta.name,
                                    fontSize = 12.sp,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = viewModel.contentImportManager.formatFileSize(meta.sizeBytes),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.accentPrimary
                                )
                            }
                        }
                        if (offer.items.size > 4) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "+ ${offer.items.size - 4} more file(s)",
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.acceptIncomingTransfer(offer.transferId) },
                        modifier = Modifier.testTag("accept_incoming_transfer_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.success,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Accept & Save")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.rejectIncomingTransfer(offer.transferId) },
                        modifier = Modifier.testTag("decline_incoming_transfer_button"),
                        border = BorderStroke(1.dp, palette.error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Decline", color = palette.error)
                    }
                },
                containerColor = palette.dialogSurface
            )
        }
    }
}
