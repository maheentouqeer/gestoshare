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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.example.model.SessionStatus
import com.example.ui.ScreenRoute
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NearbyDevicesScreen(
    discoveredDevices: List<DeviceItem>,
    selectedDevice: DeviceItem?,
    connectedDevice: DeviceItem?,
    pendingSession: ConnectionSession?,
    receivedMessages: List<NearbyMessage>,
    statusMessage: String,
    isDiscovering: Boolean,
    isAdvertising: Boolean,
    isNearbyAvailable: Boolean,
    stagedItems: List<ContentItem>,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onToggleAdvertising: () -> Unit,
    onRequestConnection: (DeviceItem) -> Unit,
    onAcceptConnection: (String) -> Unit,
    onRejectConnection: (String) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onSelectDevice: (DeviceItem) -> Unit,
    onSendTextMessage: (String) -> Unit,
    onSendPingTest: () -> Unit,
    onExecuteTransfer: (DeviceItem) -> Unit,
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    var customText by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("nearby_devices_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nearby_status_banner"),
                cornerRadius = 20.dp,
                fillColor = GlassFillElevated,
                borderAccent = NeonCyan.copy(alpha = 0.4f)
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
                                    .size(10.dp)
                                    .background(
                                        when {
                                            connectedDevice != null -> EmeraldSuccess
                                            isDiscovering -> NeonCyan
                                            isAdvertising -> ElectricViolet
                                            else -> TextSecondary
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "NEARBY CONNECTIONS (P2P_POINT_TO_POINT)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }

                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NeonCyan,
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
                        color = if (!isNearbyAvailable) RoseError else TextPrimary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isDiscovering) onStopDiscovery() else onStartDiscovery()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("toggle_discovery_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDiscovering) Color(0x551E293B) else NeonCyan,
                                contentColor = if (isDiscovering) NeonCyan else DarkBackground
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isDiscovering) NeonCyan else Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isDiscovering) Icons.Default.Close else Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDiscovering) "Stop Scan" else "Scan Nearby",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onToggleAdvertising,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("toggle_advertising_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAdvertising) ElectricViolet.copy(alpha = 0.85f) else GlassFill,
                                contentColor = TextPrimary
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isAdvertising) ElectricViolet else Color.White.copy(alpha = 0.22f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = if (isAdvertising) Color.White else NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAdvertising) "Receiving..." else "Receive Mode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (connectedDevice != null) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connected_device_console"),
                    cornerRadius = 20.dp,
                    fillColor = EmeraldSuccess.copy(alpha = 0.10f),
                    borderAccent = EmeraldSuccess.copy(alpha = 0.8f),
                    borderWidth = 1.5.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                        .glassSurface(cornerRadius = 18.dp, fillColor = Color(0x66060B18), borderAccent = EmeraldSuccess),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Connected to ${connectedDevice.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Endpoint: ${connectedDevice.endpointId} · Offline Link Active",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = EmeraldSuccess
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { onDisconnectDevice(connectedDevice.endpointId) },
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("disconnect_connected_device_button"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, RoseError)
                            ) {
                                Text("Disconnect", fontSize = 11.sp, color = RoseError)
                            }
                        }

                        if (stagedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onExecuteTransfer(connectedDevice) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("transfer_staged_now_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send ${stagedItems.size} Staged Item(s) Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "P2P Link Verification & Chat (Bytes Payload)",
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onSendPingTest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("send_ping_test_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet, contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Test Ping: 'Hello from GestoShare'", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                placeholder = { Text("Type custom message...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("custom_message_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Button(
                                onClick = {
                                    if (customText.isNotBlank()) {
                                        onSendTextMessage(customText.trim())
                                        customText = ""
                                    }
                                },
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("send_custom_message_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground)
                            ) {
                                Text("Send", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Transmission Feed (${receivedMessages.size}):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (receivedMessages.isEmpty()) {
                            Text(
                                text = "No messages exchanged yet. Tap 'Send Test Ping' above to verify the P2P link.",
                                fontSize = 11.sp,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        } else {
                            receivedMessages.takeLast(5).forEach { msg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .glassSurface(
                                            cornerRadius = 8.dp,
                                            fillColor = if (msg.isIncoming) Color(0x66060B18) else NeonCyan.copy(alpha = 0.10f),
                                            borderAccent = Color.White.copy(alpha = 0.14f)
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
                                            imageVector = if (msg.isAcknowledgment) Icons.Default.DoneAll else Icons.Default.Send,
                                            contentDescription = null,
                                            tint = if (msg.isAcknowledgment) EmeraldSuccess else if (msg.isIncoming) ElectricViolet else NeonCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${if (msg.isIncoming) msg.senderName else "You"}: ${msg.messageText}",
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                    Text(
                                        text = timeFormatter.format(Date(msg.timestamp)),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

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
                        color = TextPrimary
                    )
                    Text(
                        text = "Physical devices broadcasting GestoShare service",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = {
                        onStopDiscovery()
                        onStartDiscovery()
                    },
                    modifier = Modifier.testTag("refresh_discovery_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Scan",
                        tint = NeonCyan
                    )
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
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .glassSurface(cornerRadius = 24.dp, fillColor = Color(0x66060B18), borderAccent = NeonCyan.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isDiscovering) NeonCyan else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isDiscovering) "Scanning for Nearby GestoShare Devices..." else "No Devices Discovered",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isDiscovering) {
                                "Ensure the other phone (Redmi or Infinix) is open and has 'Receive Mode' enabled."
                            } else {
                                "Tap 'Scan Nearby' above to start searching for advertisers."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(discoveredDevices, key = { it.endpointId }) { device ->
                val isSelected = selectedDevice?.endpointId == device.endpointId
                val isConnected = connectedDevice?.endpointId == device.endpointId

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("endpoint_card_${device.endpointId}"),
                    cornerRadius = 16.dp,
                    fillColor = when {
                        isConnected -> EmeraldSuccess.copy(alpha = 0.10f)
                        isSelected -> NeonCyan.copy(alpha = 0.10f)
                        else -> GlassFill
                    },
                    borderAccent = when {
                        isConnected -> EmeraldSuccess
                        isSelected -> NeonCyan
                        else -> Color.White.copy(alpha = 0.18f)
                    },
                    borderWidth = if (isConnected || isSelected) 1.5.dp else 1.dp
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .glassSurface(
                                            cornerRadius = 21.dp,
                                            fillColor = Color(0x66060B18),
                                            borderAccent = if (isConnected) EmeraldSuccess else if (isSelected) NeonCyan else Color.White.copy(alpha = 0.25f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (isConnected) EmeraldSuccess else if (isSelected) NeonCyan else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "ID: ${device.endpointId}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary
                                    )
                                }
                            }

                            val stateColor = when (device.connectionState) {
                                DeviceConnectionState.CONNECTED -> EmeraldSuccess
                                DeviceConnectionState.CONNECTING -> NeonCyan
                                DeviceConnectionState.DISCOVERED -> TextSecondary
                                DeviceConnectionState.FAILED -> RoseError
                                DeviceConnectionState.DISCONNECTED -> Color(0xFF64748B)
                            }

                            Box(
                                modifier = Modifier
                                    .background(stateColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = device.connectionState.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = stateColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSelectDevice(device) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("select_device_${device.endpointId}"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) NeonCyan else Color.White.copy(alpha = 0.22f)
                                )
                            ) {
                                Text(
                                    text = if (isSelected) "Selected Target" else "Select Target",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) NeonCyan else TextPrimary
                                )
                            }

                            if (device.connectionState != DeviceConnectionState.CONNECTED) {
                                Button(
                                    onClick = { onRequestConnection(device) },
                                    enabled = device.connectionState != DeviceConnectionState.CONNECTING,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("request_connection_${device.endpointId}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = DarkBackground
                                    )
                                ) {
                                    Text(
                                        text = if (device.connectionState == DeviceConnectionState.CONNECTING) "Connecting..." else "Connect",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onDisconnectDevice(device.endpointId) },
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("disconnect_${device.endpointId}"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, RoseError)
                                ) {
                                    Text("Disconnect", fontSize = 12.sp, color = RoseError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingSession != null && pendingSession.status == SessionStatus.INITIATED) {
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassSurface(cornerRadius = 24.dp, fillColor = Color(0x66060B18), borderAccent = NeonCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Handshake Authentication",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = if (pendingSession.isIncoming) "Incoming Connection Request" else "Confirm Connection Code",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (pendingSession.isIncoming) {
                            "${pendingSession.endpointName} wants to connect with your device."
                        } else {
                            "Connecting to ${pendingSession.endpointName}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "CONFIRM SECURITY CODE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .glassSurface(cornerRadius = 12.dp, fillColor = Color(0x88060B18), borderAccent = NeonCyan, borderWidth = 1.5.dp)
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                            .testTag("handshake_auth_digits")
                    ) {
                        Text(
                            text = pendingSession.authDigits,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = NeonCyan,
                            letterSpacing = 4.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Verify that the other phone displays the exact same code before accepting.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAcceptConnection(pendingSession.endpointId) },
                    modifier = Modifier.testTag("accept_handshake_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
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
                    border = BorderStroke(1.dp, RoseError),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reject", color = RoseError)
                }
            },
            containerColor = DarkSurface
        )
    }
}
