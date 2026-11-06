package com.limelight.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style
import kotlin.math.roundToInt

private val RowMinHeight = 52.dp
private val ThumbSize = 27.dp

/** Uppercase caption above a group of rows, iOS grouped-list style. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppType.footnote.copy(letterSpacing = 0.6.sp),
        color = Style.textSecondary,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .semantics { heading() },
    )
}

/** Hairline between rows of a card, inset to the text. */
@Composable
fun RowDivider() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Style.textPrimary.copy(alpha = 0.08f)),
    )
}

/** Title with the optional explanation under it; takes the space the row's control leaves. */
@Composable
private fun RowScope.RowText(label: String, summary: String?, color: Color = Style.textPrimary) {
    Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = AppType.body, color = color)
        summary?.let { Text(it, style = AppType.footnote, color = Style.textSecondary) }
    }
}

/** Row whose whole width toggles [checked]. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(label, summary)
        Toggle(checked)
    }
}

/** iOS switch: the track fills with green as the thumb slides across. Display only, the row handles input. */
@Composable
private fun Toggle(checked: Boolean) {
    val style = Style
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "toggle",
    )
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(lerp(style.textPrimary.copy(alpha = 0.09f), style.success, progress))
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = progress * 20.dp.toPx() }
                .size(ThumbSize)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * Row showing the current choice of a list. [onClick] receives the row's root bounds for
 * [OverlayState.showMenu]. Choices can be long sentences, so the value wraps at the row's end.
 */
@Composable
fun PickerRow(
    label: String,
    value: String,
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val style = Style
    val anchor = rememberMenuAnchor()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .menuAnchor(anchor)
            .tappable { onClick(anchor.bounds) }
            .semantics { stateDescription = value }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RowText(label, summary)
        Text(
            value,
            style = AppType.subheadline,
            color = style.textSecondary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = style.textGhost, modifier = Modifier.size(18.dp))
    }
}

/** Row that leaves this screen. */
@Composable
fun NavRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .tappable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RowText(label, summary)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Style.textGhost,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Row that does something on tap and is red, because that something can't be undone. */
@Composable
fun DestructiveRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .tappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, summary, color = Style.destructive)
    }
}

/**
 * Title and live value above a [Slider]. Calls [onValueChange] once, on release, so a drag
 * doesn't write through every value it passes.
 */
@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    valueText: (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    summary: String? = null,
) {
    val style = Style
    var dragged by remember { mutableStateOf<Int?>(null) }
    val shown = dragged ?: value
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = AppType.body, color = style.textPrimary, modifier = Modifier.weight(1f))
            Text(
                valueText(shown),
                // Tabular digits, so the value doesn't jitter under the finger.
                style = AppType.subheadline.copy(fontFeatureSettings = "tnum"),
                color = style.textSecondary,
                maxLines = 1,
            )
        }
        Slider(
            value = shown,
            range = range,
            step = step,
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let(onValueChange)
                dragged = null
            },
        )
        summary?.let { Text(it, style = AppType.footnote, color = style.textSecondary) }
    }
}

/** iOS slider: thin track filled in accent up to a white thumb. Tap jumps, drag follows. */
@Composable
private fun Slider(
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = Style
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val fraction = ((value - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                setProgress { target ->
                    change(snapped(target, range, step))
                    finish()
                    true
                }
            }
            .pointerInput(range, step) {
                detectTapGestures { position ->
                    change(sliderValueAt(position.x, range, step))
                    finish()
                }
            }
            .pointerInput(range, step) {
                detectHorizontalDragGestures(
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { input, _ ->
                    input.consume()
                    change(sliderValueAt(input.position.x, range, step))
                }
            }
            .drawBehind {
                val y = size.height / 2
                val start = ThumbSize.toPx() / 2
                val end = size.width - start
                val track = 4.dp.toPx()
                drawLine(style.textPrimary.copy(alpha = 0.09f), Offset(start, y), Offset(end, y), track, StrokeCap.Round)
                drawLine(style.accent, Offset(start, y), Offset(start + (end - start) * fraction, y), track, StrokeCap.Round)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = (maxWidth - ThumbSize) * fraction)
                .size(ThumbSize)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** The thumb's center travels between half a thumb from either end. */
private fun PointerInputScope.sliderValueAt(x: Float, range: IntRange, step: Int): Int {
    val thumb = ThumbSize.toPx()
    val position = ((x - thumb / 2) / (size.width - thumb)).coerceIn(0f, 1f)
    return snapped(range.first + (range.last - range.first) * position, range, step)
}

private fun snapped(raw: Float, range: IntRange, step: Int): Int =
    (range.first + ((raw - range.first) / step).roundToInt() * step).coerceIn(range)
