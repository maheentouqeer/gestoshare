package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Core Brand Accents
val NeonCyan = Color(0xFF00E5FF)
val CyanPrimary = Color(0xFF06B6D4)
val CyanDark = Color(0xFF0891B2)
val ElectricViolet = Color(0xFF818CF8)
val DeepViolet = Color(0xFF6366F1)
val EmeraldSuccess = Color(0xFF10B981)
val RoseError = Color(0xFFF43F5E)
val AmberAccent = Color(0xFFF59E0B)

// Legacy Dark Constants (preserved for backwards-compatible callers)
val DarkBackground = Color(0xFF060913)
val DarkSurface = Color(0xFF0F172A)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkSurfaceElevated = Color(0xFF273549)

val GlassBackdropDeep = Color(0xFF050814)
val GlassFill = Color(0x8E121B30)
val GlassFillElevated = Color(0xAA192642)
val GlassHighlight = Color(0x38FFFFFF)
val GlassBorderSubtle = Color(0x28FFFFFF)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val BorderSubtle = Color(0xFF1E293B)
val BorderGlow = Color(0x3300E5FF)

@Immutable
data class GestoThemeColors(
    val isDark: Boolean,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val glassFill: Color,
    val glassFillElevated: Color,
    val glassFillInset: Color,
    val glassBorder: Color,
    val glassHighlightTop: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val onAccentPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val topBarStart: Color,
    val topBarEnd: Color,
    val bottomBarStart: Color,
    val bottomBarEnd: Color,
    val dialogSurface: Color
)

val DarkGestoColors = GestoThemeColors(
    isDark = true,
    bgTop = Color(0xFF050814),
    bgMid = Color(0xFF091126),
    bgBottom = Color(0xFF060A17),
    orbPrimary = NeonCyan,
    orbSecondary = ElectricViolet,
    orbTertiary = CyanDark,
    glassFill = Color(0x94111B31),
    glassFillElevated = Color(0xB8182542),
    glassFillInset = Color(0x70070B18),
    glassBorder = Color(0x36FFFFFF),
    glassHighlightTop = Color(0x24FFFFFF),
    accentPrimary = NeonCyan,
    accentSecondary = ElectricViolet,
    onAccentPrimary = Color(0xFF03101F),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    success = EmeraldSuccess,
    warning = AmberAccent,
    error = RoseError,
    topBarStart = Color(0xDD0A1124),
    topBarEnd = Color(0xB20C162E),
    bottomBarStart = Color(0xBE0C152B),
    bottomBarEnd = Color(0xEC070C18),
    dialogSurface = Color(0xFF0F172A)
)

val LightGestoColors = GestoThemeColors(
    isDark = false,
    bgTop = Color(0xFFF0F6FF),
    bgMid = Color(0xFFE3EEFC),
    bgBottom = Color(0xFFECF3FC),
    orbPrimary = Color(0xFF0EA5E9),
    orbSecondary = Color(0xFF6366F1),
    orbTertiary = Color(0xFF14B8A6),
    glassFill = Color(0xCCFFFFFF),
    glassFillElevated = Color(0xEAFFFFFF),
    glassFillInset = Color(0xB2E2ECF8),
    glassBorder = Color(0xFFCBD5E1),
    glassHighlightTop = Color(0xB8FFFFFF),
    accentPrimary = Color(0xFF0284C7),
    accentSecondary = Color(0xFF4F46E5),
    onAccentPrimary = Color.White,
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    error = Color(0xFFE11D48),
    topBarStart = Color(0xE8F8FBFF),
    topBarEnd = Color(0xCCEAF2FC),
    bottomBarStart = Color(0xCCEBF3FC),
    bottomBarEnd = Color(0xF0F5F9FF),
    dialogSurface = Color(0xFFF8FAFC)
)

val LocalGestoColors = staticCompositionLocalOf { DarkGestoColors }
