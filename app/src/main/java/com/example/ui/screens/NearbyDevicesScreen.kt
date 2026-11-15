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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.NearbyMessage
import com.example.model.RecentDeviceRecord
import com.example.model.SessionStatus
import com.example.ui.ScreenRoute
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NearbyDevicesScreen(
    discoveredDevices: List<DeviceItem>,
    selectedDevice: DeviceItem?,
    connectedDevice: DeviceItem?,
    connectedDevices: List<DeviceItem> = listOfNotNull(connectedDevice),
    pendingSession: ConnectionSession?,
    recentDevices: List<RecentDeviceRecord> = emptyList(),
    receivedMessages: List<NearbyMessage>,
    statusMessage: String,
    isDiscovering: Boolean,
    isAdvertising: Boolean,
    isNearbyAvailable: Boolean,
    stagedItems: List<ContentItem>,
    isMultiSelectMode: Boolean = false,
    multiSelectRecipientIds: Set<String> = emptySet(),
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onToggleAdvertising: () -> Unit,
    onRequestConnection: (DeviceItem) -> Unit,
    onAcceptConnection: (String) -> Unit,
    onRejectConnection: (String) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onForgetDevice: (String) -> Unit = {},
    onForgetRecentDevice: (String) -> Unit = {},
    onSelectDevice: (DeviceItem) -> Unit,
    onToggleMultiSelectMode: () -> Unit = {},
    onToggleMultiSelectRecipient: (DeviceItem) -> Unit = {},
    onSendTextMessage: (String) -> Unit,
    onSendTextMessageTo: (String, String) -> Unit = { _, msg -> onSendTextMessage(msg) },
    onSendPingTest: () -> Unit,
    onExecuteTransfer: (DeviceItem) -> Unit,
    onExecuteMultiTransfer: () -> Unit = {},
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    var customText by remember { mutableStateOf("") }
    var inspectedDevice by remember { mutableStateOf<DeviceItem?>(null) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val activeChatDevice = selectedDevice?.takeIf { sel ->
        connectedDevices.any { it.endpointId == sel.endpointId }
    } ?: connectedDevice ?: connectedDevices.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("nearby_devices_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cluster Control Banner
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nearby_status_banner"),
                cornerRadius = 20.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = palette.accentPrimary.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                    .size(9.dp)
                                    .background(
                                        when {
                                            connectedDevices.isNotEmpty() -> palette.success
                                            isDiscovering -> palette.accentPrimary
                                            isAdvertising -> palette.accentSecondary
                                            else -> palette.textSecondary
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "NEARBY CONNECTIONS (P2P_CLUSTER)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentPrimary
                            )
                        }
                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = palette.accentPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (!isNearbyAvailable) {
                            "Google Play Services Nearby unavailable on this device."
                        } else {
                            statusMessage
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!isNearbyAvailable) palette.error else palette.textPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { if (isDiscovering) onStopDiscovery() else onStartDiscovery() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("toggle_discovery_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDiscovering) palette.glassFillInset else palette.accentPrimary,
                                contentColor = if (isDiscovering) palette.accentPrimary else palette.onAccentPrimary
                            ),
                            border = BorderStroke(1.dp, if (isDiscovering) palette.accentPrimary else Color.Transparent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = if (isDiscovering) Icons.Default.Close else Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isDiscovering) "Stop Scan" else "Scan Nearby", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onToggleAdvertising,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("toggle_advertising_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAdvertising) palette.accentSecondary else palette.glassFill,
                                contentColor = if (isAdvertising) Color.White else palette.textPrimary
                            ),
                            border = BorderStroke(1.dp, if (isAdvertising) palette.accentSecondary else palette.glassBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Radio, contentDescription = null, tint = if (isAdvertising) Color.White else palette.accentPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isAdvertising) "Receiving..." else "Receive Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Connected Cluster Deck + Group Multi-Select + Chat/Ping Console
        if (connectedDevices.isNotEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connected_device_console"),
                    cornerRadius = 20.dp,
                    fillColor = palette.success.copy(alpha = 0.08f),
                    borderAccent = palette.success.copy(alpha = 0.75f),
                    borderWidth = 1.5.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Connected Cluster (${connectedDevices.size} Peer${if (connectedDevices.size > 1) "s" else ""})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = activeChatDevice?.let { "Active target: ${it.name} (${it.endpointId})" } ?: "Tap a peer to target",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.success
                                )
                            }
                            if (connectedDevices.size > 1) {
                                OutlinedButton(
                                    onClick = onToggleMultiSelectMode,
                                    modifier = Modifier.height(34.dp).testTag("toggle_multi_select_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isMultiSelectMode) palette.accentSecondary else palette.glassBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = if (isMultiSelectMode) palette.accentSecondary else palette.textSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isMultiSelectMode) "Group ON" else "Group Send", fontSize = 11.sp, color = if (isMultiSelectMode) palette.accentSecondary else palette.textPrimary)
                                }
                            }
                        }

                        connectedDevices.forEach { dev ->
                            val isSelected = selectedDevice?.endpointId == dev.endpointId
                            val isChecked = multiSelectRecipientIds.contains(dev.endpointId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassSurface(
                                        cornerRadius = 12.dp,
                                        fillColor = palette.glassFillInset,
                                        borderAccent = if (isSelected || isChecked) palette.success else palette.glassBorder
                                    )
                                    .clickable {
                                        if (isMultiSelectMode) onToggleMultiSelectRecipient(dev) else onSelectDevice(dev)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                    if (isMultiSelectMode) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { onToggleMultiSelectRecipient(dev) }
                                        )
                                    } else {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = palette.success, modifier = Modifier.size(16.dp))
                                    }
                                    Column {
                                        Text(
                                            text = dev.name + if (isSelected) " (Active)" else "",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = "ID: ${dev.endpointId}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = palette.success
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onDisconnectDevice(dev.endpointId) },
                                    modifier = Modifier.height(32.dp).testTag("disconnect_${dev.endpointId}"),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, palette.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Disconnect", fontSize = 10.sp, color = palette.error)
                                }
                            }
                        }

                        if (stagedItems.isNotEmpty()) {
                            if (isMultiSelectMode && multiSelectRecipientIds.isNotEmpty()) {
                                Button(
                                    onClick = onExecuteMultiTransfer,
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_multi_recipients_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentSecondary, contentColor = Color.White)
                                ) {
                                    Icon(imageVector = Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send ${stagedItems.size} Item(s) to ${multiSelectRecipientIds.size} Selected Peer(s)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else if (activeChatDevice != null) {
                                Button(
                                    onClick = { onExecuteTransfer(activeChatDevice) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("transfer_staged_now_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary, contentColor = palette.onAccentPrimary)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send ${stagedItems.size} Item(s) to ${activeChatDevice.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Link Verification & Chat Console
                        Button(
                            onClick = onSendPingTest,
                            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("send_ping_test_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentSecondary, contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Test Ping to ${activeChatDevice?.name ?: "Peer"}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                placeholder = { Text("Type message...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f).height(52.dp).testTag("custom_message_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentPrimary,
                                    unfocusedBorderColor = palette.glassBorder,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                )
                            )
                            Button(
                                onClick = {
                                    if (customText.isNotBlank() && activeChatDevice != null) {
                                        onSendTextMessageTo(activeChatDevice.endpointId, customText.trim())
                                        customText = ""
                                    }
                                },
                                modifier = Modifier.height(52.dp).testTag("send_custom_message_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary, contentColor = palette.onAccentPrimary)
                            ) {
                                Text("Send", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Text(
                            text = "Transmission Feed (${receivedMessages.size}):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                        receivedMessages.takeLast(4).forEach { msg ->
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (msg.isAcknowledgment) Icons.Default.DoneAll else Icons.Default.Send,
                                        contentDescription = null,
                                        tint = if (msg.isAcknowledgment) palette.success else palette.accentPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${if (msg.isIncoming) msg.senderName else "You"}: ${msg.messageText}",
                                        fontSize = 11.sp,
                                        color = palette.textPrimary
                                    )
                                }
                                Text(
                                    text = timeFormatter.format(Date(msg.timestamp)),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Discovered Endpoints Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Discovered Endpoints (${discoveredDevices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Physical Android peers broadcasting GestoShare P2P_CLUSTER",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
                IconButton(
                    onClick = { onStopDiscovery(); onStartDiscovery() },
                    modifier = Modifier.testTag("refresh_discovery_icon_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Scan", tint = palette.accentPrimary)
                }
            }
        }

        if (discoveredDevices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(cornerRadius = 18.dp)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (isDiscovering) palette.accentPrimary else palette.textSecondary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isDiscovering) "Scanning for Nearby GestoShare Devices..." else "No Devices Discovered",
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isDiscovering) "Ensure the other phone (Redmi or Infinix) has Receive Mode enabled." else "Tap 'Scan Nearby' above to search for peers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(discoveredDevices, key = { it.endpointId }) { device ->
                val isSelected = selectedDevice?.endpointId == device.endpointId
                val isConnected = device.isConnected
                val stateColor = when (device.connectionState) {
                    DeviceConnectionState.CONNECTED -> palette.success
                    DeviceConnectionState.CONNECTING, DeviceConnectionState.AUTHENTICATING -> palette.accentPrimary
                    DeviceConnectionState.FAILED -> palette.error
                    DeviceConnectionState.UNAVAILABLE, DeviceConnectionState.DISCONNECTED -> palette.warning
                    DeviceConnectionState.DISCOVERED -> palette.textSecondary
                }

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { inspectedDevice = device }
                        .testTag("endpoint_card_${device.endpointId}"),
                    cornerRadius = 16.dp,
                    borderAccent = when {
                        isConnected -> palette.success
                        isSelected -> palette.accentPrimary
                        else -> palette.glassBorder
                    },
                    borderWidth = if (isConnected || isSelected) 1.5.dp else 1.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = stateColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(text = device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                    Text(
                                        text = "ID: ${device.endpointId}" + (device.statusDetail?.let { " · $it" } ?: ""),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                GlassStatusBadge(label = device.connectionState.badgeLabel, color = stateColor)
                                IconButton(
                                    onClick = { inspectedDevice = device },
                                    modifier = Modifier.size(28.dp).testTag("device_details_${device.endpointId}")
                                ) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = "Device Details", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onSelectDevice(device) },
                                modifier = Modifier.weight(1f).height(40.dp).testTag("select_device_${device.endpointId}"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) palette.accentPrimary else palette.glassBorder)
                            ) {
                                Text(
                                    text = if (isSelected) "Selected" else "Select",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) palette.accentPrimary else palette.textPrimary
                                )
                            }

                            if (!isConnected) {
                                Button(
                                    onClick = { onRequestConnection(device) },
                                    enabled = !device.isBusyConnecting,
                                    modifier = Modifier.height(40.dp).testTag("request_connection_${device.endpointId}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary, contentColor = palette.onAccentPrimary)
                                ) {
                                    Text(
                                        text = if (device.isBusyConnecting) "Connecting..." else "Connect",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onDisconnectDevice(device.endpointId) },
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, palette.error)
                                ) {
                                    Text("Disconnect", fontSize = 12.sp, color = palette.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Remembered Recent Devices (No auto-trust)
        if (recentDevices.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Remembered Peers (${recentDevices.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "History only — pairing always requires live Nearby confirmation.",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
            }
            items(recentDevices, key = { it.deviceKey }) { rec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(cornerRadius = 14.dp, fillColor = palette.glassFill, borderAccent = palette.glassBorder)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = rec.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                        Text(
                            text = "Paired ${rec.connectionCount}x · Last endpoint ${rec.lastEndpointId}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { onForgetRecentDevice(rec.deviceKey) },
                        modifier = Modifier.testTag("forget_recent_${rec.deviceKey}")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Forget Peer", tint = palette.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Device Details Modal
    inspectedDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { inspectedDevice = null },
            containerColor = palette.dialogSurface,
            title = { Text(text = dev.name, color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassStatusBadge(label = dev.connectionState.badgeLabel, color = palette.accentPrimary)
                    Text(
                        text = "Endpoint ID: ${dev.endpointId}\nLast Seen: ${timeFormatter.format(Date(dev.lastSeenTimestamp))}\nStatus: ${dev.statusDetail ?: dev.connectionState.name}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onForgetDevice(dev.endpointId)
                        inspectedDevice = null
                    },
                    modifier = Modifier.testTag("forget_device_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.error, contentColor = Color.White)
                ) {
                    Text("Forget Device")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedDevice = null }) {
                    Text("Close", color = palette.textSecondary)
                }
            }
        )
    }

    // 6-Digit Handshake Authentication Modal
    if (pendingSession != null && pendingSession.status == SessionStatus.INITIATED) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = palette.dialogSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Handshake Authentication",
                    tint = palette.accentPrimary,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = if (pendingSession.isIncoming) "Incoming Connection Request" else "Confirm Connection Code",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (pendingSession.isIncoming) "${pendingSession.endpointName} wants to connect with your device." else "Connecting to ${pendingSession.endpointName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .glassSurface(cornerRadius = 12.dp, fillColor = palette.glassFillInset, borderAccent = palette.accentPrimary, borderWidth = 1.5.dp)
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                            .testTag("handshake_auth_digits")
                    ) {
                        Text(
                            text = pendingSession.authDigits,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = palette.accentPrimary,
                            letterSpacing = 4.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Verify both phones show this exact code before accepting.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAcceptConnection(pendingSession.endpointId) },
                    modifier = Modifier.testTag("accept_handshake_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.success, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept Code")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onRejectConnection(pendingSession.endpointId) },
                    modifier = Modifier.testTag("reject_handshake_button"),
                    border = BorderStroke(1.dp, palette.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reject", color = palette.error)
                }
            }
        )
    }
}
