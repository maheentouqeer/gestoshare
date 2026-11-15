package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.HandTrackingState
import com.example.manager.LatencyStats
import com.example.manager.MediaPipeHandTracker
import com.example.manager.OnnxModelDiagnostics
import com.example.manager.PredictionHistoryEntry
import com.example.manager.StabilizedGestureDecision
import com.example.manager.TemporalGestureConfig
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.GestureOperationMode
import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.example.model.SessionStatus
import com.example.ui.ScreenRoute
import com.example.ui.components.CameraPreviewPlaceholder
import com.example.ui.components.GlassCameraRecipientOverlay
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GestureControlScreen(
    currentGesture: GestureResult,
    liveOnnxPrediction: GestureResult,
    onnxDiagnostics: OnnxModelDiagnostics,
    confidenceThreshold: Float,
    latencyStats: LatencyStats,
    predictionHistory: List<PredictionHistoryEntry>,
    stabilizerDecision: StabilizedGestureDecision,
    stabilizerConfig: TemporalGestureConfig,
    operationMode: GestureOperationMode,
    sessionState: GestureSessionState,
    sessionFeedback: String?,
    commandLogs: List<String>,
    isContentGrabbed: Boolean,
    stagedItems: List<ContentItem>,
    discoveredDevices: List<DeviceItem>,
    selectedDevice: DeviceItem?,
    connectedDevice: DeviceItem?,
    connectedDevices: List<DeviceItem> = listOfNotNull(connectedDevice),
    isMultiSelectMode: Boolean = false,
    multiSelectRecipientIds: Set<String> = emptySet(),
    useFrontCamera: Boolean = true,
    pendingSession: ConnectionSession?,
    isTrackingHand: Boolean,
    handTrackingState: HandTrackingState,
    handTracker: MediaPipeHandTracker,
    classifierInfo: String,
    onSelectOperationMode: (GestureOperationMode) -> Unit,
    onUpdateConfidenceThreshold: (Float) -> Unit,
    onUpdateConsecutiveFrames: (Int) -> Unit,
    onToggleCameraFacing: () -> Unit = {},
    onClearPredictionHistory: () -> Unit,
    onDismissFeedback: () -> Unit,
    onStartDiscovery: () -> Unit,
    onSelectDevice: (DeviceItem) -> Unit,
    onToggleMultiSelectMode: () -> Unit = {},
    onToggleMultiRecipient: (DeviceItem) -> Unit = {},
    onRequestConnection: (DeviceItem) -> Unit,
    onAcceptConnection: (String) -> Unit,
    onRejectConnection: (String) -> Unit,
    onExecuteManualTransfer: (DeviceItem) -> Unit,
    onManualCancel: () -> Unit,
    onSimulateGesture: (GestureType) -> Unit,
    onResetSession: () -> Unit,
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    val intendedCommands = listOf(
        GestureType.LIKE,
        GestureType.FIST,
        GestureType.POINT,
        GestureType.THREE_GUN,
        GestureType.STOP
    )

    val displayedPrediction = if (handTrackingState.isHandDetected || liveOnnxPrediction.errorMessage != null) {
        liveOnnxPrediction
    } else if (currentGesture.gesture != GestureType.NONE) {
        currentGesture
    } else {
        liveOnnxPrediction
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("gesture_control_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Explicit Mode Selector Card: DEBUG / DRY RUN vs GESTURE TRANSFER MODE
        item {
            val isTransferMode = operationMode == GestureOperationMode.GESTURE_TRANSFER
            val accent = if (isTransferMode) palette.success else palette.accentPrimary

            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("gesture_mode_selector_card"),
                cornerRadius = 18.dp,
                fillColor = accent.copy(alpha = 0.13f),
                borderAccent = accent.copy(alpha = 0.65f),
                borderWidth = 1.5.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(imageVector = if (isTransferMode) Icons.Default.Send else Icons.Default.Science, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (isTransferMode) "GESTURE TRANSFER MODE" else "DEBUG / DRY RUN MODE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = accent
                            )
                        }
                        GlassStatusBadge(label = if (isTransferMode) "REAL P2P ARMED" else "TRANSFERS OFF", color = accent)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(operationMode.subtitle, fontSize = 11.sp, color = palette.textSecondary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSelectOperationMode(GestureOperationMode.DEBUG_DRY_RUN) },
                            modifier = Modifier.weight(1f).height(38.dp).testTag("mode_debug_dry_run_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isTransferMode) palette.accentPrimary else palette.glassFillInset,
                                contentColor = if (!isTransferMode) palette.onAccentPrimary else palette.textSecondary
                            ),
                            border = BorderStroke(1.dp, if (!isTransferMode) palette.accentPrimary else palette.glassBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Debug / Dry Run", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSelectOperationMode(GestureOperationMode.GESTURE_TRANSFER) },
                            modifier = Modifier.weight(1f).height(38.dp).testTag("mode_gesture_transfer_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTransferMode) palette.success else palette.glassFillInset,
                                contentColor = if (isTransferMode) Color.White else palette.textSecondary
                            ),
                            border = BorderStroke(1.dp, if (isTransferMode) palette.success else palette.glassBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Gesture Transfer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Contextual Error / Action Feedback Banner
        if (!sessionFeedback.isNullOrBlank()) {
            item {
                val isError = sessionFeedback.contains("no content", ignoreCase = true) ||
                    sessionFeedback.contains("no nearby", ignoreCase = true) ||
                    sessionFeedback.contains("blocked", ignoreCase = true) ||
                    sessionFeedback.contains("failed", ignoreCase = true) ||
                    sessionFeedback.contains("rejected", ignoreCase = true) ||
                    sessionFeedback.contains("lost", ignoreCase = true) ||
                    sessionFeedback.contains("cancelled", ignoreCase = true)

                val fbColor = if (isError) palette.error else palette.success
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("session_feedback_banner"),
                    cornerRadius = 14.dp,
                    fillColor = fbColor.copy(alpha = 0.14f),
                    borderAccent = fbColor
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle, contentDescription = null, tint = fbColor, modifier = Modifier.size(16.dp))
                            Text(text = sessionFeedback, fontSize = 11.sp, color = palette.textPrimary, lineHeight = 15.sp)
                        }
                        IconButton(onClick = onDismissFeedback, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = "Dismiss", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Live CameraX Preview with Docked Top Recipient Selector Overlay
        item {
            CameraPreviewPlaceholder(
                currentGesture = displayedPrediction,
                handTrackingState = handTrackingState,
                handTracker = handTracker,
                isTrackingHandFallback = isTrackingHand,
                useFrontCamera = useFrontCamera,
                onToggleCamera = onToggleCameraFacing,
                topOverlayContent = {
                    GlassCameraRecipientOverlay(
                        connectedDevices = connectedDevices,
                        discoveredDevices = discoveredDevices,
                        selectedDevice = selectedDevice,
                        isMultiSelectMode = isMultiSelectMode,
                        multiSelectRecipientIds = multiSelectRecipientIds,
                        onSelectDevice = onSelectDevice,
                        onToggleMultiRecipient = onToggleMultiRecipient,
                        onRequestConnection = onRequestConnection,
                        onToggleMultiSelectMode = onToggleMultiSelectMode,
                        onManageDevicesClick = { onNavigate(ScreenRoute.NEARBY_DEVICES) }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(330.dp)
            )
        }

        // Temporal Stability & Debounce Strip
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("temporal_stability_card"),
                cornerRadius = 16.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = when {
                    stabilizerDecision.isHeldLocked -> palette.warning.copy(alpha = 0.7f)
                    stabilizerDecision.consecutiveCount > 0 -> palette.accentPrimary.copy(alpha = 0.65f)
                    else -> palette.accentSecondary.copy(alpha = 0.35f)
                }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Temporal Stability & Debounce", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(
                            text = "${stabilizerDecision.consecutiveCount}/${stabilizerConfig.requiredConsecutiveFrames} frames",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = (stabilizerDecision.consecutiveCount.toFloat() /
                        stabilizerConfig.requiredConsecutiveFrames.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (stabilizerDecision.isHeldLocked) palette.warning else palette.success,
                        trackColor = palette.glassFillInset
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stabilizerDecision.statusReason,
                        fontSize = 11.sp,
                        color = if (stabilizerDecision.isHeldLocked) palette.warning else palette.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Required Consecutive Frames:", fontSize = 10.sp, color = palette.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 7).forEach { count ->
                                val selected = stabilizerConfig.requiredConsecutiveFrames == count
                                Box(
                                    modifier = Modifier
                                        .glassSurface(
                                            cornerRadius = 8.dp,
                                            fillColor = if (selected) palette.accentPrimary.copy(alpha = 0.22f) else palette.glassFillInset,
                                            borderAccent = if (selected) palette.accentPrimary else palette.glassBorder
                                        )
                                        .clickable { onUpdateConsecutiveFrames(count) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) palette.accentPrimary else palette.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Complete Gesture State Machine + Manual Cancel Fallback
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("session_state_banner"),
                cornerRadius = 18.dp,
                borderAccent = when (sessionState) {
                    GestureSessionState.COMPLETED -> palette.success
                    GestureSessionState.CANCELLED, GestureSessionState.FAILED -> palette.error
                    else -> palette.accentPrimary.copy(alpha = 0.55f)
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(10.dp).background(
                                    when (sessionState) {
                                        GestureSessionState.COMPLETED, GestureSessionState.CONNECTED -> palette.success
                                        GestureSessionState.CANCELLED, GestureSessionState.FAILED -> palette.error
                                        GestureSessionState.IDLE -> palette.textSecondary
                                        else -> palette.accentPrimary
                                    },
                                    CircleShape
                                )
                            )
                            Text("STATE: ${sessionState.name}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = palette.accentPrimary)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onManualCancel,
                                modifier = Modifier.height(32.dp).testTag("manual_cancel_workflow_button"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, palette.error.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = "Cancel Workflow", tint = palette.error, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", fontSize = 10.sp, color = palette.error, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onResetSession,
                                modifier = Modifier.height(32.dp).testTag("reset_session_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Reset", fontSize = 10.sp, color = palette.textSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(sessionState.statusLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val stages = listOf(
                            "LIKE" to (sessionState != GestureSessionState.IDLE),
                            "FIST (${stagedItems.size})" to (isContentGrabbed || sessionState.ordinal >= GestureSessionState.CONTENT_HELD.ordinal && sessionState.isActiveSession),
                            "POINT" to (selectedDevice != null || sessionState.ordinal >= GestureSessionState.TARGET_SELECTED.ordinal && sessionState.isActiveSession),
                            "PAIR (${connectedDevices.size})" to (connectedDevices.isNotEmpty() || sessionState == GestureSessionState.CONNECTED),
                            "THREE_GUN" to (sessionState == GestureSessionState.TRANSFER_OFFERED ||
                                sessionState == GestureSessionState.WAITING_FOR_RECEIVER ||
                                sessionState == GestureSessionState.TRANSFERRING ||
                                sessionState == GestureSessionState.VERIFYING ||
                                sessionState == GestureSessionState.COMPLETED)
                        )
                        stages.forEach { (label, active) ->
                            Box(
                                modifier = Modifier
                                    .background(if (active) palette.accentPrimary.copy(alpha = 0.18f) else palette.glassFillInset, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (active) palette.accentPrimary else palette.glassBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) palette.accentPrimary else palette.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                cornerRadius = 10.dp,
                                fillColor = if (isContentGrabbed && stagedItems.isNotEmpty()) palette.success.copy(alpha = 0.14f) else palette.glassFillInset,
                                borderAccent = if (isContentGrabbed && stagedItems.isNotEmpty()) palette.success else palette.glassBorder
                            )
                            .clickable { onNavigate(ScreenRoute.CONTENT_SELECTION) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isContentGrabbed) Icons.Default.Lock else Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = if (stagedItems.isEmpty()) palette.warning else if (isContentGrabbed) palette.success else palette.accentPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    stagedItems.isEmpty() -> "No content staged — tap to select files/text/URL"
                                    isContentGrabbed -> "Content Grabbed: ${stagedItems.size} item(s) held (${stagedItems.first().name})"
                                    else -> "${stagedItems.size} item(s) staged — show ✊ FIST to grab"
                                },
                                fontSize = 11.sp,
                                color = palette.textPrimary,
                                maxLines = 1
                            )
                        }
                        Text("Edit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentPrimary)
                    }

                    val manualTarget = selectedDevice?.takeIf { sel -> connectedDevices.any { it.endpointId == sel.endpointId } }
                        ?: connectedDevices.firstOrNull()
                    if (manualTarget != null && stagedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onExecuteManualTransfer(manualTarget) },
                            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("manual_send_fallback_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary, contentColor = palette.onAccentPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual Send to ${manualTarget.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Inline Pairing Handshake Prompt
        if (pendingSession != null && pendingSession.status == SessionStatus.INITIATED) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("gesture_inline_handshake_card"),
                    cornerRadius = 16.dp,
                    fillColor = palette.accentPrimary.copy(alpha = 0.14f),
                    borderAccent = palette.accentPrimary,
                    borderWidth = 1.5.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Nearby Pairing Code (${pendingSession.endpointName}): ${pendingSession.authDigits}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAcceptConnection(pendingSession.endpointId) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.success, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Accept Code", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onRejectConnection(pendingSession.endpointId) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                border = BorderStroke(1.dp, palette.error),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reject", fontSize = 11.sp, color = palette.error)
                            }
                        }
                    }
                }
            }
        }

        // Discovered & Connected Cluster Peers Card
        item {
            val combinedPeers = (connectedDevices + discoveredDevices).distinctBy { it.endpointId }
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("gesture_discovered_peers_card"),
                cornerRadius = 16.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = palette.accentSecondary.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cluster Peers (${connectedDevices.size} linked / ${discoveredDevices.size} nearby)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text("👉 POINT cycles recipients · THREE_GUN sends to ★ selected peer", fontSize = 10.sp, color = palette.textSecondary)
                        }
                        OutlinedButton(
                            onClick = onStartDiscovery,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Scan", fontSize = 10.sp, color = palette.accentPrimary) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (combinedPeers.isEmpty()) {
                        Text("No peers discovered yet. Start Scan on this device and Receive Mode on peer.", fontSize = 11.sp, color = palette.textSecondary)
                    } else {
                        combinedPeers.forEach { dev ->
                            val isSelected = selectedDevice?.endpointId == dev.endpointId
                            val isConn = connectedDevices.any { it.endpointId == dev.endpointId } || dev.isConnected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .glassSurface(
                                        cornerRadius = 10.dp,
                                        fillColor = when {
                                            isConn -> palette.success.copy(alpha = 0.14f)
                                            isSelected -> palette.accentPrimary.copy(alpha = 0.14f)
                                            else -> palette.glassFillInset
                                        },
                                        borderAccent = when {
                                            isConn -> palette.success
                                            isSelected -> palette.accentPrimary
                                            else -> palette.glassBorder
                                        }
                                    )
                                    .clickable { onSelectDevice(dev) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("gesture_peer_${dev.endpointId}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (isConn) palette.success else if (isSelected) palette.accentPrimary else palette.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = dev.name + if (isSelected) " ★" else "",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = "ID: ${dev.endpointId} · ${dev.connectionState.badgeLabel}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isConn) palette.success else palette.textSecondary
                                        )
                                    }
                                }

                                if (!isConn) {
                                    Button(
                                        onClick = { onSelectDevice(dev); onRequestConnection(dev) },
                                        enabled = !dev.isBusyConnecting,
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary, contentColor = palette.onAccentPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Text(if (dev.isBusyConnecting) "Pairing..." else "Connect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text("CONNECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = palette.success)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Extracted Section Cards
        item {
            OnnxProbabilityDashboardCard(
                liveOnnxPrediction = liveOnnxPrediction,
                onnxDiagnostics = onnxDiagnostics,
                confidenceThreshold = confidenceThreshold,
                onUpdateConfidenceThreshold = onUpdateConfidenceThreshold
            )
        }

        item {
            OnnxAssetDiagnosticsCard(
                onnxDiagnostics = onnxDiagnostics,
                handTrackingState = handTrackingState,
                latencyStats = latencyStats,
                classifierInfo = classifierInfo
            )
        }

        item {
            CommandPredictionLogsCard(
                commandLogs = commandLogs,
                predictionHistory = predictionHistory,
                onClearPredictionHistory = onClearPredictionHistory
            )
        }

        item {
            Text(
                text = "5-Command Gesture Mapping (Manual Test Hooks)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        }

        items(intendedCommands) { command ->
            val isActive = (liveOnnxPrediction.isAccepted && liveOnnxPrediction.gesture == command) || currentGesture.gesture == command
            GestureCommandHookCard(command = command, isActive = isActive, onSimulateGesture = onSimulateGesture)
        }
    }
}
