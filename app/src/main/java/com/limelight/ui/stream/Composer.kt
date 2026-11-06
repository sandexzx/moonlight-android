package com.limelight.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.Style

private const val ComposerMaxLines = 4
private val FieldVerticalPadding = 9.dp

/**
 * A real text field on the phone, so the keyboard's prediction and swipe work as in any app and
 * nothing reaches the PC until it's sent. Send and the PC's Enter share the last slot, so a
 * double tap sends and then presses Enter, the same as the keyboard's own key does.
 */
@Composable
internal fun Composer(state: KeyBarState) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shortcuts = remember(state) { ShortcutCapture(state) }
    val empty = state.draft.text.isEmpty()
    val sending = state.sendProgress != null

    LaunchedEffect(state.composerOpen) {
        if (state.composerOpen) {
            focus.requestFocus()
            keyboard?.show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            state = state.draft,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
            inputTransformation = shortcuts,
            textStyle = AppType.body.copy(color = Style.textPrimary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send,
            ),
            onKeyboardAction = { state.sendDraft() },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = ComposerMaxLines),
            cursorBrush = SolidColor(Style.accent),
            decorator = TextFieldDecorator { field ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = KeyHeight)
                        .clip(RoundedCornerShape(KeyCorner))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = FieldVerticalPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (empty) {
                        Text(
                            text = "Type here, then send",
                            style = AppType.body,
                            color = Style.textGhost,
                            maxLines = 1,
                        )
                    }
                    field()
                }
            },
        )
        if (empty) {
            KeyCap(
                label = if (sending) "Stop sending" else BarKey.Backspace.label,
                icon = if (sending) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Backspace,
                repeats = !sending,
                onTap = { if (sending) state.stopSending() else state.tapKey(BarKey.Backspace) },
                modifier = Modifier.width(ScrollingKeyWidth),
            )
        }
        KeyCap(
            label = if (empty) BarKey.Enter.label else "Send to PC",
            icon = if (empty) Icons.AutoMirrored.Rounded.KeyboardReturn else Icons.Rounded.ArrowUpward,
            look = if (empty) KeyLook.Plain else KeyLook.Filled,
            onTap = state::sendDraft,
            modifier = Modifier.width(ScrollingKeyWidth),
        )
    }
}

/** With Ctrl, Alt or Win latched, a typed character is a shortcut for the PC, not draft text. */
private class ShortcutCapture(private val state: KeyBarState) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val added = length - originalText.length
        val end = selection.end
        if (added <= 0 || end < added || !state.isShortcutHeld) return

        val typed = asCharSequence().subSequence(end - added, end).toString()
        revertAllChanges()
        if (typed.codePointCount(0, typed.length) == 1) {
            state.pressShortcut(typed.codePointAt(0))
        }
    }
}
