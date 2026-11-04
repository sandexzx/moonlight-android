package com.limelight.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style

/**
 * Round glass button: "+", help. Defaults to [LocalBarBackdrop], which is right for a
 * scaffold's top or bottom bar; inside scrolling content pass [LocalCardBackdrop].
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalBarBackdrop.current,
    tint: Color = Style.textPrimary,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
) {
    val shape = remember { Capsule() }
    Glass(
        backdrop = backdrop,
        style = Style.buttonGlass,
        shape = shape,
        onClick = onClick,
        modifier = modifier
            .size(size)
            .semantics { role = Role.Button },
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Main action, accent glass capsule across the width. While [busy] the label gives way to a
 * spinner and taps are ignored.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    backdrop: Backdrop = LocalBarBackdrop.current,
) {
    val style = Style
    val shape = remember { Capsule() }
    val active = enabled && !busy
    Glass(
        backdrop = backdrop,
        style = style.buttonGlass,
        shape = shape,
        tint = style.accent,
        onClick = if (active) onClick else null,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { alpha = if (enabled || busy) 1f else 0.4f }
            .semantics { role = Role.Button },
    ) {
        if (busy) {
            Spinner(Color.White, size = 22.dp)
        } else {
            Text(text, style = AppType.headline, color = Color.White, maxLines = 1)
        }
    }
}

/** Indeterminate progress: a fading arc that spins. */
@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, stroke: Dp = 2.5.dp) {
    val spin = rememberInfiniteTransition(label = "spinner")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(size)) {
        val width = stroke.toPx()
        rotate(angle) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, color)),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(width / 2, width / 2),
                size = Size(this.size.width - width, this.size.height - width),
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
    }
}

/** Bare icon button for rows and headers: 40dp target, dims on press. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Style.textSecondary,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier
            .size(40.dp)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
