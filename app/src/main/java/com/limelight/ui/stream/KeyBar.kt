package com.limelight.ui.stream

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.ui.theme.AppType
import com.limelight.ui.theme.BackgroundBase
import com.limelight.ui.theme.MoonlightTheme
import com.limelight.ui.theme.Style

/** A PC key that the phone's keyboard doesn't have. */
enum class BarKey(
    val label: String,
    val keyCode: Int,
    val modifierMask: Int = 0,
    val repeats: Boolean = false,
) {
    Ctrl("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, modifierMask = KeyboardPacket.MODIFIER_CTRL.toInt()),
    Alt("Alt", KeyEvent.KEYCODE_ALT_LEFT, modifierMask = KeyboardPacket.MODIFIER_ALT.toInt()),
    Shift("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, modifierMask = KeyboardPacket.MODIFIER_SHIFT.toInt()),
    Win("Win", KeyEvent.KEYCODE_META_LEFT, modifierMask = KeyboardPacket.MODIFIER_META.toInt()),
    Caps("Caps", KeyEvent.KEYCODE_CAPS_LOCK),
    Esc("Esc", KeyEvent.KEYCODE_ESCAPE),
    Tab("Tab", KeyEvent.KEYCODE_TAB, repeats = true),
    Left("Left", KeyEvent.KEYCODE_DPAD_LEFT, repeats = true),
    Down("Down", KeyEvent.KEYCODE_DPAD_DOWN, repeats = true),
    Up("Up", KeyEvent.KEYCODE_DPAD_UP, repeats = true),
    Right("Right", KeyEvent.KEYCODE_DPAD_RIGHT, repeats = true),
    Home("Home", KeyEvent.KEYCODE_MOVE_HOME),
    End("End", KeyEvent.KEYCODE_MOVE_END),
    PageUp("PgUp", KeyEvent.KEYCODE_PAGE_UP, repeats = true),
    PageDown("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, repeats = true),
    Delete("Del", KeyEvent.KEYCODE_FORWARD_DEL, repeats = true),
    Backspace("Backspace", KeyEvent.KEYCODE_DEL, repeats = true),
    Enter("Enter", KeyEvent.KEYCODE_ENTER),
    F1("F1", KeyEvent.KEYCODE_F1),
    F2("F2", KeyEvent.KEYCODE_F2),
    F3("F3", KeyEvent.KEYCODE_F3),
    F4("F4", KeyEvent.KEYCODE_F4),
    F5("F5", KeyEvent.KEYCODE_F5),
    F6("F6", KeyEvent.KEYCODE_F6),
    F7("F7", KeyEvent.KEYCODE_F7),
    F8("F8", KeyEvent.KEYCODE_F8),
    F9("F9", KeyEvent.KEYCODE_F9),
    F10("F10", KeyEvent.KEYCODE_F10),
    F11("F11", KeyEvent.KEYCODE_F11),
    F12("F12", KeyEvent.KEYCODE_F12),
}

private val ModifierKeys = listOf(BarKey.Ctrl, BarKey.Alt, BarKey.Shift, BarKey.Win)
private val NavigationKeys = listOf(
    BarKey.Esc, BarKey.Tab, BarKey.Left, BarKey.Down, BarKey.Up, BarKey.Right,
    BarKey.Home, BarKey.End, BarKey.PageUp, BarKey.PageDown, BarKey.Delete,
)
private val FunctionKeys = listOf(
    BarKey.F1, BarKey.F2, BarKey.F3, BarKey.F4, BarKey.F5, BarKey.F6,
    BarKey.F7, BarKey.F8, BarKey.F9, BarKey.F10, BarKey.F11, BarKey.F12,
)

/**
 * What the bar has latched. A modifier is held down on the PC for as long as it's latched, the
 * way a finger would hold it, so Alt+Tab can be stepped through and Ctrl works with a click.
 */
class KeyBarState(smartTyping: Boolean, private val listener: Listener) {
    interface Listener {
        fun onKeyBarKey(key: BarKey)
        fun onKeyBarModifier(key: BarKey, down: Boolean)
        fun onKeyBarSmartTypingChanged(enabled: Boolean)
        fun onKeyBarSendText(text: String)
        fun onKeyBarStopSending()
        fun onKeyBarShortcut(codePoint: Int)
    }

    enum class Latch { Off, Once, Locked }

    private val latches = mutableStateMapOf<BarKey, Latch>()

    var smartTyping by mutableStateOf(smartTyping)
        private set

    var showingFunctionKeys by mutableStateOf(false)
        private set

    /** What's being written on the phone. It reaches the PC only when it's sent. */
    val draft = TextFieldState()

    /** Whether the draft field should hold the keyboard. */
    var composerOpen by mutableStateOf(false)

    /** How much of the sent text has reached the PC, from 0 to 1, or null when none is on its way. */
    var sendProgress by mutableStateOf<Float?>(null)

    /** The latched modifiers as KeyboardPacket flags. */
    val modifierMask: Int
        get() = latches.keys.fold(0) { mask, key -> mask or key.modifierMask }

    /**
     * Whether the next character is half of a shortcut rather than text. Shift isn't: on its
     * own it only changes the character.
     */
    val isShortcutHeld: Boolean
        get() = modifierMask and BarKey.Shift.modifierMask.inv() != 0

    fun latchOf(key: BarKey): Latch = latches[key] ?: Latch.Off

    /** Off, then good for one key, then locked until tapped again. */
    fun tapModifier(key: BarKey) {
        when (latchOf(key)) {
            Latch.Off -> {
                latches[key] = Latch.Once
                listener.onKeyBarModifier(key, true)
            }
            Latch.Once -> latches[key] = Latch.Locked
            Latch.Locked -> {
                latches.remove(key)
                listener.onKeyBarModifier(key, false)
            }
        }
    }

    /** A key went through, so the modifiers that were good for one key let go. */
    fun consumeOneShot() {
        latches.filterValues { it == Latch.Once }.keys.toList().forEach(::release)
    }

    /** Nothing can stay held once the bar is gone, or the PC would be left with a stuck key. */
    fun releaseModifiers() {
        latches.keys.toList().forEach(::release)
    }

    private fun release(key: BarKey) {
        latches.remove(key)
        listener.onKeyBarModifier(key, false)
    }

    internal fun tapKey(key: BarKey) = listener.onKeyBarKey(key)

    /** An empty field has nothing to send, so the same key is the PC's Enter. */
    internal fun sendDraft() {
        val text = draft.text.toString()
        if (text.isEmpty()) {
            tapKey(BarKey.Enter)
            return
        }
        draft.clearText()
        // A modifier held down on the PC would turn the text into shortcuts
        releaseModifiers()
        listener.onKeyBarSendText(text)
    }

    internal fun stopSending() = listener.onKeyBarStopSending()

    internal fun pressShortcut(codePoint: Int) = listener.onKeyBarShortcut(codePoint)

    internal fun toggleSmartTyping() {
        smartTyping = !smartTyping
        listener.onKeyBarSmartTypingChanged(smartTyping)
    }

    internal fun toggleFunctionKeys() {
        showingFunctionKeys = !showingFunctionKeys
    }
}

fun attachKeyBar(view: ComposeView, state: KeyBarState) {
    view.setContent {
        MoonlightTheme {
            KeyBar(state)
        }
    }
}

internal val KeyGap = 5.dp
internal val KeyHeight = 40.dp
internal val ScrollingKeyWidth = 46.dp
private val FixedKeyWidth = 54.dp

/** Above this there's room for everything on one line, which matters in landscape. */
private val OneLineMinWidth = 600.dp

/**
 * The accessory row above the keyboard. Video can't be sampled for refraction, so this is an
 * opaque surface in the glass palette. Touches on its gaps must not reach the stream as clicks.
 */
@Composable
private fun KeyBar(state: KeyBarState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundBase)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = KeyGap),
        ) {
            val availableWidth = maxWidth
            Column(verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                if (state.smartTyping) Composer(state)
                if (availableWidth >= OneLineMinWidth) {
                    Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                        FixedKeys(state, Modifier.width(FixedKeyWidth))
                        ScrollingKeys(state, Modifier.weight(1f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                        FixedKeys(state, Modifier.weight(1f))
                    }
                    ScrollingKeys(state, Modifier.fillMaxWidth())
                }
            }
        }
        state.sendProgress?.let { progress ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(Style.accent),
            )
        }
    }
}

