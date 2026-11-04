package com.limelight.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified

/** iOS systemGroupedBackground; base tone of [AppBackground] and the window background. */
val BackgroundBase = Color(0xFFF2F2F7)

/**
 * Static backdrop behind the content; the glass refracts and blurs it. Neutral light gray with
 * two barely-there cool glows. [ambient] adds a stronger colored light behind the top of the
 * screen: with so little content there, the glass needs color to bend or it reads as flat white.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, ambient: Color = Color.Unspecified) {
    Canvas(modifier) {
        drawRect(BackgroundBase)
        glow(Color(0xFFDDE7FA), Offset(size.width * 0.05f, size.height * 0.04f), size.width * 0.95f)
        glow(Color(0xFFE7E3F6), Offset(size.width * 0.98f, size.height * 0.62f), size.width * 0.90f)
        if (ambient.isSpecified) {
            glow(ambient, Offset(size.width * 0.88f, size.height * 0.14f), size.width * 0.80f)
            glow(ambient, Offset(size.width * 0.08f, size.height * 0.36f), size.width * 0.72f, strength = 0.65f)
        }
    }
}

/** A soft color spot: a radial gradient fading to transparent reads as a blurred circle. */
private fun DrawScope.glow(color: Color, center: Offset, radius: Float, strength: Float = 1f) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.7f * strength),
                color.copy(alpha = 0.25f * strength),
                color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
