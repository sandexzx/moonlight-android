package com.limelight.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Immutable
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    /** Shows a check mark: the current choice of a picker menu. */
    val checked: Boolean = false,
    val onClick: () -> Unit,
)

/** [anchor] is in root coordinates; the menu opens below it (or above), aligned to its end. */
@Immutable
data class MenuRequest(val anchor: Rect, val actions: List<MenuAction>, val title: String? = null)

@Immutable
data class AlertRequest(
    val title: String,
    val message: String? = null,
    /** Left-aligned and scrollable, for text that may not fit: PC details. */
    val longMessage: Boolean = false,
    /** Large line under the message, for something to read off the screen: the pairing PIN. */
    val emphasis: String? = null,
    /** Small print under [emphasis]. */
    val footnote: String? = null,
    val confirmLabel: String,
    val destructive: Boolean = false,
    /** Null leaves a single button. */
    val cancelLabel: String? = null,
    val onCancel: () -> Unit = {},
    val onConfirm: () -> Unit,
)

@Immutable
data class ToastMessage(val text: String, val id: Long)

/**
 * App-wide floating layers: context menus, alerts and toasts. One instance lives at the app
 * root ([LocalOverlay]); any screen can open them without owning a window of its own.
 */
@Stable
class OverlayState {
    var menu by mutableStateOf<MenuRequest?>(null)
        private set
    var alert by mutableStateOf<AlertRequest?>(null)
        private set
    var toast by mutableStateOf<ToastMessage?>(null)
        private set
    private var toastIds = 0L

    fun showMenu(anchor: Rect, actions: List<MenuAction>, title: String? = null) {
        if (actions.isNotEmpty()) menu = MenuRequest(anchor, actions, title)
    }

    fun dismissMenu() {
        menu = null
    }

    fun showAlert(request: AlertRequest) {
        menu = null
        alert = request
    }

    fun dismissAlert() {
        alert = null
    }

    /** Short message at the top of the screen; hides by itself. */
    fun toast(text: String) {
        toast = ToastMessage(text, ++toastIds)
    }

    internal fun dismissToast(id: Long) {
        if (toast?.id == id) toast = null
    }
}

val LocalOverlay = staticCompositionLocalOf<OverlayState> { error("No OverlayHost above") }

/** Remembers where an element sits, so a menu can open next to it. */
class MenuAnchor {
    internal var coordinates: LayoutCoordinates? = null

    /** Current bounds in root coordinates; read at tap time, after scrolling. */
    val bounds: Rect
        get() = coordinates?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
}

@Composable
fun rememberMenuAnchor(): MenuAnchor = remember { MenuAnchor() }

fun Modifier.menuAnchor(anchor: MenuAnchor): Modifier = onPlaced { anchor.coordinates = it }

/**
 * Draws the [state]'s menu, alert and toast over the whole app. Place it last in the root,
 * outside the `layerBackdrop` source of [backdrop] (the app content), so the glass refracts
 * the screen beneath.
 */
@Composable
fun OverlayHost(state: OverlayState, backdrop: Backdrop, modifier: Modifier = Modifier) {
    BackHandler(enabled = state.menu != null || state.alert != null) {
        val alert = state.alert
        if (alert != null) {
            state.dismissAlert()
            alert.onCancel()
        } else {
            state.dismissMenu()
        }
    }
    Box(modifier.fillMaxSize()) {
        MenuLayer(state, backdrop)
        AlertLayer(state, backdrop)
        ToastLayer(state, backdrop)
    }
}

/**
 * Enter/exit progress of a layer, 0 = gone. Returns null when nothing should be composed;
 * otherwise the request to draw, which outlives its dismissal until the exit animation ends.
 */
@Composable
private fun <T : Any> rememberLayer(request: T?, progress: Animatable<Float, *>): T? {
    // Plain holder, not state: written during composition only to remember what to draw on exit.
    val last = remember { arrayOfNulls<Any>(1) }
    if (request != null) last[0] = request
    LaunchedEffect(request != null) {
        if (request != null) {
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 600f))
        } else {
            progress.animateTo(0f, tween(150))
            last[0] = null
        }
    }
    val animating by remember { derivedStateOf { progress.value > 0f } }
    @Suppress("UNCHECKED_CAST")
    return if (request != null || animating) (request ?: last[0] as T?) else null
}

