package com.example.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
fun SettingsScreen(
    currentDeviceName: String,
    onUpdateDeviceName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingName by remember { mutableStateOf(currentDeviceName) }
    var nameSavedMessage by remember { mutableStateOf(false) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> cameraGranted = granted }
    )

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val isBluetoothEnabled = remember { bluetoothManager?.adapter?.isEnabled == true }

    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    val isWifiEnabled = remember { wifiManager?.isWifiEnabled == true }

    var nearbyGranted by remember {
        mutableStateOf(
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                else ->
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val nearbyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            nearbyGranted = permissions.values.all { it }
        }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_name_card"),
                cornerRadius = 20.dp,
                borderAccent = NeonCyan.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Display Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Visible to nearby Redmi / Infinix / Android peers during Nearby discovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = {
                                editingName = it
                                nameSavedMessage = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("device_name_input"),
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
                                onUpdateDeviceName(editingName)
                                nameSavedMessage = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("save_device_name_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (nameSavedMessage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "✓ Device name updated successfully",
                            fontSize = 12.sp,
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Hardware & System Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("camera_status_tile"),
                cornerRadius = 16.dp
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
                                .size(40.dp)
                                .glassSurface(cornerRadius = 20.dp, fillColor = Color(0x66060B18), borderAccent = if (cameraGranted) EmeraldSuccess else RoseError),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (cameraGranted) EmeraldSuccess else RoseError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Camera Feed Access",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (cameraGranted) "Granted · MediaPipe tracking ready" else "Required for gesture detection",
                                fontSize = 11.sp,
                                color = if (cameraGranted) EmeraldSuccess else RoseError
                            )
                        }
                    }

                    if (!cameraGranted) {
                        OutlinedButton(
                            onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, color = NeonCyan)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Granted",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bluetooth_status_tile"),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .glassSurface(cornerRadius = 20.dp, fillColor = Color(0x66060B18), borderAccent = if (isBluetoothEnabled) NeonCyan else Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (isBluetoothEnabled) NeonCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bluetooth Adapter",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBluetoothEnabled) "Active · Nearby handshake ready" else "Disabled or standby",
                            fontSize = 11.sp,
                            color = if (isBluetoothEnabled) EmeraldSuccess else TextSecondary
                        )
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wifi_status_tile"),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .glassSurface(cornerRadius = 20.dp, fillColor = Color(0x66060B18), borderAccent = if (isWifiEnabled) ElectricViolet else Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (isWifiEnabled) ElectricViolet else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wi-Fi Direct / Local Radio",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isWifiEnabled) "Active · High-speed P2P transfer ready" else "Local radio standby",
                            fontSize = 11.sp,
                            color = if (isWifiEnabled) EmeraldSuccess else TextSecondary
                        )
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nearby_permission_tile"),
                cornerRadius = 16.dp
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
                                .size(40.dp)
                                .glassSurface(cornerRadius = 20.dp, fillColor = Color(0x66060B18), borderAccent = if (nearbyGranted) EmeraldSuccess else ElectricViolet),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = if (nearbyGranted) EmeraldSuccess else ElectricViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Nearby Discovery Permissions",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (nearbyGranted) "Granted · Peer discovery ready" else "Requires Nearby / Scan permissions",
                                fontSize = 11.sp,
                                color = if (nearbyGranted) EmeraldSuccess else TextSecondary
                            )
                        }
                    }

                    if (!nearbyGranted) {
                        OutlinedButton(
                            onClick = {
                                when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> nearbyLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.NEARBY_WIFI_DEVICES,
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_ADVERTISE
                                        )
                                    )
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> nearbyLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_ADVERTISE,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                    else -> nearbyLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                    )
                                }
                            },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Request", fontSize = 12.sp, color = ElectricViolet)
                        }
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .glassSurface(cornerRadius = 20.dp, fillColor = Color(0x66060B18), borderAccent = EmeraldSuccess),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Downloads/GestoShare & SAF",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Received files verified via SHA-256 & saved to Downloads/GestoShare",
                            fontSize = 11.sp,
                            color = EmeraldSuccess
                        )
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("architecture_roadmap_card"),
                cornerRadius = 20.dp,
                fillColor = GlassFillElevated,
                borderAccent = NeonCyan.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "GestoShare Pipeline Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val roadmapItems = listOf(
                        "Phase 3–4 (Active)" to "Google Nearby Connections P2P_POINT_TO_POINT discovery, 6-digit handshake, receiver consent, real file/text/URL streaming & MediaStore Downloads export.",
                        "Phase 5 (Active)" to "Streaming SHA-256 verification on sender & receiver before marking completion, automatic Room SQLite history audit logging.",
                        "Phase 6 (Active)" to "CameraX ImageAnalysis + MediaPipe HandLandmarker 21 keypoints normalized to [1, 63] vector with live Canvas skeleton overlay.",
                        "Phase 7 (Next)" to "Custom trained HaGRIDv2 5-class ONNX model consuming the [1, 63] landmark feature tensor."
                    )

                    roadmapItems.forEach { (title, desc) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "• $title",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(
                                text = desc,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_card"),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "About GestoShare",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0 · Native Kotlin & Jetpack Compose\nOffline P2P File Sharing · Glassmorphism UI",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
