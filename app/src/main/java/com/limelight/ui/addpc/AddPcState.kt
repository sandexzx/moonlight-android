package com.limelight.ui.addpc

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.limelight.ui.components.AlertRequest
import com.limelight.ui.components.OverlayState
import com.limelight.ui.theme.setGlassContent

/** Implemented by AddComputerManually, which keeps the Java logic. */
interface AddPcActions {
    fun onBack()
    fun onAdd(address: String)
}

/**
 * State of the add PC screen, fed from Java.
 * All methods must be called on the main thread.
 */
@Stable
class AddPcState {
    val overlay = OverlayState()
    val address = TextFieldState()

    /** True from the tap on Add until the PC answers or the attempt fails. */
    var busy by mutableStateOf(false)

    fun showFailure(title: String, message: String, okLabel: String, helpLabel: String, onHelp: Runnable) {
        busy = false
        overlay.showAlert(
            AlertRequest(
                title = title,
                message = message,
                confirmLabel = okLabel,
                cancelLabel = helpLabel,
                onCancel = { onHelp.run() },
                onConfirm = {},
            ),
        )
    }
}

/** Java's way into Compose. */
object AddPcHost {
    @JvmStatic
    fun attach(activity: ComponentActivity, state: AddPcState, actions: AddPcActions) {
        activity.setGlassContent { AddPcApp(state, actions) }
    }
}
