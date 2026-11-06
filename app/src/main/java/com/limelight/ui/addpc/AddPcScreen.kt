package com.limelight.ui.addpc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ui.components.ContentMaxWidth
import com.limelight.ui.components.GlassCard
import com.limelight.ui.components.GlassIconButton
import com.limelight.ui.components.GlassScreen
import com.limelight.ui.components.LargeTitle
import com.limelight.ui.components.LocalCardBackdrop
import com.limelight.ui.components.PrimaryButton
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style

private val Ambient = Color(0xFF8DBBFF)

/** Root of the add PC screen. */
@Composable
fun AddPcApp(state: AddPcState, actions: AddPcActions) {
    // Leaving waits for the attempt, and its network calls can't be cut short: stay until it ends.
    BackHandler(enabled = state.busy) {}

    GlassScreen(overlay = state.overlay, ambient = Ambient) { padding ->
        AddPcContent(state, actions, padding)
    }
}

@Composable
private fun AddPcContent(state: AddPcState, actions: AddPcActions, padding: PaddingValues) {
    val style = Style
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val address = state.address.text.trim().toString()
    val columnWidth = Modifier.widthIn(max = ContentMaxWidth)

    fun submit() {
        if (address.isNotEmpty() && !state.busy) {
            keyboard?.hide()
            actions.onAdd(address)
        }
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

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
            // Stacked, not side by side: translations of this title need the whole width.
            Column(
                columnWidth.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.apps_back),
                    onClick = { if (!state.busy) actions.onBack() },
                    backdrop = LocalCardBackdrop.current,
                    iconSize = 30.dp,
                )
                LargeTitle(stringResource(R.string.title_add_pc), maxLines = 2)
            }
        }
        item(key = "address") {
            GlassCard(columnWidth, contentPadding = PaddingValues(0.dp)) {
                BasicTextField(
                    state = state.address,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    readOnly = state.busy,
                    textStyle = AppType.body.copy(color = style.textPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { submit() },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    cursorBrush = SolidColor(style.accent),
                    decorator = TextFieldDecorator { field ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.address.text.isEmpty()) {
                                Text(
                                    stringResource(R.string.ip_hint),
                                    style = AppType.body,
                                    color = style.textGhost,
                                    maxLines = 1,
                                )
                            }
                            field()
                        }
                    },
                )
            }
        }
        item(key = "add") {
            PrimaryButton(
                text = stringResource(R.string.addpc_action),
                onClick = { submit() },
                modifier = columnWidth,
                enabled = address.isNotEmpty(),
                busy = state.busy,
                backdrop = LocalCardBackdrop.current,
            )
        }
        if (state.busy) {
            item(key = "progress") {
                Text(
                    stringResource(R.string.msg_add_pc),
                    style = AppType.footnote,
                    color = style.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = columnWidth.fillMaxWidth(),
                )
            }
        }
    }
}
