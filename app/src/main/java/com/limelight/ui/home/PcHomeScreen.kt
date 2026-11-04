package com.limelight.ui.home

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.limelight.R
import com.limelight.ui.components.AppScaffold
import com.limelight.ui.components.Glass
import com.limelight.ui.components.GlassCard
import com.limelight.ui.components.GlassIconButton
import com.limelight.ui.components.IconAction
import com.limelight.ui.components.LargeTitle
import com.limelight.ui.components.LocalBarBackdrop
import com.limelight.ui.components.LocalCardBackdrop
import com.limelight.ui.components.LocalOverlay
import com.limelight.ui.components.MenuAction
import com.limelight.ui.components.OverlayHost
import com.limelight.ui.components.OverlayState
import com.limelight.ui.components.PrimaryButton
import com.limelight.ui.components.Spinner
import com.limelight.ui.components.TextAction
import com.limelight.ui.components.menuAnchor
import com.limelight.ui.components.rememberMenuAnchor
import com.limelight.ui.theme.AppBackground
import com.limelight.ui.theme.AppStyle
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style

private val ContentMaxWidth = 520.dp

/**
 * Root of the computers screen. The whole scaffold is the backdrop of the overlay layer, so
 * menus and alerts refract the screen beneath them.
 */
@Composable
fun PcHomeApp(state: PcHomeState, actions: PcHomeActions) {
    val appBackdrop = rememberLayerBackdrop()
    val ambient by animateColorAsState(
        targetValue = ambientColor(state.computers.firstOrNull()?.status),
        animationSpec = tween(700),
        label = "ambient",
    )

    CompositionLocalProvider(LocalOverlay provides state.overlay) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(appBackdrop),
            ) {
                AppScaffold(
                    background = { AppBackground(it, ambient) },
                    bottomBar = { HomeTabBar(onOpenSettings = actions::onOpenSettings) },
                ) { padding ->
                    PcHomeContent(state, actions, padding)
                }
            }
            OverlayHost(state.overlay, appBackdrop)
        }
    }
}

/** The room's light follows the first PC, so the screen says "ready" before you read it. */
private fun ambientColor(status: PcStatus?): Color = when (status) {
    PcStatus.Ready, null -> Color(0xFF8DBBFF)
    PcStatus.Session -> Color(0xFF8BE3A8)
    PcStatus.NotPaired -> Color(0xFFFFCB8E)
    PcStatus.Offline, PcStatus.Checking -> Color(0xFFD6D6E0)
}

@Composable
private fun PcHomeContent(state: PcHomeState, actions: PcHomeActions, padding: PaddingValues) {
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
            HomeHeader(actions, Modifier.widthIn(max = ContentMaxWidth))
        }
        if (state.computers.isEmpty()) {
            item(key = "searching") {
                SearchingCard(actions, Modifier.widthIn(max = ContentMaxWidth))
            }
        }
        items(state.computers, key = { it.uuid }) { card ->
            PcHeroCard(
                card = card,
                connecting = state.connectingUuid == card.uuid,
                overlay = state.overlay,
                actions = actions,
                modifier = Modifier
                    .animateItem()
                    .widthIn(max = ContentMaxWidth),
            )
        }
    }
}

@Composable
private fun HomeHeader(actions: PcHomeActions, modifier: Modifier = Modifier) {
    val backdrop = LocalCardBackdrop.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LargeTitle(stringResource(R.string.home_title), Modifier.weight(1f))
        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = stringResource(R.string.help),
            onClick = actions::onHelp,
            backdrop = backdrop,
        )
        GlassIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.title_add_pc),
            onClick = actions::onAddPc,
            backdrop = backdrop,
        )
    }
}

