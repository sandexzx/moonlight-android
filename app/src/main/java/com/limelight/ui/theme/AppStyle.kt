package com.limelight.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.limelight.ui.components.GlassStyle

/**
 * Liquid Glass design tokens: milky white glass over a neutral light-gray background,
 * iOS system colors. Light only, same family as the owner's other apps.
 */
@Immutable
data class AppStyle(
    /** Fills: primary buttons. */
    val accent: Color,
    /** Accent used for text and icons. */
    val accentText: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textGhost: Color,
    val cardGlass: GlassStyle,
    val barGlass: GlassStyle,
    val cardCorner: Dp = 26.dp,
    /** Online, session running. */
    val success: Color,
    /** Needs attention but works: a PC that is not paired yet. */
    val caution: Color,
    val destructive: Color,
    /** Small round glass buttons: "+", help. */
    val buttonGlass: GlassStyle = GlassStyle(
        surface = Color(0xA6FFFFFF),
        blur = 4.dp,
        lensHeight = 10.dp,
        lensAmount = 22.dp,
        chromaticAberration = true,
        shadowAlpha = 0.10f,
    ),
    /** Menus, alerts and toasts floating over everything. */
    val popupGlass: GlassStyle = GlassStyle(
        surface = Color(0xD1FFFFFF),
        blur = 24.dp,
        lensHeight = 12.dp,
        lensAmount = 24.dp,
        shadowAlpha = 0.16f,
    ),
)

// iOS light system colors: label, secondaryLabel, systemBlue, systemGreen, systemOrange, systemRed.
// Text accent and red are a shade deeper than the system fills so small text stays readable.
val LightGlass = AppStyle(
    accent = Color(0xFF007AFF),
    accentText = Color(0xFF0066D6),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    textGhost = Color(0x6B3C3C43),
    cardGlass = GlassStyle(
        surface = Color(0x8CFFFFFF),
        blur = 16.dp,
        lensHeight = 10.dp,
        lensAmount = 16.dp,
        shadowAlpha = 0.05f,
    ),
    barGlass = GlassStyle(
        surface = Color(0x4DFFFFFF),
        blur = 3.dp,
        lensHeight = 16.dp,
        lensAmount = 36.dp,
        chromaticAberration = true,
    ),
    success = Color(0xFF34C759),
    caution = Color(0xFFFF9500),
    destructive = Color(0xFFE5342A),
)

val LocalAppStyle = staticCompositionLocalOf { LightGlass }

/** Shortcut for the app style inside composables. */
val Style: AppStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalAppStyle.current
