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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Full-screen luminous atmospheric backdrop that adapts seamlessly between
 * Midnight Obsidian Glass (dark) and Frosted Ice Glass (light).
 */
@Composable
fun GestoGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = GestoTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.bgTop, palette.bgMid, palette.bgBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alphaScale = if (palette.isDark) 1f else 0.72f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.orbPrimary.copy(alpha = 0.22f * alphaScale),
                        palette.orbPrimary.copy(alpha = 0.06f * alphaScale),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.14f, h * 0.12f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.14f, h * 0.12f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.orbSecondary.copy(alpha = 0.24f * alphaScale),
                        palette.orbSecondary.copy(alpha = 0.07f * alphaScale),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.88f, h * 0.44f),
                    radius = w * 0.60f
                ),
                radius = w * 0.60f,
                center = Offset(w * 0.88f, h * 0.44f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.orbTertiary.copy(alpha = 0.18f * alphaScale),
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
 * Applies a theme-aware frosted translucent glass fill + vertical specular sheen + luminous
 * top-left to bottom-right hairline border. Uses [Modifier.composed] so even existing calls
 * automatically adapt to Dark/Light theme when custom colors aren't specified.
 */
fun Modifier.glassSurface(
    cornerRadius: Dp = 20.dp,
    fillColor: Color? = null,
    borderAccent: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val palette = GestoTheme.colors
    val resolvedFill = when {
        fillColor == null -> palette.glassFill
        // Map legacy hardcoded dark GlassFill constants cleanly when switching to light mode
        !palette.isDark && (fillColor == GlassFill || fillColor == GlassFillElevated || fillColor.value == 0x66060B18UL || fillColor.value == 0x66060913UL || fillColor.value == 0x88060B18UL) ->
            palette.glassFillInset
        else -> fillColor
    }
    val resolvedBorder = borderAccent ?: palette.glassBorder
    val shape = RoundedCornerShape(cornerRadius)
    this
        .clip(shape)
        .background(resolvedFill, shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    if (palette.isDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.45f),
                    if (palette.isDark) Color.White.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.12f)
                )
            ),
            shape
        )
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    if (palette.isDark) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.9f),
                    resolvedBorder,
                    if (palette.isDark) Color.White.copy(alpha = 0.06f) else palette.glassBorder.copy(alpha = 0.5f)
                )
            ),
            shape = shape
        )
}

/**
 * Reusable frosted glass Material 3 Card that adapts between Dark Glass & Light Ice Glass.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fillColor: Color? = null,
    borderAccent: Color? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = GestoTheme.colors
    val resolvedFill = when {
        fillColor == null -> palette.glassFill
        !palette.isDark && (fillColor == GlassFill || fillColor == GlassFillElevated) -> palette.glassFillElevated
        else -> fillColor
    }
    val resolvedBorder = borderAccent ?: palette.accentPrimary.copy(alpha = if (palette.isDark) 0.28f else 0.32f)
    val shape = RoundedCornerShape(cornerRadius)

    Card(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (palette.isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.55f),
                    Color.Transparent
                )
            ),
            shape = shape
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = resolvedFill),
        border = BorderStroke(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    if (palette.isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.92f),
                    resolvedBorder,
                    if (palette.isDark) Color.White.copy(alpha = 0.06f) else palette.glassBorder.copy(alpha = 0.6f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (palette.isDark) 0.dp else 2.dp
        ),
        content = content
    )
}