/** Nothing found yet: discovery keeps running behind this card. */
@Composable
private fun SearchingCard(actions: PcHomeActions, modifier: Modifier = Modifier) {
    val style = Style
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spinner(style.accentText, size = 30.dp, stroke = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.searching_pc),
            style = AppType.subheadline,
            color = style.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextAction(stringResource(R.string.title_add_pc), onClick = actions::onAddPc)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PcHeroCard(
    card: PcCardModel,
    connecting: Boolean,
    overlay: OverlayState,
    actions: PcHomeActions,
    modifier: Modifier = Modifier,
) {
    val style = Style
    val context = LocalContext.current
    val anchor = rememberMenuAnchor()
    val tint by animateColorAsState(statusColor(style, card.status), label = "pcStatus")
    val openMenu = { overlay.showMenu(anchor.bounds, pcMenu(context, card, actions), title = card.name) }

    GlassCard(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = openMenu,
                        onLongClick = openMenu,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DesktopWindows, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        card.name,
                        style = AppType.title,
                        color = style.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tint),
                        )
                        Text(
                            stringResource(statusLabel(card.status)),
                            style = AppType.footnote,
                            color = style.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            IconAction(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.home_more),
                onClick = openMenu,
                modifier = Modifier.menuAnchor(anchor),
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryAction(card, connecting, actions)

        if (card.status == PcStatus.Ready || card.status == PcStatus.Session) {
            Spacer(Modifier.height(6.dp))
            TextAction(
                stringResource(R.string.home_apps),
                onClick = { actions.onOpenApps(card.uuid) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** The one thing to do with this PC right now. */
@Composable
private fun PrimaryAction(card: PcCardModel, connecting: Boolean, actions: PcHomeActions) {
    val backdrop = LocalCardBackdrop.current
    when (card.status) {
        PcStatus.Checking -> PrimaryButton(
            text = stringResource(R.string.home_connect),
            onClick = {},
            busy = true,
            backdrop = backdrop,
        )
        PcStatus.Offline -> PrimaryButton(
            text = stringResource(R.string.home_wake),
            onClick = { actions.onWake(card.uuid) },
            enabled = card.canWake,
            backdrop = backdrop,
        )
        PcStatus.NotPaired -> PrimaryButton(
            text = stringResource(R.string.home_pair),
            onClick = { actions.onPair(card.uuid) },
            backdrop = backdrop,
        )
        PcStatus.Ready -> PrimaryButton(
            text = stringResource(R.string.home_connect),
            onClick = { actions.onConnect(card.uuid) },
            busy = connecting,
            backdrop = backdrop,
        )
        PcStatus.Session -> PrimaryButton(
            text = stringResource(R.string.home_resume),
            onClick = { actions.onResume(card.uuid) },
            backdrop = backdrop,
        )
    }
}

private fun statusColor(style: AppStyle, status: PcStatus): Color = when (status) {
    PcStatus.Ready -> style.accent
    PcStatus.Session -> style.success
    PcStatus.NotPaired -> style.caution
    PcStatus.Offline, PcStatus.Checking -> style.textGhost
}

private fun statusLabel(status: PcStatus): Int = when (status) {
    PcStatus.Ready -> R.string.home_status_paired
    PcStatus.Session -> R.string.home_status_session
    PcStatus.NotPaired -> R.string.home_status_not_paired
    PcStatus.Offline -> R.string.pcview_menu_header_offline
    PcStatus.Checking -> R.string.pcview_menu_header_unknown
}

/** Same actions, in the same order, as the old context menu. */
private fun pcMenu(context: Context, card: PcCardModel, actions: PcHomeActions): List<MenuAction> = buildList {
    val uuid = card.uuid
    fun item(label: Int, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
        add(MenuAction(context.getString(label), icon, destructive, onClick = onClick))
    }

    when (card.status) {
        PcStatus.Checking, PcStatus.Offline ->
            item(R.string.pcview_menu_send_wol, Icons.Rounded.PowerSettingsNew) { actions.onWake(uuid) }
        PcStatus.NotPaired ->
            item(R.string.pcview_menu_pair_pc, Icons.Rounded.Link) { actions.onPair(uuid) }
        PcStatus.Ready, PcStatus.Session -> {
            if (card.status == PcStatus.Session) {
                item(R.string.applist_menu_resume, Icons.Rounded.PlayArrow) { actions.onResume(uuid) }
                item(R.string.applist_menu_quit, Icons.Rounded.Close) { actions.onQuitSession(uuid) }
            }
            item(R.string.pcview_menu_app_list, Icons.Rounded.Apps) { actions.onOpenAllApps(uuid) }
        }
    }
    if (card.isGameStream) {
        item(R.string.pcview_menu_eol, Icons.Rounded.Info) { actions.onGameStreamInfo() }
    }
    item(R.string.pcview_menu_test_network, Icons.Rounded.NetworkCheck) { actions.onTestNetwork() }
    item(R.string.pcview_menu_details, Icons.Rounded.Info) { actions.onShowDetails(uuid) }
    item(R.string.pcview_menu_delete_pc, Icons.Rounded.Delete, destructive = true) { actions.onDeletePc(uuid) }
}

/**
 * Floating glass tab bar. Settings is still a separate activity, so its tab only opens it and
 * Computers stays selected; it becomes a real tab when that screen moves to Compose.
 */
@Composable
private fun HomeTabBar(onOpenSettings: () -> Unit) {
    val style = Style
    val shape = remember { Capsule() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Glass(
            backdrop = LocalBarBackdrop.current,
            style = style.barGlass,
            shape = shape,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(236.dp)
                .height(64.dp),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .selectableGroup(),
            ) {
                TabItem(
                    title = stringResource(R.string.home_title),
                    icon = Icons.Rounded.DesktopWindows,
                    selected = true,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                TabItem(
                    title = stringResource(R.string.home_tab_settings),
                    icon = Icons.Rounded.Settings,
                    selected = false,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = Style
    val color = if (selected) style.accentText else style.textPrimary.copy(alpha = 0.72f)
    Column(
        modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(if (selected) style.textPrimary.copy(alpha = 0.06f) else Color.Transparent)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            title,
            style = AppType.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = color,
            maxLines = 1,
        )
    }
}
