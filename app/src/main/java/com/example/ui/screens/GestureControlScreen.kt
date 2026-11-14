package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.HandTrackerStatus
import com.example.manager.HandTrackingState
import com.example.manager.MediaPipeHandTracker
import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
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

@Composable
fun GestureControlScreen(
    currentGesture: GestureResult,
    sessionState: GestureSessionState,
    isTrackingHand: Boolean,
    handTrackingState: HandTrackingState,
    handTracker: MediaPipeHandTracker,
    testWithoutTransfer: Boolean,
    onToggleTestWithoutTransfer: (Boolean) -> Unit,
    classifierInfo: String,
    onSimulateGesture: (GestureType) -> Unit,
    onResetSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intendedCommands = listOf(
        GestureType.LIKE,
        GestureType.FIST,
        GestureType.POINT,
        GestureType.THREE_GUN,
        GestureType.STOP
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("gesture_control_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live CameraX Preview + MediaPipe 21-Landmark Skeleton Overlay
        item {
            CameraPreviewPlaceholder(
                currentGesture = currentGesture,
                handTrackingState = handTrackingState,
                handTracker = handTracker,
                isTrackingHandFallback = isTrackingHand,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
            )
        }

        // MediaPipe Live Pipeline & Asset Diagnostics Card
        item {
            val isMissingModel = handTrackingState.status == HandTrackerStatus.MODEL_MISSING
            val isCameraErr = handTrackingState.status == HandTrackerStatus.CAMERA_ERROR

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mediapipe_diagnostics_card"),
                cornerRadius = 18.dp,
                fillColor = if (isMissingModel || isCameraErr) Color(0x28F59E0B) else GlassFillElevated,
                borderAccent = when {
                    isCameraErr -> RoseError.copy(alpha = 0.7f)
                    isMissingModel -> Color(0xFFF59E0B).copy(alpha = 0.7f)
                    handTrackingState.isHandDetected -> EmeraldSuccess.copy(alpha = 0.65f)
                    else -> ElectricViolet.copy(alpha = 0.45f)
                }
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
                                imageVector = if (isMissingModel || isCameraErr) Icons.Default.Warning else Icons.Default.Science,
                                contentDescription = null,
                                tint = when {
                                    isCameraErr -> RoseError
                                    isMissingModel -> Color(0xFFF59E0B)
                                    else -> NeonCyan
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "MediaPipe Hand Tracker (Phase 6)",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "${handTrackingState.fps} FPS · ${handTrackingState.inferenceTimeMs}ms",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = handTrackingState.statusMessage,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    if (handTrackingState.normalizedFeatureVector != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val previewSample = handTrackingState.normalizedFeatureVector.take(6)
                            .joinToString(", ") { String.format(java.util.Locale.US, "%.2f", it) }
                        Text(
                            text = "[1, 63] vector ready: [$previewSample, ...]",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldSuccess
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Safe "Test Without Transferring" Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                cornerRadius = 12.dp,
                                fillColor = Color(0x66060B18),
                                borderAccent = Color.White.copy(alpha = 0.14f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Test Without Transferring",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (testWithoutTransfer) {
                                    "Safe Mode: THREE_GUN won't launch real file sends"
                                } else {
                                    "Armed: THREE_GUN will trigger real P2P transfer"
                                },
                                fontSize = 10.sp,
                                color = if (testWithoutTransfer) NeonCyan else Color(0xFFF59E0B)
                            )
                        }
                        Switch(
                            checked = testWithoutTransfer,
                            onCheckedChange = onToggleTestWithoutTransfer,
                            modifier = Modifier.testTag("test_without_transfer_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = NeonCyan
                            )
                        )
                    }
                }
            }
        }

        // Gesture Session State Machine Banner
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_state_banner"),
                cornerRadius = 18.dp,
                borderAccent = NeonCyan.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
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
                                            GestureSessionState.TRANSFERRING -> EmeraldSuccess
                                            GestureSessionState.CANCELLED -> RoseError
                                            GestureSessionState.IDLE -> TextSecondary
                                            else -> NeonCyan
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "SESSION STATE",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
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
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = sessionState.statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val steps = listOf(
                            "1. LIKE" to (sessionState != GestureSessionState.IDLE),
                            "2. FIST" to (sessionState == GestureSessionState.CONTENT_HELD || sessionState == GestureSessionState.TARGET_LOCKED || sessionState == GestureSessionState.TRANSFERRING),
                            "3. POINT" to (sessionState == GestureSessionState.TARGET_LOCKED || sessionState == GestureSessionState.TRANSFERRING),
                            "4. SEND" to (sessionState == GestureSessionState.TRANSFERRING)
                        )

                        steps.forEach { (label, active) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (active) NeonCyan.copy(alpha = 0.2f) else Color(0x66060B18),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) NeonCyan else Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) NeonCyan else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // HaGRIDv2 Phase 7 Info Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_notice_card"),
                cornerRadius = 16.dp,
                fillColor = GlassFillElevated,
                borderAccent = ElectricViolet.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "HaGRIDv2 ONNX Classifier (Phase 7 Pending)",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = classifierInfo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "5-Command Gesture Vocabulary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        items(intendedCommands) { command ->
            val isActive = currentGesture.gesture == command

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
                                .glassSurface(cornerRadius = 12.dp, fillColor = Color(0x66060B18), borderAccent = Color.White.copy(alpha = 0.2f)),
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
                            text = if (isActive) "Active" else "Test",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
