package com.limelight.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.limelight.ui.theme.AppBackground

/** Background + scrolling content: what floating bars, sheets and menus refract. */
val LocalBarBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppScaffold above") }

/** Background only: what glass cards inside the scrolling content refract. */
val LocalCardBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppScaffold above") }

/**
 * Screen frame shared by every screen: background, scrolling [content], and
 * floating glass [topBar] / [bottomBar] over it. [overlay] (sheets, menus, previews) is
 * drawn last, above the bars, and may use [LocalBarBackdrop].
 *
 * Bars are measured, and [content] receives padding that clears them and the system bars;
 * horizontal padding is left to the screen. The bottom bar rides on top of the keyboard.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    background: @Composable (Modifier) -> Unit = { AppBackground(it) },
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    overlay: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val barBackdrop = rememberLayerBackdrop()
    val cardBackdrop = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var topHeight by remember { mutableStateOf<Dp?>(null) }
    var bottomHeight by remember { mutableStateOf<Dp?>(null) }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(barBackdrop),
        ) {
            background(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(cardBackdrop),
            )
            // Content scrolled up fades out before the status bar, so the clock and icons
            // always sit on the bare background.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val clear = statusTop.toPx()
                        val opaque = clear + 20.dp.toPx()
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = clear, endY = opaque),
                            size = Size(size.width, opaque),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                CompositionLocalProvider(LocalCardBackdrop provides cardBackdrop) {
                    content(
                        PaddingValues(
                            top = (topHeight ?: statusTop) + 8.dp,
                            bottom = (bottomHeight ?: navBottom) + 24.dp,
                        ),
                    )
                }
            }
        }

        CompositionLocalProvider(
            LocalBarBackdrop provides barBackdrop,
            LocalCardBackdrop provides cardBackdrop,
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topHeight = with(density) { it.height.toDp() } }
                    .statusBarsPadding(),
            ) {
                topBar()
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                bottomBar()
            }
            overlay()
        }
    }
}

/**
 * Whole-screen frame: [AppScaffold] plus the overlay layer. The scaffold is the backdrop of that
 * layer, so menus and alerts refract the screen beneath them. [ambient] is the room's light,
 * eased when it changes.
 */
@Composable
fun GlassScreen(
    overlay: OverlayState,
    ambient: Color,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val appBackdrop = rememberLayerBackdrop()
    val light by animateColorAsState(targetValue = ambient, animationSpec = tween(700), label = "ambient")

    CompositionLocalProvider(LocalOverlay provides overlay) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(appBackdrop),
            ) {
                AppScaffold(
                    background = { AppBackground(it, light) },
                    bottomBar = bottomBar,
                    content = content,
                )
            }
            OverlayHost(overlay, appBackdrop)
        }
    }
}