@Composable
private fun MenuLayer(state: OverlayState, backdrop: Backdrop) {
    val progress = remember { Animatable(0f) }
    val request = state.menu
    val current = rememberLayer(request, progress) ?: return
    val insets = WindowInsets.safeDrawing

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.08f * progress.value.coerceIn(0f, 1f))) }
            .then(
                if (request != null) {
                    Modifier.pointerInput(Unit) { detectTapGestures(onPress = { state.dismissMenu() }) }
                } else {
                    Modifier
                },
            ),
    )
    Layout(
        content = {
            MenuPanel(current, backdrop) { action ->
                state.dismissMenu()
                action.onClick()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val left = insets.getLeft(this, layoutDirection) + margin
        val right = constraints.maxWidth - insets.getRight(this, layoutDirection) - margin
        val top = insets.getTop(this) + margin
        val bottom = constraints.maxHeight - insets.getBottom(this) - margin
        val width = 250.dp.roundToPx().coerceAtMost((right - left).coerceAtLeast(0))
        val placeable = measurables.first().measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = (bottom - top).coerceAtLeast(0)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val origin = coordinates?.positionInRoot() ?: Offset.Zero
            val anchor = current.anchor.translate(-origin)
            val x = (anchor.right.roundToInt() - width).coerceIn(left, (right - width).coerceAtLeast(left))
            val below = anchor.bottom.roundToInt() + gap
            val opensDown = below + placeable.height <= bottom
            val y = if (opensDown) below else (anchor.top.roundToInt() - gap - placeable.height).coerceAtLeast(top)
            val pivot = TransformOrigin(
                pivotFractionX = if (width > 0) ((anchor.center.x - x) / width).coerceIn(0f, 1f) else 1f,
                pivotFractionY = if (opensDown) 0f else 1f,
            )
            placeable.placeWithLayer(x, y) {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                scaleX = 0.55f + 0.45f * p
                scaleY = 0.55f + 0.45f * p
                transformOrigin = pivot
            }
        }
    }
}

@Composable
private fun MenuPanel(request: MenuRequest, backdrop: Backdrop, onAction: (MenuAction) -> Unit) {
    val style = Style
    val shape = remember { RoundedRectangle(24.dp) }
    val showChecks = request.actions.any { it.checked }
    Glass(
        backdrop = backdrop,
        style = style.popupGlass,
        shape = shape,
        contentAlignment = Alignment.TopStart,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            request.title?.let { title ->
                Text(
                    title,
                    style = AppType.footnote,
                    color = style.textSecondary,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 4.dp),
                )
            }
            request.actions.forEach { action ->
                MenuItem(action, showChecks) { onAction(action) }
            }
        }
    }
}

@Composable
private fun MenuItem(action: MenuAction, showChecks: Boolean, onClick: () -> Unit) {
    val style = Style
    val color = if (action.destructive) style.destructive else style.textPrimary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) style.textPrimary.copy(alpha = 0.07f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showChecks) {
            Box(Modifier.width(18.dp)) {
                if (action.checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Text(action.label, style = AppType.body, color = color, modifier = Modifier.weight(1f), maxLines = 2)
        action.icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun AlertLayer(state: OverlayState, backdrop: Backdrop) {
    val progress = remember { Animatable(0f) }
    val request = state.alert
    val current = rememberLayer(request, progress) ?: return
    val style = Style
    val shape = remember { RoundedRectangle(30.dp) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.2f * progress.value.coerceIn(0f, 1f))) }
            .then(
                // Modal: taps outside do nothing, but must not reach the screen below.
                if (request != null) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Glass(
            backdrop = backdrop,
            style = style.popupGlass,
            shape = shape,
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .graphicsLayer {
                    val p = progress.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 1.1f - 0.1f * p
                    scaleY = 1.1f - 0.1f * p
                }
                .padding(horizontal = 24.dp)
                .widthIn(max = 300.dp),
        ) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(current.title, style = AppType.headline, color = style.textPrimary, textAlign = TextAlign.Center)
                current.message?.let {
                    Spacer(Modifier.height(6.dp))
                    if (current.longMessage) {
                        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
                        Text(
                            it,
                            style = AppType.footnote,
                            color = style.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight)
                                .verticalScroll(rememberScrollState()),
                        )
                    } else {
                        Text(it, style = AppType.footnote, color = style.textPrimary, textAlign = TextAlign.Center)
                    }
                }
                current.emphasis?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, style = AppType.pin, color = style.textPrimary, textAlign = TextAlign.Center)
                }
                current.footnote?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, style = AppType.footnote, color = style.textSecondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    current.cancelLabel?.let { label ->
                        AlertButton(
                            label = label,
                            fill = style.textPrimary.copy(alpha = 0.07f),
                            textColor = style.textPrimary,
                            bold = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            state.dismissAlert()
                            current.onCancel()
                        }
                    }
                    AlertButton(
                        label = current.confirmLabel,
                        fill = if (current.destructive) style.destructive else style.accent,
                        textColor = Color.White,
                        bold = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        state.dismissAlert()
                        current.onConfirm()
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertButton(
    label: String,
    fill: Color,
    textColor: Color,
    bold: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(fill)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (bold) AppType.headline else AppType.body,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun ToastLayer(state: OverlayState, backdrop: Backdrop) {
    val toast = state.toast
    val last = remember { arrayOfNulls<ToastMessage>(1) }
    if (toast != null) last[0] = toast
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(2_200)
            state.dismissToast(toast.id)
        }
    }
    val shape = remember { Capsule() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { -it },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 2 },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp),
        ) {
            val shown = last[0] ?: return@AnimatedVisibility
            Glass(
                backdrop = backdrop,
                style = Style.popupGlass,
                shape = shape,
                onClick = { state.dismissToast(shown.id) },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    shown.text,
                    style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = Style.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
