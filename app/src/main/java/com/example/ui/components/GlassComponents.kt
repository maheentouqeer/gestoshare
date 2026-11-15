package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.DiagnosticErrorEvent
import com.example.model.ErrorSeverity
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GlassStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = GestoTheme.colors.accentPrimary,
    contentColor: Color = GestoTheme.colors.onAccentPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.32f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = GestoTheme.colors.accentPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (enabled) 0.7f else 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = GestoTheme.colors.glassFillInset,
            contentColor = accentColor
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun GlassSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val palette = GestoTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun GlassProgressIndicator(
    progress: Float,
    accentColor: Color = GestoTheme.colors.accentPrimary,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = accentColor,
        trackColor = if (palette.isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    )
}

@Composable
fun GlassErrorCard(
    event: DiagnosticErrorEvent,
    onClick: () -> Unit,
    onCopyReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    val sevColor = when (event.severity) {
        ErrorSeverity.CRITICAL, ErrorSeverity.ERROR -> palette.error
        ErrorSeverity.WARNING -> palette.warning
        ErrorSeverity.INFO -> palette.accentPrimary
    }
    val fmt = SimpleDateFormat("MMM dd · HH:mm:ss", Locale.US)

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("error_card_${event.code}"),
        cornerRadius = 18.dp,
        borderAccent = sevColor.copy(alpha = 0.55f)
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
                        imageVector = when (event.severity) {
                            ErrorSeverity.WARNING -> Icons.Default.Warning
                            ErrorSeverity.INFO -> Icons.Default.BugReport
                            else -> Icons.Default.Error
                        },
                        contentDescription = event.severity.label,
                        tint = sevColor,
                        modifier = Modifier.size(18.dp)
                    )
                    GlassStatusBadge(label = event.code, color = sevColor)
                    GlassStatusBadge(label = event.category.label, color = palette.accentSecondary)
                }

                IconButton(
                    onClick = onCopyReport,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("copy_error_${event.code}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy error report",
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${event.sourceComponent}.${event.operation}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = fmt.format(Date(event.timestamp)),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textMuted
                    )
                }
            }
        }
    }
}

/**
 * Compact floating glass overlay docked cleanly at the top edge of the CameraX feed.
 * Shows connected & nearby devices without obstructing the center hand-tracking zone.
 */
@Composable
fun GlassCameraRecipientOverlay(
    connectedDevices: List<DeviceItem>,
    discoveredDevices: List<DeviceItem>,
    selectedDevice: DeviceItem?,
    isMultiSelectMode: Boolean,
    multiSelectRecipientIds: Set<String>,
    onSelectDevice: (DeviceItem) -> Unit,
    onToggleMultiRecipient: (DeviceItem) -> Unit,
    onRequestConnection: (DeviceItem) -> Unit,
    onToggleMultiSelectMode: () -> Unit,
    onManageDevicesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    // Combine connected devices first, then any undiscovered peers so the overlay is always informative
    val displayDevices = if (connectedDevices.isNotEmpty()) {
        connectedDevices + discoveredDevices.filter { d ->
            connectedDevices.none { it.endpointId == d.endpointId }
        }
    } else {
        discoveredDevices
    }

    val selectedIndex = displayDevices.indexOfFirst { it.endpointId == selectedDevice?.endpointId }
    val selectedSummary = when {
        connectedDevices.isEmpty() && discoveredDevices.isEmpty() -> "No peers visible"
        isMultiSelectMode && multiSelectRecipientIds.isNotEmpty() -> "${multiSelectRecipientIds.size} selected (Group)"
        selectedDevice != null && displayDevices.size > 1 && selectedIndex >= 0 ->
            "${selectedDevice.name} (${selectedIndex + 1} of ${displayDevices.size})"
        selectedDevice != null && connectedDevices.size == 1 ->
            "${selectedDevice.name} (Only connected)"
        selectedDevice != null -> selectedDevice.name
        else -> "👉 POINT to select peer"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                cornerRadius = 16.dp,
                fillColor = if (palette.isDark) Color(0xCC070D1D) else Color(0xDDF4F8FF),
                borderAccent = palette.accentPrimary.copy(alpha = 0.50f)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("camera_nearby_recipients_overlay")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            if (connectedDevices.isNotEmpty()) palette.success else palette.accentPrimary,
                            CircleShape
                        )
                )
                Text(
                    text = "NEARBY RECIPIENTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = palette.accentPrimary
                )
                Text(
                    text = "· $selectedSummary",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (connectedDevices.size > 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isMultiSelectMode) palette.accentSecondary.copy(alpha = 0.28f)
                                else palette.glassFillInset
                            )
                            .border(
                                1.dp,
                                if (isMultiSelectMode) palette.accentSecondary else palette.glassBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleMultiSelectMode() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("camera_overlay_multiselect_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Group Mode",
                                tint = if (isMultiSelectMode) palette.accentSecondary else palette.textSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "MULTI",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMultiSelectMode) palette.accentSecondary else palette.textSecondary
                            )
                        }
                    }
                }

                Text(
                    text = "Manage",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onManageDevicesClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        if (displayDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                itemsIndexed(displayDevices, key = { _, d -> d.endpointId }) { index, dev ->
                    val isConn = connectedDevices.any { it.endpointId == dev.endpointId } ||
                        dev.connectionState == DeviceConnectionState.CONNECTED
                    val isPicked = if (isMultiSelectMode) {
                        multiSelectRecipientIds.contains(dev.endpointId)
                    } else {
                        selectedDevice?.endpointId == dev.endpointId
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isPicked && isConn -> palette.success.copy(alpha = 0.22f)
                                    isPicked -> palette.accentPrimary.copy(alpha = 0.20f)
                                    isConn -> palette.success.copy(alpha = 0.08f)
                                    else -> palette.glassFillInset
                                }
                            )
                            .border(
                                width = if (isPicked) 1.5.dp else 1.dp,
                                color = when {
                                    isPicked && isConn -> palette.success
                                    isPicked -> palette.accentPrimary
                                    isConn -> palette.success.copy(alpha = 0.45f)
                                    else -> palette.glassBorder
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (isMultiSelectMode && isConn) {
                                    onToggleMultiRecipient(dev)
                                } else {
                                    onSelectDevice(dev)
                                    if (!isConn && dev.connectionState == DeviceConnectionState.DISCOVERED) {
                                        onRequestConnection(dev)
                                    }
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("overlay_peer_chip_${dev.endpointId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isPicked) Icons.Default.CheckCircle else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = when {
                                isConn -> palette.success
                                isPicked -> palette.accentPrimary
                                else -> palette.textSecondary
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${index + 1}. ${dev.name}",
                            fontSize = 10.sp,
                            fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                            color = palette.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = if (isConn) "LINKED" else dev.connectionState.name.take(4),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isConn) palette.success else palette.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassTransferStageCard(
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isActive: Boolean,
    isFailed: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    val accent = when {
        isFailed -> palette.error
        isCompleted -> palette.success
        isActive -> palette.accentPrimary
        else -> palette.textMuted
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                cornerRadius = 12.dp,
                fillColor = if (isActive || isCompleted || isFailed) accent.copy(alpha = 0.10f) else palette.glassFillInset,
                borderAccent = accent.copy(alpha = if (isActive || isCompleted || isFailed) 0.6f else 0.2f)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = when {
                isFailed -> Icons.Default.Close
                isCompleted -> Icons.Default.CheckCircle
                else -> Icons.Default.Schedule
            },
            contentDescription = title,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (isFailed) palette.error else palette.textSecondary
            )
        }
    }
}
