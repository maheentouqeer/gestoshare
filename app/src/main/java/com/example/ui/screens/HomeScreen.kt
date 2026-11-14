package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ContentItem
import com.example.model.DeviceItem
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.ui.ScreenRoute
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassFill
import com.example.ui.theme.GlassFillElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.glassSurface

@Composable
fun HomeScreen(
    deviceName: String,
    stagedItems: List<ContentItem>,
    discoveredDevices: List<DeviceItem>,
    isAdvertising: Boolean,
    recentTransfers: List<TransferRecord>,
    onNavigate: (ScreenRoute) -> Unit,
    onToggleAdvertising: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                fillColor = GlassFillElevated,
                borderAccent = NeonCyan.copy(alpha = 0.55f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    NeonCyan.copy(alpha = 0.12f),
                                    ElectricViolet.copy(alpha = 0.10f),
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
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.airshare_icon_1794479065389),
                                    contentDescription = "GestoShare Logo",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GestoShare",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Gesture-Powered P2P Transfer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .glassSurface(cornerRadius = 12.dp, fillColor = Color(0x66060B18), borderAccent = Color.White.copy(alpha = 0.25f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "v1.0",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Share photos, videos, PDFs, notes, and links directly between nearby Android devices with SHA-256 verification and MediaPipe hand tracking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(
                                    cornerRadius = 12.dp,
                                    fillColor = Color(0x66060913),
                                    borderAccent = EmeraldSuccess.copy(alpha = 0.4f)
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
                                        .background(EmeraldSuccess, CircleShape)
                                )
                                Text(
                                    text = "Device:",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = deviceName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = "Offline Ready",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
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
                        containerColor = NeonCyan,
                        contentColor = DarkBackground
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
                        containerColor = if (isAdvertising) ElectricViolet.copy(alpha = 0.85f) else GlassFillElevated,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isAdvertising) ElectricViolet else Color.White.copy(alpha = 0.22f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = if (isAdvertising) Color.White else NeonCyan,
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
                                color = if (isAdvertising) Color(0xFFE0E0FF) else TextSecondary
                            )
                        }
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
                    fillColor = NeonCyan.copy(alpha = 0.14f),
                    borderAccent = NeonCyan.copy(alpha = 0.7f)
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
                                    .background(NeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = DarkBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "${stagedItems.size} Item(s) Staged for Transfer",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tap to review SHA-256 & send to peer",
                                    fontSize = 12.sp,
                                    color = NeonCyan
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Review",
                            tint = NeonCyan
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
                fillColor = GlassFill,
                borderAccent = NeonCyan.copy(alpha = 0.35f)
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
                                    .glassSurface(cornerRadius = 19.dp, fillColor = Color(0x66060B18), borderAccent = NeonCyan.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Nearby Peers Status",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${discoveredDevices.size} peer device(s) discovered nearby",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View Devices",
                            tint = TextSecondary
                        )
                    }

                    if (discoveredDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            discoveredDevices.take(3).forEach { dev ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .glassSurface(
                                            cornerRadius = 12.dp,
                                            fillColor = Color(0x66060B18),
                                            borderAccent = NeonCyan.copy(alpha = 0.3f)
                                        )
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = dev.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "ID: ${dev.endpointId}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = NeonCyan
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
                fillColor = GlassFill,
                borderAccent = ElectricViolet.copy(alpha = 0.45f)
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
                                    fillColor = Color(0x66060B18),
                                    borderAccent = ElectricViolet,
                                    borderWidth = 1.5.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "MediaPipe Gesture Radar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "LIKE · FIST · POINT · THREE_GUN · STOP",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = ElectricViolet
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Gesture Screen",
                        tint = ElectricViolet
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
                    color = TextPrimary
                )

                Text(
                    text = "View All (${recentTransfers.size})",
                    fontSize = 12.sp,
                    color = NeonCyan,
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
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import files and share between Redmi / Infinix devices",
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(recentTransfers.take(3)) { record ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(ScreenRoute.HISTORY) },
                    cornerRadius = 14.dp,
                    borderAccent = if (record.status == TransferStatus.SUCCESS) EmeraldSuccess.copy(alpha = 0.4f) else RoseError.copy(alpha = 0.4f)
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
                                imageVector = if (record.status == TransferStatus.SUCCESS) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (record.status == TransferStatus.SUCCESS) EmeraldSuccess else RoseError,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = record.fileName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${record.fileSizeFormatted} · ${record.receiverName}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Text(
                            text = record.status.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (record.status == TransferStatus.SUCCESS) EmeraldSuccess else RoseError
                        )
                    }
                }
            }
        }
    }
}
