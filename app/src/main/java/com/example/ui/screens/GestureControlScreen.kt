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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.manager.HaGridV2OnnxClassifier
import com.example.manager.HandTrackerStatus
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
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassFillElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    pendingSession: ConnectionSession?,
    isTrackingHand: Boolean,
    handTrackingState: HandTrackingState,
    handTracker: MediaPipeHandTracker,
    classifierInfo: String,
    onSelectOperationMode: (GestureOperationMode) -> Unit,
    onUpdateConfidenceThreshold: (Float) -> Unit,
    onUpdateConsecutiveFrames: (Int) -> Unit,
    onClearPredictionHistory: () -> Unit,
    onDismissFeedback: () -> Unit,
    onStartDiscovery: () -> Unit,
    onSelectDevice: (DeviceItem) -> Unit,
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

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gesture_mode_selector_card"),
                cornerRadius = 18.dp,
                fillColor = if (isTransferMode) EmeraldSuccess.copy(alpha = 0.14f) else Color(0x2200E5FF),
                borderAccent = if (isTransferMode) EmeraldSuccess else NeonCyan.copy(alpha = 0.65f),
                borderWidth = 1.5.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isTransferMode) Icons.Default.Send else Icons.Default.Science,
                                contentDescription = null,
                                tint = if (isTransferMode) EmeraldSuccess else NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isTransferMode) "GESTURE TRANSFER MODE" else "DEBUG / DRY RUN MODE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isTransferMode) EmeraldSuccess else NeonCyan
                            )
                        }

                        Box(
                            modifier = Modifier
                                .glassSurface(
                                    cornerRadius = 8.dp,
                                    fillColor = if (isTransferMode) Color(0x4410B981) else Color(0x4400E5FF),
                                    borderAccent = if (isTransferMode) EmeraldSuccess else NeonCyan
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isTransferMode) "REAL P2P ARMED" else "TRANSFERS OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isTransferMode) EmeraldSuccess else NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = operationMode.subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectOperationMode(GestureOperationMode.DEBUG_DRY_RUN) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("mode_debug_dry_run_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isTransferMode) NeonCyan else Color(0x55060B18),
                                contentColor = if (!isTransferMode) DarkBackground else TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (!isTransferMode) NeonCyan else Color.White.copy(alpha = 0.2f)),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Debug / Dry Run", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSelectOperationMode(GestureOperationMode.GESTURE_TRANSFER) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("mode_gesture_transfer_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTransferMode) EmeraldSuccess else Color(0x55060B18),
                                contentColor = if (isTransferMode) Color.White else TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (isTransferMode) EmeraldSuccess else Color.White.copy(alpha = 0.2f)),
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
                    sessionFeedback.contains("insufficient", ignoreCase = true) ||
                    sessionFeedback.contains("unavailable", ignoreCase = true) ||
                    sessionFeedback.contains("cancelled", ignoreCase = true)

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_feedback_banner"),
                    cornerRadius = 14.dp,
                    fillColor = if (isError) Color(0x33EF4444) else Color(0x2810B981),
                    borderAccent = if (isError) RoseError else EmeraldSuccess
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) RoseError else EmeraldSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = sessionFeedback,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 15.sp
                            )
                        }
                        IconButton(
                            onClick = onDismissFeedback,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live CameraX Preview + MediaPipe 21-Landmark Skeleton + Live ONNX HUD
        item {
            CameraPreviewPlaceholder(
                currentGesture = displayedPrediction,
                handTrackingState = handTrackingState,
                handTracker = handTracker,
                isTrackingHandFallback = isTrackingHand,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            )
        }

        // Temporal Stability, Hold & Neutral Debounce Strip
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("temporal_stability_card"),
                cornerRadius = 16.dp,
                fillColor = GlassFillElevated,
                borderAccent = when {
                    stabilizerDecision.isHeldLocked -> Color(0xFFF59E0B).copy(alpha = 0.7f)
                    stabilizerDecision.consecutiveCount > 0 -> NeonCyan.copy(alpha = 0.65f)
                    else -> ElectricViolet.copy(alpha = 0.35f)
                }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Temporal Stability & Debounce",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${stabilizerDecision.consecutiveCount}/${stabilizerConfig.requiredConsecutiveFrames} frames",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val progress = (stabilizerDecision.consecutiveCount.toFloat() /
                        stabilizerConfig.requiredConsecutiveFrames.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (stabilizerDecision.isHeldLocked) Color(0xFFF59E0B) else EmeraldSuccess,
                        trackColor = Color(0x55060B18)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stabilizerDecision.statusReason,
                        fontSize = 11.sp,
                        color = if (stabilizerDecision.isHeldLocked) Color(0xFFF59E0B) else TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Required Consecutive Frames:",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 7).forEach { count ->
                                val selected = stabilizerConfig.requiredConsecutiveFrames == count
                                Box(
                                    modifier = Modifier
                                        .glassSurface(
                                            cornerRadius = 8.dp,
                                            fillColor = if (selected) NeonCyan.copy(alpha = 0.25f) else Color(0x66060B18),
                                            borderAccent = if (selected) NeonCyan else Color.White.copy(alpha = 0.16f)
                                        )
                                        .clickable { onUpdateConsecutiveFrames(count) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) NeonCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Complete Gesture State Machine + Always-Visible Manual Cancel Fallback
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_state_banner"),
                cornerRadius = 18.dp,
                borderAccent = when (sessionState) {
                    GestureSessionState.COMPLETED -> EmeraldSuccess
                    GestureSessionState.CANCELLED, GestureSessionState.FAILED -> RoseError
                    else -> NeonCyan.copy(alpha = 0.55f)
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when (sessionState) {
                                            GestureSessionState.COMPLETED, GestureSessionState.CONNECTED -> EmeraldSuccess
                                            GestureSessionState.CANCELLED, GestureSessionState.FAILED -> RoseError
                                            GestureSessionState.IDLE -> TextSecondary
                                            else -> NeonCyan
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "STATE: ${sessionState.name}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Always-visible manual Cancel (STOP) backup
                            OutlinedButton(
                                onClick = onManualCancel,
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("manual_cancel_workflow_button"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Cancel Workflow",
                                    tint = RoseError,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", fontSize = 10.sp, color = RoseError, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onResetSession,
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("reset_session_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Reset", fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = sessionState.statusLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pipeline stage chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val stages = listOf(
                            "LIKE" to (sessionState != GestureSessionState.IDLE),
                            "FIST (${stagedItems.size})" to (isContentGrabbed || sessionState.ordinal >= GestureSessionState.CONTENT_HELD.ordinal && sessionState.isActiveSession),
                            "POINT" to (selectedDevice != null || sessionState.ordinal >= GestureSessionState.TARGET_SELECTED.ordinal && sessionState.isActiveSession),
                            "PAIR" to (connectedDevice != null || sessionState == GestureSessionState.CONNECTED),
                            "THREE_GUN" to (sessionState == GestureSessionState.TRANSFER_OFFERED ||
                                sessionState == GestureSessionState.WAITING_FOR_RECEIVER ||
                                sessionState == GestureSessionState.TRANSFERRING ||
                                sessionState == GestureSessionState.VERIFYING ||
                                sessionState == GestureSessionState.COMPLETED)
                        )
                        stages.forEach { (label, active) ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (active) NeonCyan.copy(alpha = 0.18f) else Color(0x66060B18),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) NeonCyan else Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) NeonCyan else TextSecondary
                                )
                            }
                        }
                    }

                    // Staged Content Grab Confirmation & Fallback
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                cornerRadius = 10.dp,
                                fillColor = if (isContentGrabbed && stagedItems.isNotEmpty()) EmeraldSuccess.copy(alpha = 0.14f) else Color(0x66060B18),
                                borderAccent = if (isContentGrabbed && stagedItems.isNotEmpty()) EmeraldSuccess else Color.White.copy(alpha = 0.14f)
                            )
                            .clickable { onNavigate(ScreenRoute.CONTENT_SELECTION) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isContentGrabbed) Icons.Default.Lock else Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = if (stagedItems.isEmpty()) Color(0xFFF59E0B) else if (isContentGrabbed) EmeraldSuccess else NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    stagedItems.isEmpty() -> "No content staged — tap to select files/text/URL"
                                    isContentGrabbed -> "Content Grabbed: ${stagedItems.size} item(s) held (${stagedItems.first().name})"
                                    else -> "${stagedItems.size} item(s) staged — show ✊ FIST to grab"
                                },
                                fontSize = 11.sp,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "Edit",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    // Manual fallback Send button when connected & staged
                    if (connectedDevice != null && stagedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onExecuteManualTransfer(connectedDevice) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("manual_send_fallback_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual Send to ${connectedDevice.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Inline Pairing Handshake Prompt (when Nearby requires 6-digit confirmation)
        if (pendingSession != null && pendingSession.status == SessionStatus.INITIATED) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gesture_inline_handshake_card"),
                    cornerRadius = 16.dp,
                    fillColor = Color(0x2800E5FF),
                    borderAccent = NeonCyan,
                    borderWidth = 1.5.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Nearby Pairing Code (${pendingSession.endpointName}): ${pendingSession.authDigits}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAcceptConnection(pendingSession.endpointId) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Accept Code", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onRejectConnection(pendingSession.endpointId) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                border = BorderStroke(1.dp, RoseError),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reject", fontSize = 11.sp, color = RoseError)
                            }
                        }
                    }
                }
            }
        }

        // Discovered Peers Selector Card (POINT cycles through these real endpoints)
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gesture_discovered_peers_card"),
                cornerRadius = 16.dp,
                fillColor = GlassFillElevated,
                borderAccent = ElectricViolet.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Discovered Peers (${discoveredDevices.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "👉 POINT cycles peers using real endpoint IDs",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onStartDiscovery,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Scan", fontSize = 10.sp, color = NeonCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (discoveredDevices.isEmpty()) {
                        Text(
                            text = "No peers discovered yet. Start Scan on this device and Receive Mode on your Redmi/Infinix peer.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    } else {
                        discoveredDevices.forEach { dev ->
                            val isSelected = selectedDevice?.endpointId == dev.endpointId
                            val isConnected = connectedDevice?.endpointId == dev.endpointId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .glassSurface(
                                        cornerRadius = 10.dp,
                                        fillColor = when {
                                            isConnected -> EmeraldSuccess.copy(alpha = 0.14f)
                                            isSelected -> NeonCyan.copy(alpha = 0.14f)
                                            else -> Color(0x66060B18)
                                        },
                                        borderAccent = when {
                                            isConnected -> EmeraldSuccess
                                            isSelected -> NeonCyan
                                            else -> Color.White.copy(alpha = 0.14f)
                                        }
                                    )
                                    .clickable { onSelectDevice(dev) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("gesture_peer_${dev.endpointId}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (isConnected) EmeraldSuccess else if (isSelected) NeonCyan else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = dev.name + if (isSelected) " ★" else "",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "ID: ${dev.endpointId} · ${dev.connectionState.name}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isConnected) EmeraldSuccess else TextSecondary
                                        )
                                    }
                                }

                                if (!isConnected) {
                                    Button(
                                        onClick = {
                                            onSelectDevice(dev)
                                            onRequestConnection(dev)
                                        },
                                        enabled = dev.connectionState != DeviceConnectionState.CONNECTING,
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (dev.connectionState == DeviceConnectionState.CONNECTING) "Pairing..." else "Connect",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "CONNECTED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = EmeraldSuccess
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // All 6 Class Probabilities + Confidence Threshold Card (Phase 7 Debug Dashboard Retained)
        item {
            val classes = onnxDiagnostics.classOrder.ifEmpty { HaGridV2OnnxClassifier.FALLBACK_CLASS_ORDER }
            val probs = liveOnnxPrediction.classProbabilities

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onnx_probabilities_card"),
                cornerRadius = 18.dp,
                fillColor = GlassFillElevated,
                borderAccent = if (liveOnnxPrediction.isAccepted) {
                    EmeraldSuccess.copy(alpha = 0.65f)
                } else {
                    ElectricViolet.copy(alpha = 0.45f)
                }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "6-Class ONNX Probability Output",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (liveOnnxPrediction.isHandDetected) {
                                    "Top: ${liveOnnxPrediction.predictedClass} (${(liveOnnxPrediction.confidence * 100).toInt()}%) · " +
                                        if (liveOnnxPrediction.isAccepted) "ACCEPTED" else "FILTERED -> OTHER"
                                } else {
                                    "Show a hand in front of the camera for live probabilities"
                                },
                                fontSize = 11.sp,
                                color = if (liveOnnxPrediction.isAccepted) EmeraldSuccess else TextSecondary
                            )
                        }

                        Text(
                            text = "Thresh ${String.format(Locale.US, "%.2f", confidenceThreshold)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    classes.forEachIndexed { idx, className ->
                        val prob = probs.getOrElse(idx) { 0f }.coerceIn(0f, 1f)
                        val isTop = idx == liveOnnxPrediction.predictedClassIndex
                        val barColor = when {
                            isTop && className != "OTHER" && prob >= confidenceThreshold -> EmeraldSuccess
                            isTop && className != "OTHER" -> Color(0xFFF59E0B)
                            className == "OTHER" -> TextSecondary
                            else -> NeonCyan.copy(alpha = 0.7f)
                        }

                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val actionHint = onnxDiagnostics.actions[className]?.takeIf { it.isNotBlank() }
                                Text(
                                    text = if (actionHint != null) "$idx · $className ($actionHint)" else "$idx · $className",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isTop) TextPrimary else TextSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "%5.1f%%", prob * 100f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isTop) barColor else TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { prob },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = barColor,
                                trackColor = Color(0x55060B18)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Confidence Threshold",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${(confidenceThreshold * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = NeonCyan
                        )
                    }
                    Slider(
                        value = confidenceThreshold,
                        onValueChange = { onUpdateConfidenceThreshold(it) },
                        valueRange = 0.50f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.testTag("confidence_threshold_slider")
                    )
                }
            }
        }

        // Asset Status & Latency Benchmark Card
        item {
            val missing = onnxDiagnostics.missingAssets
            val hasAssetError = missing.isNotEmpty() || !onnxDiagnostics.isLoaded || handTrackingState.status == HandTrackerStatus.MODEL_MISSING

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onnx_diagnostics_card"),
                cornerRadius = 18.dp,
                fillColor = if (hasAssetError) Color(0x28EF4444) else GlassFillElevated,
                borderAccent = if (hasAssetError) RoseError.copy(alpha = 0.7f) else NeonCyan.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (hasAssetError) Icons.Default.Warning else Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (hasAssetError) RoseError else NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ONNX Runtime & Asset Diagnostics",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "Load ${latencyStats.modelLoadTimeMs}ms",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (onnxDiagnostics.isLoaded) EmeraldSuccess else RoseError
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HaGridV2OnnxClassifier.DEFAULT_REQUIRED_ASSETS.forEach { assetName ->
                            val isMissing = missing.contains(assetName) ||
                                (assetName == HaGridV2OnnxClassifier.HAND_LANDMARKER_ASSET &&
                                    handTrackingState.status == HandTrackerStatus.MODEL_MISSING)
                            Row(
                                modifier = Modifier
                                    .glassSurface(
                                        cornerRadius = 8.dp,
                                        fillColor = if (isMissing) Color(0x44EF4444) else Color(0x4410B981),
                                        borderAccent = if (isMissing) RoseError else EmeraldSuccess.copy(alpha = 0.7f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMissing) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isMissing) RoseError else EmeraldSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = assetName,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = classifierInfo,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (hasAssetError) RoseError else TextSecondary,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricMiniBox(
                            label = "ONNX AVG",
                            value = if (latencyStats.sampleCount > 0) String.format(Locale.US, "%.2fms", latencyStats.avgOnnxMs) else "--",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            label = "MEDIAN",
                            value = if (latencyStats.sampleCount > 0) String.format(Locale.US, "%.2fms", latencyStats.medianOnnxMs) else "--",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            label = "P95",
                            value = if (latencyStats.sampleCount > 0) String.format(Locale.US, "%.2fms", latencyStats.p95OnnxMs) else "--",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            label = "HAND",
                            value = handTrackingState.handedness,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Command Execution & Live Prediction Logs
        item {
            val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prediction_history_card"),
                cornerRadius = 18.dp,
                fillColor = GlassFillElevated,
                borderAccent = ElectricViolet.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Command & Prediction Logs",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )

                        OutlinedButton(
                            onClick = onClearPredictionHistory,
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("clear_prediction_history_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 10.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (commandLogs.isNotEmpty()) {
                        commandLogs.take(5).forEach { logLine ->
                            Text(
                                text = logLine,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NeonCyan,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (predictionHistory.isEmpty()) {
                        Text(
                            text = "No hand predictions recorded yet.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    } else {
                        predictionHistory.take(6).forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .glassSurface(
                                        cornerRadius = 8.dp,
                                        fillColor = Color(0x66060B18),
                                        borderAccent = if (entry.isAccepted) EmeraldSuccess.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatter.format(Date(entry.timestampMs))} · ${entry.predictedClass} (${entry.handedness})",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (entry.isAccepted) EmeraldSuccess else TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(entry.confidence * 100).toInt()}% · ${entry.onnxLatencyUs}µs",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (entry.isAccepted) NeonCyan else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "5-Command Gesture Mapping (Manual Test Hooks)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(intendedCommands) { command ->
            val isLiveActive = liveOnnxPrediction.isAccepted && liveOnnxPrediction.gesture == command
            val isDiagActive = currentGesture.gesture == command
            val isActive = isLiveActive || isDiagActive

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gesture_command_${command.name}"),
                cornerRadius = 16.dp,
                fillColor = if (isActive) NeonCyan.copy(alpha = 0.14f) else GlassFillElevated,
                borderAccent = if (isActive) NeonCyan else Color.White.copy(alpha = 0.18f),
                borderWidth = if (isActive) 1.5.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .glassSurface(
                                    cornerRadius = 12.dp,
                                    fillColor = Color(0x66060B18),
                                    borderAccent = Color.White.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = command.symbol,
                                fontSize = 24.sp
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = command.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) NeonCyan else TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "· ${command.actionName}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (command == GestureType.STOP) RoseError else NeonCyan,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = command.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = { onSimulateGesture(command) },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("trigger_${command.name.lowercase()}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) NeonCyan else Color(0x66060B18),
                            contentColor = if (isActive) DarkBackground else NeonCyan
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) NeonCyan else Color.White.copy(alpha = 0.22f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (isActive) "Active" else "Trigger",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassSurface(
                cornerRadius = 10.dp,
                fillColor = Color(0x66060B18),
                borderAccent = Color.White.copy(alpha = 0.14f)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = NeonCyan,
            maxLines = 1
        )
    }
}
