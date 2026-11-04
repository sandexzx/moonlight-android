package com.limelight.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Immutable
data class GlassStyle(
    /** Milky layer drawn over the refracted backdrop. */
    val surface: Color,
    val blur: Dp,
    /** 0.dp disables refraction. Must stay below half the element's height. */
    val lensHeight: Dp = 0.dp,
    val lensAmount: Dp = 0.dp,
    val chromaticAberration: Boolean = false,
    /** Magnifies the middle of the lens, like a drop of water; for small round elements. */
    val depthEffect: Boolean = false,
    val vibrancy: Boolean = true,
    val shadowAlpha: Float = 0.08f,
)

/**
 * Liquid Glass surface: draws a refracted, blurred copy of [backdrop] behind [content].
 * Must not be placed inside the `layerBackdrop` source of the same [backdrop].
 *
 * [shape] should be a `com.kyant.shapes` shape (Capsule, RoundedRectangle): the lens
 * effect needs its corner radii. [tint] turns the surface into a colored button.
 */
@Composable
fun Glass(
    backdrop: Backdrop,
    style: GlassStyle,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "glassPressScale",
    )
    val shadow = remember(style.shadowAlpha) {
        Shadow(radius = 20.dp, color = Color.Black.copy(alpha = style.shadowAlpha))
    }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (style.vibrancy) vibrancy()
                    blur(style.blur.toPx())
                    lens(
                        refractionHeight = style.lensHeight.toPx(),
                        refractionAmount = style.lensAmount.toPx(),
                        depthEffect = style.depthEffect,
                        chromaticAberration = style.chromaticAberration,
                    )
                },
                shadow = { shadow },
                layerBlock = if (onClick != null) {
                    {
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.85f))
                    } else {
                        drawRect(style.surface)
                    }
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