/** Always within reach, so a modifier can be latched and then combined with any key. */
@Composable
private fun FixedKeys(state: KeyBarState, keyModifier: Modifier) {
    ModifierKeys.forEach { key ->
        val look = when (state.latchOf(key)) {
            KeyBarState.Latch.Off -> KeyLook.Plain
            KeyBarState.Latch.Once -> KeyLook.Tinted
            KeyBarState.Latch.Locked -> KeyLook.Filled
        }
        KeyCap(
            label = key.label,
            look = look,
            onTap = { state.tapModifier(key) },
            modifier = keyModifier,
        )
    }
    KeyCap(
        label = BarKey.Caps.label,
        onTap = { state.tapKey(BarKey.Caps) },
        modifier = keyModifier,
    )
    KeyCap(
        label = "Fn",
        look = if (state.showingFunctionKeys) KeyLook.Tinted else KeyLook.Plain,
        onTap = state::toggleFunctionKeys,
        modifier = keyModifier,
    )
    KeyCap(
        label = if (state.smartTyping) "Smart" else "Raw",
        look = if (state.smartTyping) KeyLook.Tinted else KeyLook.Plain,
        onTap = state::toggleSmartTyping,
        modifier = keyModifier,
    )
}

@Composable
private fun ScrollingKeys(state: KeyBarState, modifier: Modifier) {
    val keys = if (state.showingFunctionKeys) FunctionKeys else NavigationKeys
    // Each set starts from its own beginning
    key(state.showingFunctionKeys) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(KeyCorner))
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KeyGap),
        ) {
            keys.forEach { key ->
                KeyCap(
                    label = key.label,
                    icon = iconFor(key),
                    repeats = key.repeats,
                    onTap = { state.tapKey(key) },
                    modifier = Modifier.width(ScrollingKeyWidth),
                )
            }
        }
    }
}

