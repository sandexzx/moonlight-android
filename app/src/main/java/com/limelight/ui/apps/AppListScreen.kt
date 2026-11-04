package com.limelight.ui.apps

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ui.components.ContentMaxWidth
import com.limelight.ui.components.GlassCard
import com.limelight.ui.components.GlassIconButton
import com.limelight.ui.components.GlassScreen
import com.limelight.ui.components.IconAction
import com.limelight.ui.components.LargeTitle
import com.limelight.ui.components.LocalCardBackdrop
import com.limelight.ui.components.MenuAction
import com.limelight.ui.components.MenuAnchor
import com.limelight.ui.components.PrimaryButton
import com.limelight.ui.components.Spinner
import com.limelight.ui.components.TextAction
import com.limelight.ui.components.menuAnchor
import com.limelight.ui.components.rememberMenuAnchor
import com.limelight.ui.components.tappable
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style

private val AmbientIdle = Color(0xFF8DBBFF)
private val AmbientSession = Color(0xFF8BE3A8)

/** Root of the app list screen. */
@Composable
fun AppListApp(state: AppListState, actions: AppListActions) {
    GlassScreen(
        overlay = state.overlay,
        ambient = if (state.runningId != 0) AmbientSession else AmbientIdle,
    ) { padding ->
        AppListContent(state, actions, padding)
    }
}

@Composable
private fun AppListContent(state: AppListState, actions: AppListActions, padding: PaddingValues) {
    val session = state.runningApp
    val listed = state.listedApps
    val columnWidth = Modifier.widthIn(max = ContentMaxWidth)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            AppListHeader(state.pcName, actions, columnWidth)
        }
        when {
            !state.loaded -> item(key = "loading") {
                MessageCard(stringResource(R.string.applist_refresh_msg), busy = true, modifier = columnWidth)
            }
            session == null && listed.isEmpty() -> item(key = "empty") {
                MessageCard(stringResource(R.string.apps_empty), busy = false, modifier = columnWidth)
            }
        }
        if (session != null) {
            item(key = "session") {
                SessionCard(session, state, actions, Modifier.animateItem().then(columnWidth))
            }
        }
        if (listed.isNotEmpty()) {
            item(key = "apps") {
                AppsCard(listed, state, actions, Modifier.animateItem().then(columnWidth))
            }
        }
    }
}

@Composable
private fun AppListHeader(pcName: String, actions: AppListActions, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.apps_back),
            onClick = actions::onBack,
            backdrop = LocalCardBackdrop.current,
            iconSize = 30.dp,
        )
        LargeTitle(pcName, Modifier.weight(1f))
    }
}

/** Loading and empty states share one quiet card. */
@Composable
private fun MessageCard(text: String, busy: Boolean, modifier: Modifier = Modifier) {
    val style = Style
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            Spinner(style.accentText, size = 30.dp, stroke = 3.dp)
            Spacer(Modifier.height(16.dp))
        }
        Text(text, style = AppType.subheadline, color = style.textSecondary, textAlign = TextAlign.Center)
    }
}

