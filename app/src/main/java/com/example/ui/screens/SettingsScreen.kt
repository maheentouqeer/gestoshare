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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.BuildConfig
import com.example.data.AppSettingsState
import com.example.ui.ScreenRoute
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import java.util.Locale

@Composable
fun SettingsScreen(
    currentDeviceName: String,
    settingsState: AppSettingsState = AppSettingsState(deviceName = currentDeviceName),
    errorCount: Int = 0,
    onUpdateDeviceName: (String) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit = {},
    onUpdateConfidenceThreshold: (Float) -> Unit = {},
    onUpdateConsecutiveFrames: (Int) -> Unit = {},
    onUpdateCooldownMs: (Long) -> Unit = {},
    onUpdateHoldDurationMs: (Long) -> Unit = {},
    onToggleCameraFacing: () -> Unit = {},
    onToggleHapticFeedback: (Boolean) -> Unit = {},
    onToggleSoundFeedback: (Boolean) -> Unit = {},
    onToggleGestureTransferMode: (Boolean) -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    val context = LocalContext.current
    var editingName by remember(currentDeviceName) { mutableStateOf(currentDeviceName) }
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
        onResult = { permissions -> nearbyGranted = permissions.values.all { it } }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance & Glassmorphism Theme Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("appearance_theme_card"),
                cornerRadius = 20.dp,
                fillColor = palette.glassFillElevated,
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .glassSurface(
                                    cornerRadius = 20.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentPrimary.copy(alpha = 0.55f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (settingsState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Theme Mode",
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (settingsState.isDarkTheme) "Midnight Glass (Dark)" else "Frosted Ice (Light)",
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Switch between Dark Obsidian & Light Frosted UI",
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }

                    Switch(
                        checked = settingsState.isDarkTheme,
                        onCheckedChange = onToggleDarkTheme,
                        modifier = Modifier.testTag("theme_mode_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.onAccentPrimary,
                            checkedTrackColor = palette.accentPrimary
                        )
                    )
                }
            }
        }

        // Device Display Name Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_name_card"),
                cornerRadius = 20.dp,
                borderAccent = palette.accentPrimary.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Display Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Visible to nearby Redmi / Infinix / Android peers during Nearby discovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                                focusedBorderColor = palette.accentPrimary,
                                unfocusedBorderColor = palette.glassBorder,
                                focusedTextColor = palette.textPrimary,
                                unfocusedTextColor = palette.textPrimary
                            )
                        )
                        Button(
                            onClick = {
                                onUpdateDeviceName(editingName)
                                nameSavedMessage = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentPrimary,
                                contentColor = palette.onAccentPrimary
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
                            color = palette.success,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Gesture Sensitivity, Camera & Feedback Tuning
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gesture_tuning_card"),
                cornerRadius = 20.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = palette.accentPrimary.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Gesture Sensitivity & Camera Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Confidence Threshold", fontSize = 12.sp, color = palette.textPrimary)
                        Text(
                            text = "${(settingsState.gestureConfidenceThreshold * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentPrimary
                        )
                    }
                    Slider(
                        value = settingsState.gestureConfidenceThreshold,
                        onValueChange = onUpdateConfidenceThreshold,
                        valueRange = 0.50f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accentPrimary,
                            activeTrackColor = palette.accentPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stability Frames", fontSize = 12.sp, color = palette.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 7).forEach { frames ->
                                val selected = settingsState.requiredConsecutiveFrames == frames
                                Box(
                                    modifier = Modifier
                                        .glassSurface(
                                            cornerRadius = 8.dp,
                                            fillColor = if (selected) palette.accentPrimary.copy(alpha = 0.22f) else palette.glassFillInset,
                                            borderAccent = if (selected) palette.accentPrimary else palette.glassBorder
                                        )
                                        .clickable { onUpdateConsecutiveFrames(frames) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$frames",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) palette.accentPrimary else palette.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hold Duration", fontSize = 12.sp, color = palette.textPrimary)
                        Text("${settingsState.holdDurationMs} ms", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = palette.accentPrimary)
                    }
                    Slider(
                        value = settingsState.holdDurationMs.toFloat(),
                        onValueChange = { onUpdateHoldDurationMs(it.toLong()) },
                        valueRange = 0f..1000f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Command Cooldown", fontSize = 12.sp, color = palette.textPrimary)
                        Text("${settingsState.commandCooldownMs} ms", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = palette.accentPrimary)
                    }
                    Slider(
                        value = settingsState.commandCooldownMs.toFloat(),
                        onValueChange = { onUpdateCooldownMs(it.toLong()) },
                        valueRange = 400f..3000f
                    )

                    SettingsSwitchRow(
                        label = "Use Front Selfie Camera",
                        sublabel = if (settingsState.useFrontCamera) "Front Camera Active" else "Rear Camera Active",
                        checked = settingsState.useFrontCamera,
                        onCheckedChange = { onToggleCameraFacing() }
                    )
                    SettingsSwitchRow(
                        label = "Haptic Gesture Feedback",
                        sublabel = "Vibrate subtly when gesture command triggers",
                        checked = settingsState.hapticFeedbackEnabled,
                        onCheckedChange = onToggleHapticFeedback
                    )
                    SettingsSwitchRow(
                        label = "Audio Beep Confirmation",
                        sublabel = "Short notification tone on gesture command",
                        checked = settingsState.soundFeedbackEnabled,
                        onCheckedChange = onToggleSoundFeedback
                    )
                    SettingsSwitchRow(
                        label = "Default to Gesture Transfer Mode",
                        sublabel = "Arm real THREE_GUN sends instead of Dry Run lock",
                        checked = settingsState.gestureTransferModeEnabled,
                        onCheckedChange = onToggleGestureTransferMode
                    )
                }
            }
        }

        // Diagnostics, Onboarding & About Shortcuts
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDiagnostics() }
                        .testTag("settings_open_diagnostics_card"),
                    cornerRadius = 16.dp,
                    borderAccent = if (errorCount > 0) palette.error.copy(alpha = 0.65f) else palette.accentPrimary.copy(alpha = 0.4f)
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
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Diagnostics Center",
                                tint = if (errorCount > 0) palette.error else palette.accentPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text("Diagnostics & Error Center", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 14.sp)
                                Text(
                                    text = if (errorCount > 0) "$errorCount error report(s) available to copy/export" else "Inspect telemetry logs & export TXT/JSON bundle",
                                    fontSize = 11.sp,
                                    color = if (errorCount > 0) palette.error else palette.textSecondary
                                )
                            }
                        }
                        if (errorCount > 0) {
                            GlassStatusBadge(label = "$errorCount", color = palette.error)
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = palette.textSecondary)
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReplayOnboarding() }
                        .testTag("settings_replay_onboarding_button"),
                    cornerRadius = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Replay Onboarding Guide", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 14.sp)
                                Text("Review the 5-step gesture & P2P cluster walkthrough", fontSize = 11.sp, color = palette.textSecondary)
                            }
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = palette.textSecondary)
                    }
                }

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAbout() }
                        .testTag("settings_open_about_card"),
                    cornerRadius = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(22.dp))
                            Column {
                                Text("About GestoShare", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 14.sp)
                                Text("v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) · Offline AI & Privacy", fontSize = 11.sp, color = palette.textSecondary)
                            }
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = palette.textSecondary)
                    }
                }
            }
        }

        // Hardware & Permissions Status
        item {
            Text(
                text = "Hardware & Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(modifier = Modifier.fillMaxWidth().testTag("camera_status_tile"), cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = if (cameraGranted) palette.success else palette.error)
                            Column {
                                Text("Camera Feed Access", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 13.sp)
                                Text(if (cameraGranted) "Granted · MediaPipe tracking ready" else "Required for gesture detection", fontSize = 11.sp, color = if (cameraGranted) palette.success else palette.error)
                            }
                        }
                        if (!cameraGranted) {
                            OutlinedButton(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(10.dp)) {
                                Text("Grant", fontSize = 12.sp, color = palette.accentPrimary)
                            }
                        } else {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Granted", tint = palette.success)
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth().testTag("nearby_permission_tile"), cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.NearMe, contentDescription = null, tint = if (nearbyGranted) palette.success else palette.accentSecondary)
                            Column {
                                Text("Nearby Discovery Permissions", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 13.sp)
                                Text(if (nearbyGranted) "Granted · P2P_CLUSTER ready" else "Requires Nearby Wi-Fi / Bluetooth scan permissions", fontSize = 11.sp, color = if (nearbyGranted) palette.success else palette.textSecondary)
                            }
                        }
                        if (!nearbyGranted) {
                            OutlinedButton(
                                onClick = {
                                    when {
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> nearbyLauncher.launch(
                                            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
                                        )
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> nearbyLauncher.launch(
                                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.ACCESS_FINE_LOCATION)
                                        )
                                        else -> nearbyLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Request", fontSize = 12.sp, color = palette.accentSecondary) }
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth().testTag("bluetooth_status_tile"), cornerRadius = 16.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = if (isBluetoothEnabled) palette.accentPrimary else palette.textSecondary)
                        Column {
                            Text("Bluetooth Adapter", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 13.sp)
                            Text(if (isBluetoothEnabled) "Active · Nearby handshake ready" else "Disabled or standby", fontSize = 11.sp, color = if (isBluetoothEnabled) palette.success else palette.textSecondary)
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth().testTag("wifi_status_tile"), cornerRadius = 16.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = if (isWifiEnabled) palette.accentSecondary else palette.textSecondary)
                        Column {
                            Text("Wi-Fi Direct / Local Radio", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 13.sp)
                            Text(if (isWifiEnabled) "Active · High-speed P2P transfer ready" else "Local radio standby", fontSize = 11.sp, color = if (isWifiEnabled) palette.success else palette.textSecondary)
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = palette.success)
                        Column {
                            Text("Downloads/GestoShare & Scoped Storage", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 13.sp)
                            Text("Verified payloads copied via ContentResolver URI before MediaStore export", fontSize = 11.sp, color = palette.success)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = GestoTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text(text = sublabel, fontSize = 10.sp, color = palette.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccentPrimary,
                checkedTrackColor = palette.accentPrimary
            )
        )
    }
}