private fun iconFor(key: BarKey): ImageVector? = when (key) {
    BarKey.Left -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
    BarKey.Right -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
    BarKey.Up -> Icons.Rounded.KeyboardArrowUp
    BarKey.Down -> Icons.Rounded.KeyboardArrowDown
    else -> null
}

internal enum class KeyLook { Plain, Tinted, Filled }

internal val KeyCorner = 10.dp
private val KeyPressedColor = Color(0xFFDCDCE2)

@Composable
internal fun KeyCap(
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    look: KeyLook = KeyLook.Plain,
    repeats: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val currentOnTap by rememberUpdatedState(onTap)
    val tap = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        currentOnTap()
    }

    val fill = when {
        look == KeyLook.Filled -> Style.accent
        look == KeyLook.Tinted -> Style.accent.copy(alpha = 0.16f)
        pressed -> KeyPressedColor
        else -> Color.White
    }
    val ink = when (look) {
        KeyLook.Filled -> Color.White
        KeyLook.Tinted -> Style.accentText
        KeyLook.Plain -> Style.textPrimary
    }

    Box(
        modifier = modifier
            .height(KeyHeight)
            .clip(RoundedCornerShape(KeyCorner))
            .background(fill)
            .pointerInput(repeats) {
                awaitEachGesture {
                    // Left unconsumed so a drag can still scroll the row, which ends this gesture
                    awaitFirstDown()
                    pressed = true
                    try {
                        if (!repeats) {
                            if (waitForUpOrCancellation() != null) tap()
                            return@awaitEachGesture
                        }

                        var finished = false
                        var lifted = false
                        withTimeoutOrNull(RepeatDelayMillis) {
                            lifted = waitForUpOrCancellation() != null
                            finished = true
                        }
                        if (finished) {
                            if (lifted) tap()
                            return@awaitEachGesture
                        }

                        // Held down: keep going until the finger lifts
                        while (!finished) {
                            tap()
                            withTimeoutOrNull(RepeatIntervalMillis) {
                                waitForUpOrCancellation()
                                finished = true
                            }
                        }
                    } finally {
                        pressed = false
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    tap()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = label,
                style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
                color = ink,
                maxLines = 1,
            )
        }
    }
}

private const val RepeatDelayMillis = 400L
private const val RepeatIntervalMillis = 60L
