package com.limelight.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style

/** Screens stay a readable column on wide or landscape windows. */
val ContentMaxWidth = 520.dp

/** iOS-style tap feedback: dims while pressed instead of a ripple. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tappable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.45f else 1f, label = "tapAlpha")
    this
        .graphicsLayer { this.alpha = alpha }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** Content card: frosted glass refracting the background. Use inside [AppScaffold] content. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = Style
    val shape = remember(style.cardCorner) { RoundedRectangle(style.cardCorner) }
    Glass(
        backdrop = LocalCardBackdrop.current,
        style = style.cardGlass,
        shape = shape,
        contentAlignment = Alignment.TopStart,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * [GlassCard]'s surface and corners without the glass. For long lists of tall cards: glass is
 * recomputed over its whole area on every scroll frame, and it shows little over a soft background.
 */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = Style
    val shape = remember(style.cardCorner) { RoundedRectangle(style.cardCorner) }
    Column(
        modifier
            .fillMaxWidth()
            .background(style.cardGlass.surface, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** iOS large navigation title; scrolls with the content. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        title,
        style = AppType.largeTitle,
        color = Style.textPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics { heading() },
    )
}

/** Accent-colored text button without a background, iOS style. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Style.accentText,
    textStyle: TextStyle = AppType.subheadline,
) {
    Text(
        text,
        style = textStyle,
        color = color,
        modifier = modifier
            .tappable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
