package com.limelight.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

/** Hosts a Compose screen edge to edge, with dark system bar icons over the light background. */
fun ComponentActivity.setGlassContent(content: @Composable () -> Unit) {
    val bars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    setContent {
        MoonlightTheme(content)
    }
}

@Composable
fun MoonlightTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppStyle provides LightGlass) {
        MaterialTheme(colorScheme = AppColorScheme, content = content)
    }
}
