package com.limelight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Material is only the host for Text and Icon; every color the screen draws comes from [Style].
// One fixed light scheme, no dynamic color.
private val AppColorScheme = lightColorScheme(
    primary = LightGlass.accent,
    onPrimary = Color.White,
    background = BackgroundBase,
    onBackground = LightGlass.textPrimary,
    surface = BackgroundBase,
    onSurface = LightGlass.textPrimary,
    onSurfaceVariant = LightGlass.textSecondary,
    error = LightGlass.destructive,
    onError = Color.White,
)

@Composable
fun MoonlightTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppStyle provides LightGlass) {
        MaterialTheme(colorScheme = AppColorScheme, content = content)
    }
}
