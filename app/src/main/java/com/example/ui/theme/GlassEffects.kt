package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Full-screen luminous backdrop featuring a deep midnight obsidian gradient
 * and soft cyan/violet ambient orbs so frosted glass surfaces above it pick up
 * authentic depth and translucency without hurting 60fps or 30fps CameraX performance.
 */
@Composable
fun GestoGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050814),
                        Color(0xFF091126),
                        Color(0xFF060A17)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-left cyan atmospheric orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.22f),
                        NeonCyan.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.14f, h * 0.12f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.14f, h * 0.12f)
            )

            // Mid-right electric violet orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricViolet.copy(alpha = 0.24f),
                        ElectricViolet.copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.88f, h * 0.44f),
                    radius = w * 0.60f
                ),
                radius = w * 0.60f,
                center = Offset(w * 0.88f, h * 0.44f)
            )

            // Bottom-left teal glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CyanDark.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.22f, h * 0.86f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.22f, h * 0.86f)
            )
        }

        content()
    }
}

/**
 * Applies a frosted translucent glass fill + vertical specular sheen + luminous
 * top-left to bottom-right hairline border.
 */
fun Modifier.glassSurface(
    cornerRadius: Dp = 20.dp,
    fillColor: Color = GlassFill,
    borderAccent: Color = GlassBorderSubtle,
    borderWidth: Dp = 1.dp
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(fillColor, shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.09f),
                    Color.White.copy(alpha = 0.02f)
                )
            ),
            shape
        )
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f),
                    borderAccent,
                    Color.White.copy(alpha = 0.06f)
                )
            ),
            shape = shape
        )
}

/**
 * Reusable frosted glass Material 3 Card with translucent surface and specular edge border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fillColor: Color = GlassFill,
    borderAccent: Color = NeonCyan.copy(alpha = 0.26f),
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Card(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.07f),
                        Color.Transparent
                    )
                ),
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = fillColor),
        border = BorderStroke(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.25f),
                    borderAccent,
                    Color.White.copy(alpha = 0.06f)
                )
            )
        ),
        content = content
    )
}
