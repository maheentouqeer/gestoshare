package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransferHistoryScreen(
    transfers: List<TransferRecord>,
    onClearHistory: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredTransfers = remember(transfers, selectedFilter) {
        when (selectedFilter) {
            "SENT" -> transfers.filter { it.direction == TransferDirection.SEND }
            "RECEIVED" -> transfers.filter { it.direction == TransferDirection.RECEIVE }
            "FAILED" -> transfers.filter { it.status != TransferStatus.SUCCESS }
            else -> transfers
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("transfer_history_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "All", "SENT" to "Sent", "RECEIVED" to "Received", "FAILED" to "Failed").forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentPrimary,
                                selectedLabelColor = palette.onAccentPrimary,
                                containerColor = palette.glassFill,
                                labelColor = palette.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = palette.glassBorder,
                                selectedBorderColor = palette.accentPrimary
                            )
                        )
                    }
                }

                if (transfers.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("clear_history_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear History",
                            tint = palette.error
                        )
                    }
                }
            }
        }

        if (filteredTransfers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp)
                        .glassSurface(cornerRadius = 18.dp)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .glassSurface(
                                    cornerRadius = 28.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentPrimary.copy(alpha = 0.35f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Transfer Logs",
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Completed, failed, and cancelled P2P transfers are stored locally in Room SQLite with SHA-256 status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredTransfers, key = { it.id }) { record ->
                val isSuccess = record.status == TransferStatus.SUCCESS
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_record_${record.id}"),
                    cornerRadius = 16.dp,
                    borderAccent = (if (isSuccess) palette.success else palette.error).copy(alpha = 0.4f)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .glassSurface(
                                            cornerRadius = 18.dp,
                                            fillColor = palette.glassFillInset,
                                            borderAccent = if (record.direction == TransferDirection.SEND) palette.accentPrimary else palette.accentSecondary
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (record.direction == TransferDirection.SEND) Icons.Default.CallMade else Icons.Default.CallReceived,
                                        contentDescription = null,
                                        tint = if (record.direction == TransferDirection.SEND) palette.accentPrimary else palette.accentSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = record.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${record.fileSizeFormatted} · ${if (record.direction == TransferDirection.SEND) "To: " + record.receiverName else "From: " + record.senderName}",
                                        fontSize = 11.sp,
                                        color = palette.textSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteRecord(record.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(
                                    cornerRadius = 8.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.glassBorder
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isSuccess) palette.success else palette.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = record.status.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuccess) palette.success else palette.error
                                )
                                Text(
                                    text = "· ${record.sha256Verification}",
                                    fontSize = 10.sp,
                                    color = palette.textSecondary,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = dateFormatter.format(Date(record.timestamp)),
                                fontSize = 10.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = palette.dialogSurface,
            title = { Text("Clear All Transfer History?", color = palette.textPrimary) },
            text = { Text("This will permanently remove all transfer records from local SQLite storage.", color = palette.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.error, contentColor = Color.White)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}
