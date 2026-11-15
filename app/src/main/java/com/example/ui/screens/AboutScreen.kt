package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.ScreenRoute
import com.example.ui.components.GestoShareBrandMark
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme

@Composable
fun AboutScreen(
    onNavigate: (ScreenRoute) -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("about_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = palette.accentPrimary.copy(alpha = 0.5f),
                borderWidth = 1.5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GestoShareBrandMark(size = 84.dp, cornerRadius = 22.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "GestoShare",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.accentPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassStatusBadge(label = "OFFLINE FIRST", color = palette.success)
                        GlassStatusBadge(label = "P2P_CLUSTER", color = palette.accentSecondary)
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = palette.success, modifier = Modifier.size(18.dp))
                        Text("Offline Privacy Guarantee", fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                    Text(
                        text = "GestoShare runs entirely on-device without cloud storage, user accounts, or analytics trackers. Hand landmarks stay in local memory, and diagnostic logs redact sensitive codes before saving locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
                        Text("On-Device AI Gesture Pipeline", fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                    Text(
                        text = "CameraX ImageAnalysis -> MediaPipe HandLandmarker (21 3D keypoints) -> Preprocessing Normalization [1, 63] -> HaGRIDv2 RBF SVM exported to ONNX Runtime -> TemporalGestureStabilizer (LIKE, FIST, POINT, THREE_GUN, STOP).",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(18.dp))
                        Text("Scoped Storage & Integrity Architecture", fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                    Text(
                        text = "Incoming Nearby Connections FILE payloads are copied strictly via ContentResolver.openInputStream(uri) to app-private storage, verified byte-for-byte against SHA-256 checksums, and exported to Downloads/GestoShare via MediaStore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    onClick = { onNavigate(ScreenRoute.DIAGNOSTICS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("about_open_diagnostics_button")
                ) {
                    Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Diagnostics & Error Center", fontWeight = FontWeight.Bold)
                }

                GlassOutlinedButton(
                    onClick = onReplayOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("about_replay_onboarding_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Replay Onboarding Walkthrough", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