/** What's on the PC right now, and the way back into it. */
@Composable
private fun SessionCard(
    app: AppItem,
    state: AppListState,
    actions: AppListActions,
    modifier: Modifier = Modifier,
) {
    val style = Style
    val context = LocalContext.current
    val anchor = rememberMenuAnchor()

    GlassCard(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppCover(state.art(app.id), width = 72.dp, height = 96.dp, corner = 16.dp)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.name,
                    style = AppType.title,
                    color = style.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(style.success),
                    )
                    Text(
                        stringResource(R.string.home_status_session),
                        style = AppType.footnote,
                        color = style.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            MoreButton(anchor) { openMenu(context, app, state, actions, anchor) }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = stringResource(R.string.home_resume),
            onClick = { actions.onResumeApp(app.id) },
            backdrop = LocalCardBackdrop.current,
        )
        Spacer(Modifier.height(6.dp))
        TextAction(
            stringResource(R.string.applist_menu_quit),
            onClick = { actions.onQuitApp(app.id) },
            color = style.destructive,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** One glass card holding every other app, iOS grouped list style. */
@Composable
private fun AppsCard(
    apps: List<AppItem>,
    state: AppListState,
    actions: AppListActions,
    modifier: Modifier = Modifier,
) {
    val divider = Style.textPrimary.copy(alpha = 0.08f)

    GlassCard(modifier = modifier, contentPadding = PaddingValues(vertical = 6.dp)) {
        apps.forEachIndexed { index, app ->
            if (index > 0) {
                // Starts at the text, as iOS separators do.
                Box(
                    Modifier
                        .padding(start = 78.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(divider),
                )
            }
            AppRow(app, state, actions)
        }
    }
}

@Composable
private fun AppRow(app: AppItem, state: AppListState, actions: AppListActions) {
    val style = Style
    val context = LocalContext.current
    val anchor = rememberMenuAnchor()
    val hidden = state.isHidden(app.id)
    val menu = { openMenu(context, app, state, actions, anchor) }

    Row(
        Modifier
            .fillMaxWidth()
            .tappable(
                onLongClick = menu,
                // A session is running: starting this one would end it, so offer the choice.
                onClick = { if (state.runningId != 0) menu() else actions.onStartApp(app.id) },
            )
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCover(
            state.art(app.id),
            width = 48.dp,
            height = 64.dp,
            corner = 11.dp,
            modifier = Modifier.graphicsLayer { alpha = if (hidden) 0.4f else 1f },
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.name,
                style = AppType.headline,
                color = if (hidden) style.textSecondary else style.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hidden) {
                Text(
                    stringResource(R.string.apps_hidden),
                    style = AppType.footnote,
                    color = style.textGhost,
                    maxLines = 1,
                )
            }
        }
        MoreButton(anchor, menu)
    }
}

@Composable
private fun MoreButton(anchor: MenuAnchor, onClick: () -> Unit) {
    IconAction(
        icon = Icons.Rounded.MoreHoriz,
        contentDescription = stringResource(R.string.home_more),
        onClick = onClick,
        modifier = Modifier.menuAnchor(anchor),
    )
}

/** Box art in portrait; a tinted tile while it loads or when the host has none. */
@Composable
private fun AppCover(
    art: ImageBitmap?,
    width: Dp,
    height: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    val style = Style
    Crossfade(
        targetState = art,
        label = "appCover",
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(corner)),
    ) { current ->
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(style.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = style.accent,
                    modifier = Modifier.size(width * 0.5f),
                )
            }
        }
    }
}

private fun openMenu(
    context: Context,
    app: AppItem,
    state: AppListState,
    actions: AppListActions,
    anchor: MenuAnchor,
) {
    state.overlay.showMenu(anchor.bounds, appMenu(context, app, state, actions), title = app.name)
}

/** The old context menu, minus Resume and Quit: the session card has those as buttons. */
private fun appMenu(
    context: Context,
    app: AppItem,
    state: AppListState,
    actions: AppListActions,
): List<MenuAction> = buildList {
    val id = app.id
    val running = state.runningId == id
    val hidden = state.isHidden(id)
    fun item(label: Int, icon: ImageVector, checked: Boolean = false, onClick: () -> Unit) {
        add(MenuAction(context.getString(label), icon, checked = checked, onClick = onClick))
    }

    if (state.runningId != 0 && !running) {
        item(R.string.applist_menu_quit_and_start, Icons.Rounded.SwapHoriz) { actions.onQuitAndStartApp(id) }
    }
    if (!running || hidden) {
        item(R.string.applist_menu_hide_app, Icons.Rounded.VisibilityOff, checked = hidden) {
            actions.onToggleHiddenApp(id)
        }
    }
    item(R.string.applist_menu_details, Icons.Rounded.Info) { actions.onShowAppDetails(id) }
    // A launcher shortcut needs an icon, so it waits for the art.
    if (state.art(id) != null) {
        item(R.string.applist_menu_scut, Icons.Rounded.PushPin) { actions.onCreateAppShortcut(id) }
    }
}
