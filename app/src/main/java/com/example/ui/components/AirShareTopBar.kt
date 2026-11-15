package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScreenRoute
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface

@Composable
fun AirShareTopBar(
    currentRoute: ScreenRoute,
    isAdvertising: Boolean,
    errorCount: Int = 0,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDiagnosticsClick: () -> Unit = {},
    onToggleAdvertising: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors

    val title = when (currentRoute) {
        ScreenRoute.SPLASH, ScreenRoute.ONBOARDING, ScreenRoute.HOME -> "GestoShare"
        ScreenRoute.CONTENT_SELECTION -> "Select Content"
        ScreenRoute.NEARBY_DEVICES -> "Nearby Devices"
        ScreenRoute.GESTURE_CONTROL -> "Gesture Radar"
        ScreenRoute.TRANSFER -> "Active Transfer"
        ScreenRoute.HISTORY -> "Transfer History"
        ScreenRoute.SETTINGS -> "Settings"
        ScreenRoute.DIAGNOSTICS -> "Diagnostics & Errors"
        ScreenRoute.ABOUT -> "About GestoShare"
    }

    val subtitle = when (currentRoute) {
        ScreenRoute.SPLASH, ScreenRoute.ONBOARDING, ScreenRoute.HOME -> "Peer-to-Peer Gesture File Sharing"
        ScreenRoute.CONTENT_SELECTION -> "Import files, photos, PDFs, notes & links"
        ScreenRoute.NEARBY_DEVICES -> "Multi-peer offline Nearby cluster"
        ScreenRoute.GESTURE_CONTROL -> "CameraX + MediaPipe + HaGRIDv2 ONNX"
        ScreenRoute.TRANSFER -> "Scoped-storage safe & SHA-256 verified"
        ScreenRoute.HISTORY -> "Local audit trail & cryptographic records"
        ScreenRoute.SETTINGS -> "Theme, sensitivity, hardware & logs"
        ScreenRoute.DIAGNOSTICS -> "Error reports, stack traces & TXT/JSON export"
        ScreenRoute.ABOUT -> "Offline architecture & AI pipeline info"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.topBarStart, palette.topBarEnd)
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
                                tint = palette.accentPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        GestoShareBrandMark(
                            size = 38.dp,
                            cornerRadius = 11.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = palette.textSecondary,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (errorCount > 0 && currentRoute != ScreenRoute.DIAGNOSTICS) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .glassSurface(
                                    cornerRadius = 16.dp,
                                    fillColor = palette.error.copy(alpha = 0.16f),
                                    borderAccent = palette.error.copy(alpha = 0.7f)
                                )
                                .clickable { onDiagnosticsClick() }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("topbar_diagnostics_badge"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Open Diagnostics",
                                tint = palette.error,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "$errorCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = palette.error
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .glassSurface(
                                cornerRadius = 20.dp,
                                fillColor = if (isAdvertising) palette.accentPrimary.copy(alpha = 0.20f) else palette.glassFill,
                                borderAccent = if (isAdvertising) palette.accentPrimary else palette.glassBorder
                            )
                            .clickable { onToggleAdvertising() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("toggle_receive_pill"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = "Receive Mode",
                                tint = if (isAdvertising) palette.accentPrimary else palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isAdvertising) "RECEIVING" else "STANDBY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdvertising) palette.accentPrimary else palette.textSecondary
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
                                tint = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            palette.accentPrimary.copy(alpha = 0.35f),
                            palette.glassBorder,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
