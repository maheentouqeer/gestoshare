package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.components.GestoShareBrandMark
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors

    LaunchedEffect(Unit) {
        delay(900L)
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_ring_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(24.dp)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    palette.accentPrimary.copy(alpha = 0.28f),
                                    palette.accentSecondary.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            palette.accentPrimary.copy(alpha = 0.32f),
                            CircleShape
                        )
                )
                GestoShareBrandMark(
                    size = 104.dp,
                    cornerRadius = 28.dp
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "GestoShare",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Gesture-Controlled Offline P2P Sharing",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.accentPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(18.dp))

            GlassStatusBadge(
                label = "OFFLINE AI + NEARBY P2P · v${BuildConfig.VERSION_NAME}",
                color = palette.accentSecondary
            )
        }

        TextButton(
            onClick = onFinished,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .glassSurface(
                    cornerRadius = 14.dp,
                    fillColor = palette.glassFill,
                    borderAccent = palette.glassBorder
                )
                .testTag("splash_continue_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Tap to Continue",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
            )
        }
    }
}
