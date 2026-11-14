package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveTransfer
import com.example.model.TransferStatus
import com.example.ui.ScreenRoute
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
fun TransferScreen(
    activeTransfer: ActiveTransfer?,
    onCancelTransfer: () -> Unit,
    onRetryTransfer: () -> Unit,
    onOpenReceivedFile: (String, String?) -> Unit,
    onNavigate: (ScreenRoute) -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier
) {
    if (activeTransfer == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Active Transfer Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select files and a connected Nearby peer to begin transferring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onNavigate(ScreenRoute.HOME) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Return Home", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val statusColor = when (activeTransfer.status) {
        TransferStatus.SUCCESS -> EmeraldSuccess
        TransferStatus.FAILED, TransferStatus.CANCELLED -> RoseError
        TransferStatus.VERIFYING, TransferStatus.AWAITING_ACCEPTANCE -> ElectricViolet
        else -> NeonCyan
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("transfer_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Peer Connection Bridge Glass Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("peer_bridge_card"),
                cornerRadius = 20.dp,
                borderAccent = NeonCyan.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .glassSurface(cornerRadius = 24.dp, fillColor = Color(0x66060B18), borderAccent = NeonCyan, borderWidth = 1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Sender", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = activeTransfer.senderName.take(14),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "P2P Beam",
                            tint = if (activeTransfer.status == TransferStatus.TRANSFERRING) NeonCyan else TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f MB/s", activeTransfer.speedMbps),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .glassSurface(cornerRadius = 24.dp, fillColor = Color(0x66060B18), borderAccent = ElectricViolet, borderWidth = 1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Receiver", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = activeTransfer.receiverName.take(14),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Main Progress Glass Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transfer_progress_card"),
                cornerRadius = 24.dp,
                fillColor = GlassFillElevated,
                borderAccent = statusColor.copy(alpha = 0.7f),
                borderWidth = 1.5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .glassSurface(
                                cornerRadius = 36.dp,
                                fillColor = Color(0x88060B18),
                                borderAccent = statusColor,
                                borderWidth = 2.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (activeTransfer.status) {
                            TransferStatus.SUCCESS -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            TransferStatus.FAILED, TransferStatus.CANCELLED -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Failed",
                                    tint = RoseError,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            TransferStatus.AWAITING_ACCEPTANCE, TransferStatus.VERIFYING, TransferStatus.PREPARING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(50.dp),
                                    color = statusColor,
                                    trackColor = Color(0xFF1E293B),
                                    strokeWidth = 4.dp
                                )
                            }
                            TransferStatus.TRANSFERRING -> {
                                CircularProgressIndicator(
                                    progress = { activeTransfer.progress },
                                    modifier = Modifier.size(54.dp),
                                    color = NeonCyan,
                                    trackColor = Color(0xFF1E293B),
                                    strokeWidth = 4.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = activeTransfer.status.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )

                    if (!activeTransfer.statusDetail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeTransfer.statusDetail,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${(activeTransfer.progress * 100).toInt()}% Completed",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { activeTransfer.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = statusColor,
                        trackColor = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Transferred: ${formatSize(activeTransfer.bytesTransferred)}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Total: ${formatSize(activeTransfer.totalBytes)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Payload Items & Cryptographic SHA-256 Verification Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transfer_files_summary"),
                cornerRadius = 18.dp,
                fillColor = GlassFillElevated,
                borderAccent = if (activeTransfer.isIntegrityVerified) EmeraldSuccess.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Payload Items (${activeTransfer.items.size})",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    activeTransfer.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.name,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatSize(item.sizeBytes),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                cornerRadius = 10.dp,
                                fillColor = Color(0x66060B18),
                                borderAccent = if (activeTransfer.isIntegrityVerified) EmeraldSuccess.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Integrity",
                            tint = if (activeTransfer.isIntegrityVerified) EmeraldSuccess else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Column {
                            Text(
                                text = if (activeTransfer.isIntegrityVerified) "SHA-256 VERIFIED" else "SHA-256 CHECKSUM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTransfer.isIntegrityVerified) EmeraldSuccess else NeonCyan
                            )
                            Text(
                                text = activeTransfer.sha256Checksum.take(28) + if (activeTransfer.sha256Checksum.length > 28) "..." else "",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons according to state
        item {
            when (activeTransfer.status) {
                TransferStatus.TRANSFERRING,
                TransferStatus.PREPARING,
                TransferStatus.AWAITING_ACCEPTANCE,
                TransferStatus.VERIFYING -> {
                    Button(
                        onClick = onCancelTransfer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("cancel_transfer_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseError.copy(alpha = 0.2f),
                            contentColor = RoseError
                        ),
                        border = BorderStroke(1.dp, RoseError),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Transfer", fontWeight = FontWeight.Bold)
                    }
                }

                TransferStatus.SUCCESS -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!activeTransfer.savedUriString.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    onOpenReceivedFile(
                                        activeTransfer.savedUriString,
                                        activeTransfer.savedMimeType
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("open_received_file_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldSuccess,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Received File", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        Button(
                            onClick = { onNavigate(ScreenRoute.CONTENT_SELECTION) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("share_another_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Share More Content", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onNavigate(ScreenRoute.HISTORY) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("view_history_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View in Transfer History", color = TextPrimary)
                        }
                    }
                }

                TransferStatus.FAILED, TransferStatus.CANCELLED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRetryTransfer,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("retry_transfer_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onNavigate(ScreenRoute.HOME) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("return_home_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Text("Return Home", color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
