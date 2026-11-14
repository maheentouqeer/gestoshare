package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScreenRoute
import com.example.ui.theme.GlassFill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.glassSurface

@Composable
fun AirShareTopBar(
    currentRoute: ScreenRoute,
    isAdvertising: Boolean,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleAdvertising: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (currentRoute) {
        ScreenRoute.HOME -> "GestoShare"
        ScreenRoute.CONTENT_SELECTION -> "Select Content"
        ScreenRoute.NEARBY_DEVICES -> "Nearby Devices"
        ScreenRoute.GESTURE_CONTROL -> "Gesture Radar"
        ScreenRoute.TRANSFER -> "Active Transfer"
        ScreenRoute.HISTORY -> "Transfer History"
        ScreenRoute.SETTINGS -> "Settings & Hardware"
    }

    val subtitle = when (currentRoute) {
        ScreenRoute.HOME -> "Peer-to-Peer Gesture File Sharing"
        ScreenRoute.CONTENT_SELECTION -> "Import files, photos, PDFs & links"
        ScreenRoute.NEARBY_DEVICES -> "Discover and connect nearby Android peers"
        ScreenRoute.GESTURE_CONTROL -> "CameraX + MediaPipe 21-Keypoint Pipeline"
        ScreenRoute.TRANSFER -> "Offline P2P transfer with SHA-256 verification"
        ScreenRoute.HISTORY -> "Audit logs and cryptographic integrity records"
        ScreenRoute.SETTINGS -> "Bluetooth, Wi-Fi & AI model configuration"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xD80A1124),
                        Color(0xAA0C162E)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (currentRoute != ScreenRoute.HOME) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("topbar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate Back",
                                tint = NeonCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .glassSurface(
                                    cornerRadius = 19.dp,
                                    fillColor = GlassFill,
                                    borderAccent = NeonCyan.copy(alpha = 0.65f),
                                    borderWidth = 1.5.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡",
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .glassSurface(
                                cornerRadius = 20.dp,
                                fillColor = if (isAdvertising) NeonCyan.copy(alpha = 0.20f) else GlassFill,
                                borderAccent = if (isAdvertising) NeonCyan else Color.White.copy(alpha = 0.20f)
                            )
                            .clickable { onToggleAdvertising() }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                            .testTag("toggle_receive_pill"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = "Receive Mode",
                                tint = if (isAdvertising) NeonCyan else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isAdvertising) "RECEIVING" else "STANDBY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdvertising) NeonCyan else TextSecondary
                            )
                        }
                    }

                    if (currentRoute != ScreenRoute.SETTINGS) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.testTag("topbar_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Specular glass bottom divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            NeonCyan.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
