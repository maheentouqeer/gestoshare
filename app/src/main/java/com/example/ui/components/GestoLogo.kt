package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface

/**
 * Custom GestoShare brand emblem combining the generated adaptive icon artwork
 * with a luminous specular glass frame, plus a vector fallback/mark for splash & top bar.
 */
@Composable
fun GestoShareBrandMark(
    size: Dp = 54.dp,
    cornerRadius: Dp = 15.dp,
    useRasterArtwork: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .glassSurface(
                cornerRadius = cornerRadius,
                fillColor = palette.glassFillElevated,
                borderAccent = palette.accentPrimary.copy(alpha = 0.72f),
                borderWidth = 1.5.dp
            )
            .border(1.dp, palette.accentPrimary.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (useRasterArtwork) {
            Image(
                painter = painterResource(id = R.drawable.gestoshare_logo_1794726566963),
                contentDescription = "GestoShare Brand Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val cx = w / 2f
                val cy = h / 2f

                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            palette.accentPrimary.copy(alpha = 0.25f),
                            palette.accentSecondary.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    radius = w * 0.48f,
                    center = Offset(cx, cy)
                )

                // Signal arcs
                drawArc(
                    color = palette.accentPrimary,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.32f, cy - h * 0.32f),
                    size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
                    style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                )

                // Dynamic air-transfer chevron
                val arrow = Path().apply {
                    moveTo(cx - w * 0.20f, cy + h * 0.14f)
                    lineTo(cx + w * 0.04f, cy - h * 0.14f)
                    lineTo(cx + w * 0.04f, cy - h * 0.02f)
                    lineTo(cx + w * 0.22f, cy - h * 0.18f)
                }
                drawPath(
                    path = arrow,
                    color = palette.accentSecondary,
                    style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
                )
            }
        }
    }
}
