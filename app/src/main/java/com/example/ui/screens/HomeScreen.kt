package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.model.ContentItem
import com.example.model.DeviceItem
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.ui.ScreenRoute
import com.example.ui.components.GestoShareBrandMark
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface

@Composable
fun HomeScreen(
    deviceName: String,
    stagedItems: List<ContentItem>,
    discoveredDevices: List<DeviceItem>,
    connectedDevices: List<DeviceItem> = emptyList(),
    errorCount: Int = 0,
    isAdvertising: Boolean,
    recentTransfers: List<TransferRecord>,
    onNavigate: (ScreenRoute) -> Unit,
    onToggleAdvertising: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Branding Glass Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_brand_card"),
                cornerRadius = 24.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = palette.accentPrimary.copy(alpha = 0.55f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    palette.accentPrimary.copy(alpha = 0.12f),
                                    palette.accentSecondary.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            GestoShareBrandMark(
                                size = 54.dp,
                                cornerRadius = 15.dp
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GestoShare",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "Gesture-Powered P2P Transfer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.accentPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            GlassStatusBadge(
                                label = "v${BuildConfig.VERSION_NAME}",
                                color = palette.accentSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Share photos, videos, PDFs, notes, and links directly across nearby Android devices with MediaPipe gesture control and SHA-256 verification.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(
                                    cornerRadius = 12.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.success.copy(alpha = 0.45f)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(palette.success, CircleShape)
                                )
                                Text(
                                    text = "Device:",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary
                                )
                                Text(
                                    text = deviceName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = "Offline Ready",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentPrimary
                            )
                        }
                    }
                }
            }
        }

        // Send & Receive Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onNavigate(ScreenRoute.CONTENT_SELECTION) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("send_content_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentPrimary,
                        contentColor = palette.onAccentPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Send Content",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Import & Share",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Button(
                    onClick = onToggleAdvertising,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("receive_content_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdvertising) palette.accentSecondary else palette.glassFillElevated,
                        contentColor = if (isAdvertising) Color.White else palette.textPrimary
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isAdvertising) palette.accentSecondary else palette.glassBorder
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = if (isAdvertising) Color.White else palette.accentPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = if (isAdvertising) "Receiving..." else "Receive",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isAdvertising) "Beacon Active" else "Open Beacon",
                                fontSize = 11.sp,
                                color = if (isAdvertising) Color.White.copy(alpha = 0.85f) else palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Diagnostics shortcut when errors have occurred
        if (errorCount > 0) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(ScreenRoute.DIAGNOSTICS) }
                        .testTag("home_diagnostics_alert_card"),
                    cornerRadius = 16.dp,
                    fillColor = palette.error.copy(alpha = 0.12f),
                    borderAccent = palette.error.copy(alpha = 0.65f)
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Diagnostics Alert",
                                tint = palette.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "$errorCount Diagnostic Report(s) Recorded",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "Tap to inspect stack traces or export report",
                                    fontSize = 11.sp,
                                    color = palette.error
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Diagnostics",
                            tint = palette.error
                        )
                    }
                }
            }
        }

        // Staged Content Alert Glass Banner
        if (stagedItems.isNotEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(ScreenRoute.CONTENT_SELECTION) }
                        .testTag("staged_content_alert"),
                    cornerRadius = 16.dp,
                    fillColor = palette.accentPrimary.copy(alpha = 0.14f),
                    borderAccent = palette.accentPrimary.copy(alpha = 0.7f)
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(palette.accentPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = palette.onAccentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "${stagedItems.size} Item(s) Staged for Transfer",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tap to review SHA-256 & send to peer",
                                    fontSize = 12.sp,
                                    color = palette.accentPrimary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Review",
                            tint = palette.accentPrimary
                        )
                    }
                }
            }
        }

        // Nearby Devices Glass Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ScreenRoute.NEARBY_DEVICES) }
                    .testTag("nearby_status_card"),
                cornerRadius = 20.dp,
                fillColor = palette.glassFill,
                borderAccent = if (connectedDevices.isNotEmpty()) palette.success.copy(alpha = 0.5f) else palette.accentPrimary.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .glassSurface(
                                        cornerRadius = 19.dp,
                                        fillColor = palette.glassFillInset,
                                        borderAccent = palette.accentPrimary.copy(alpha = 0.55f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = if (connectedDevices.isNotEmpty()) palette.success else palette.accentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Nearby Cluster Status",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "${connectedDevices.size} connected · ${discoveredDevices.size} discovered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (connectedDevices.isNotEmpty()) palette.success else palette.textSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View Devices",
                            tint = palette.textSecondary
                        )
                    }

                    val previewList = (connectedDevices + discoveredDevices).distinctBy { it.endpointId }
                    if (previewList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            previewList.take(3).forEach { dev ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .glassSurface(
                                            cornerRadius = 12.dp,
                                            fillColor = palette.glassFillInset,
                                            borderAccent = if (dev.isConnected) palette.success else palette.accentPrimary.copy(alpha = 0.3f)
                                        )
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = dev.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.textPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (dev.isConnected) "CONNECTED" else "ID: ${dev.endpointId}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (dev.isConnected) palette.success else palette.accentPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gesture Control Glass Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ScreenRoute.GESTURE_CONTROL) }
                    .testTag("gesture_sandbox_card"),
                cornerRadius = 20.dp,
                fillColor = palette.glassFill,
                borderAccent = palette.accentSecondary.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .glassSurface(
                                    cornerRadius = 22.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentSecondary,
                                    borderWidth = 1.5.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = null,
                                tint = palette.accentSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "MediaPipe Gesture Radar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "LIKE · FIST · POINT · THREE_GUN · STOP",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = palette.accentSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Gesture Screen",
                        tint = palette.accentSecondary
                    )
                }
            }
        }

        // Recent Transfers Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Transfers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )

                Text(
                    text = "View All (${recentTransfers.size})",
                    fontSize = 12.sp,
                    color = palette.accentPrimary,
                    modifier = Modifier
                        .clickable { onNavigate(ScreenRoute.HISTORY) }
                        .padding(4.dp)
                )
            }
        }

        if (recentTransfers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(cornerRadius = 16.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No transfers yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import files and share between Redmi / Infinix devices",
                            fontSize = 12.sp,
                            color = palette.textMuted
                        )
                    }
                }
            }
        } else {
            items(recentTransfers.take(3)) { record ->
                val isOk = record.status == TransferStatus.SUCCESS
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(ScreenRoute.HISTORY) },
                    cornerRadius = 14.dp,
                    borderAccent = (if (isOk) palette.success else palette.error).copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isOk) palette.success else palette.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = record.fileName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${record.fileSizeFormatted} · ${record.receiverName}",
                                    fontSize = 11.sp,
                                    color = palette.textSecondary
                                )
                            }
                        }

                        Text(
                            text = record.status.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOk) palette.success else palette.error
                        )
                    }
                }
            }
        }
    }
}
