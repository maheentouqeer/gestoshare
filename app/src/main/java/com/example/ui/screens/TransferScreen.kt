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
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveTransfer
import com.example.model.TransferDirection
import com.example.model.TransferPipelineStage
import com.example.model.TransferStatus
import com.example.ui.ScreenRoute
import com.example.ui.components.GlassProgressIndicator
import com.example.ui.components.GlassStatusBadge
import com.example.ui.components.GlassTransferStageCard
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import java.util.Locale

@Composable
fun TransferScreen(
    activeTransfer: ActiveTransfer?,
    endpointTransfers: Map<String, ActiveTransfer> = emptyMap(),
    onCancelTransfer: () -> Unit,
    onCancelEndpointTransfer: (String) -> Unit = {},
    onRetryTransfer: () -> Unit,
    onOpenReceivedFile: (String, String?) -> Unit,
    onNavigate: (ScreenRoute) -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors

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
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select files and a connected Nearby peer to begin transferring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onNavigate(ScreenRoute.HOME) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentPrimary,
                            contentColor = palette.onAccentPrimary
                        ),
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
        TransferStatus.SUCCESS -> palette.success
        TransferStatus.FAILED, TransferStatus.CANCELLED -> palette.error
        TransferStatus.VERIFYING, TransferStatus.AWAITING_ACCEPTANCE -> palette.accentSecondary
        else -> palette.accentPrimary
    }

    val isOngoing = activeTransfer.status == TransferStatus.PREPARING ||
        activeTransfer.status == TransferStatus.AWAITING_ACCEPTANCE ||
        activeTransfer.status == TransferStatus.TRANSFERRING ||
        activeTransfer.status == TransferStatus.VERIFYING

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("transfer_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Peer Connection Bridge Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("peer_bridge_card"),
                cornerRadius = 20.dp,
                borderAccent = palette.accentPrimary.copy(alpha = 0.4f)
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
                                .glassSurface(
                                    cornerRadius = 24.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentPrimary,
                                    borderWidth = 1.5.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Sender", fontSize = 10.sp, color = palette.textSecondary)
                        Text(
                            text = activeTransfer.senderName.take(14),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "P2P Beam",
                            tint = if (activeTransfer.status == TransferStatus.TRANSFERRING) palette.accentPrimary else palette.textSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f MB/s", activeTransfer.speedMbps),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .glassSurface(
                                    cornerRadius = 24.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentSecondary,
                                    borderWidth = 1.5.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = palette.accentSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Receiver", fontSize = 10.sp, color = palette.textSecondary)
                        Text(
                            text = activeTransfer.receiverName.take(14),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
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
                fillColor = palette.glassFillElevated,
                borderAccent = statusColor.copy(alpha = 0.7f),
                borderWidth = 1.5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .glassSurface(
                                cornerRadius = 34.dp,
                                fillColor = palette.glassFillInset,
                                borderAccent = statusColor,
                                borderWidth = 2.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (activeTransfer.status) {
                            TransferStatus.SUCCESS -> Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = palette.success,
                                modifier = Modifier.size(38.dp)
                            )
                            TransferStatus.FAILED, TransferStatus.CANCELLED -> Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Failed",
                                tint = palette.error,
                                modifier = Modifier.size(38.dp)
                            )
                            TransferStatus.AWAITING_ACCEPTANCE, TransferStatus.VERIFYING, TransferStatus.PREPARING ->
                                CircularProgressIndicator(
                                    modifier = Modifier.size(46.dp),
                                    color = statusColor,
                                    trackColor = palette.glassBorder,
                                    strokeWidth = 4.dp
                                )
                            TransferStatus.TRANSFERRING -> CircularProgressIndicator(
                                progress = { activeTransfer.progress },
                                modifier = Modifier.size(50.dp),
                                color = palette.accentPrimary,
                                trackColor = palette.glassBorder,
                                strokeWidth = 4.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = activeTransfer.status.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )

                    val detailText = activeTransfer.errorMessage ?: activeTransfer.statusDetail
                    if (!detailText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detailText,
                            fontSize = 12.sp,
                            color = if (activeTransfer.status == TransferStatus.FAILED) palette.error else palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (!activeTransfer.lastErrorCode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        GlassStatusBadge(label = activeTransfer.lastErrorCode, color = palette.error)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${(activeTransfer.progress * 100).toInt()}% · ${activeTransfer.pipelineStage.label}",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassProgressIndicator(
                        progress = activeTransfer.progress,
                        accentColor = statusColor
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Transferred: ${formatSize(activeTransfer.bytesTransferred)}",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        Text(
                            text = "Total: ${formatSize(activeTransfer.totalBytes)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                    }
                }
            }
        }

        // Granular Sub-Stage Verification Breakdown
        item {
            val stageOrder = activeTransfer.pipelineStage.stepOrder
            val isFailed = activeTransfer.status == TransferStatus.FAILED
            val isReceive = activeTransfer.direction == TransferDirection.RECEIVE

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transfer_pipeline_stages_card"),
                cornerRadius = 18.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = palette.accentPrimary.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pipeline Stage Telemetry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.textPrimary
                    )

                    GlassTransferStageCard(
                        title = "1. Nearby Connections Transport",
                        subtitle = if (activeTransfer.transportCompleted) "100% payload bytes delivered over Nearby" else activeTransfer.pipelineStage.label,
                        isCompleted = activeTransfer.transportCompleted || activeTransfer.status == TransferStatus.SUCCESS,
                        isActive = stageOrder in 6..8 && !activeTransfer.transportCompleted && !isFailed,
                        isFailed = isFailed && !activeTransfer.transportCompleted
                    )

                    if (isReceive) {
                        GlassTransferStageCard(
                            title = "2. Scoped Storage Copy (ContentResolver URI)",
                            subtitle = if (activeTransfer.fileCopyCompleted) "Copied safely to app-private verified directory" else "Reads payload.asFile().asUri() stream (no EACCES POSIX paths)",
                            isCompleted = activeTransfer.fileCopyCompleted || activeTransfer.status == TransferStatus.SUCCESS,
                            isActive = activeTransfer.pipelineStage == TransferPipelineStage.COPYING_TO_PRIVATE_STORAGE,
                            isFailed = isFailed && activeTransfer.transportCompleted && !activeTransfer.fileCopyCompleted
                        )
                    }

                    GlassTransferStageCard(
                        title = if (isReceive) "3. SHA-256 Cryptographic Verification" else "2. Receiver SHA-256 Confirmation",
                        subtitle = if (activeTransfer.isIntegrityVerified) {
                            "SHA-256 Matched (${activeTransfer.sha256Checksum.take(12)}...)"
                        } else {
                            "Awaiting streaming SHA-256 & byte-length check"
                        },
                        isCompleted = activeTransfer.isIntegrityVerified,
                        isActive = activeTransfer.pipelineStage == TransferPipelineStage.VERIFYING_INTEGRITY,
                        isFailed = isFailed && activeTransfer.transportCompleted && !activeTransfer.isIntegrityVerified
                    )

                    if (isReceive) {
                        val exportFailed = activeTransfer.mediaStoreExported == false
                        GlassTransferStageCard(
                            title = "4. Public Downloads/GestoShare Export",
                            subtitle = activeTransfer.mediaStoreExportNote
                                ?: if (activeTransfer.mediaStoreExported == true) "Exported to Downloads/GestoShare via MediaStore"
                                else "Queued after SHA-256 verification",
                            isCompleted = activeTransfer.mediaStoreExported == true,
                            isActive = activeTransfer.pipelineStage == TransferPipelineStage.EXPORTING_TO_DOWNLOADS,
                            isFailed = exportFailed
                        )
                    }
                }
            }
        }

        // Concurrent Multi-Recipient Progress Cards (when transferring to multiple endpoints)
        if (endpointTransfers.size > 1) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("multi_recipient_progress_card"),
                    cornerRadius = 18.dp,
                    borderAccent = palette.accentSecondary.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Per-Recipient Status (${endpointTransfers.size} peers)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = palette.textPrimary
                        )
                        endpointTransfers.forEach { (epId, tx) ->
                            val epOngoing = tx.status == TransferStatus.PREPARING ||
                                tx.status == TransferStatus.AWAITING_ACCEPTANCE ||
                                tx.status == TransferStatus.TRANSFERRING ||
                                tx.status == TransferStatus.VERIFYING
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassSurface(
                                        cornerRadius = 12.dp,
                                        fillColor = palette.glassFillInset,
                                        borderAccent = palette.glassBorder
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${tx.receiverName} ($epId)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "${tx.status.label} · ${(tx.progress * 100).toInt()}%",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = when (tx.status) {
                                            TransferStatus.SUCCESS -> palette.success
                                            TransferStatus.FAILED, TransferStatus.CANCELLED -> palette.error
                                            else -> palette.accentPrimary
                                        }
                                    )
                                }
                                if (epOngoing) {
                                    IconButton(
                                        onClick = { onCancelEndpointTransfer(epId) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("cancel_endpoint_$epId")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel for ${tx.receiverName}",
                                            tint = palette.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
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
                fillColor = palette.glassFillElevated,
                borderAccent = if (activeTransfer.isIntegrityVerified) palette.success.copy(alpha = 0.5f) else palette.glassBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Payload Items (${activeTransfer.items.size})",
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
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
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.name,
                                fontSize = 12.sp,
                                color = palette.textPrimary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatSize(item.sizeBytes),
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                cornerRadius = 10.dp,
                                fillColor = palette.glassFillInset,
                                borderAccent = if (activeTransfer.isIntegrityVerified) palette.success.copy(alpha = 0.6f) else palette.glassBorder
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Integrity",
                            tint = if (activeTransfer.isIntegrityVerified) palette.success else palette.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Column {
                            Text(
                                text = if (activeTransfer.isIntegrityVerified) "SHA-256 VERIFIED" else "SHA-256 CHECKSUM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTransfer.isIntegrityVerified) palette.success else palette.accentPrimary
                            )
                            Text(
                                text = activeTransfer.sha256Checksum.take(32) + if (activeTransfer.sha256Checksum.length > 32) "..." else "",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons according to state
        item {
            when {
                isOngoing -> {
                    Button(
                        onClick = onCancelTransfer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("cancel_transfer_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.error.copy(alpha = 0.16f),
                            contentColor = palette.error
                        ),
                        border = BorderStroke(1.dp, palette.error),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Transfer", fontWeight = FontWeight.Bold)
                    }
                }

                activeTransfer.status == TransferStatus.SUCCESS -> {
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
                                    containerColor = palette.success,
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
                                containerColor = palette.accentPrimary,
                                contentColor = palette.onAccentPrimary
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
                            border = BorderStroke(1.dp, palette.glassBorder)
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null, tint = palette.textSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View in Transfer History", color = palette.textPrimary)
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onNavigate(ScreenRoute.DIAGNOSTICS) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("transfer_open_diagnostics_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.error)
                        ) {
                            Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = palette.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Diagnostics & Copy Error Report", color = palette.error, fontWeight = FontWeight.Bold)
                        }

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
                                    containerColor = palette.accentPrimary,
                                    contentColor = palette.onAccentPrimary
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
                                border = BorderStroke(1.dp, palette.glassBorder)
                            ) {
                                Text("Return Home", color = palette.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
