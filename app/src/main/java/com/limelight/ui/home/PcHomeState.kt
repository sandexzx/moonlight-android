package com.limelight.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import com.limelight.ui.components.AlertRequest
import com.limelight.ui.components.OverlayState
import com.limelight.ui.theme.setGlassContent

/** What a PC card shows and offers. */
enum class PcStatus { Checking, Offline, NotPaired, Ready, Session }

@Immutable
data class PcCardModel(
    val uuid: String,
    val name: String,
    val status: PcStatus,
    /** Wake-on-LAN needs a known MAC address. */
    val canWake: Boolean,
    val isGameStream: Boolean,
)

/** Everything the screen can ask for. Implemented by PcView, which keeps the Java logic. */
interface PcHomeActions {
    fun onConnect(uuid: String)
    fun onResume(uuid: String)
    fun onPair(uuid: String)
    fun onWake(uuid: String)
    fun onOpenApps(uuid: String)
    fun onOpenAllApps(uuid: String)
    fun onQuitSession(uuid: String)
    fun onDeletePc(uuid: String)
    fun onShowDetails(uuid: String)
    fun onTestNetwork()
    fun onGameStreamInfo()
    fun onOpenSettings()
    fun onAddPc()
    fun onHelp()
}

/**
 * State of the computers screen, fed from Java. Replaces PcGridAdapter: PcView pushes every
 * polled [ComputerDetails] here and looks them up again by uuid when an action comes back.
 * All methods must be called on the main thread.
 */
@Stable
class PcHomeState {
    val overlay = OverlayState()

    /** Sorted by name, like the old grid. */
    val computers = mutableStateListOf<PcCardModel>()

    /** The PC whose Connect is looking up its Desktop app; its button shows a spinner. */
    var connectingUuid by mutableStateOf<String?>(null)
        private set

    private val details = HashMap<String, ComputerDetails>()

    fun update(computer: ComputerDetails) {
        details[computer.uuid] = computer
        val card = computer.toCard()
        val index = computers.indexOfFirst { it.uuid == card.uuid }
        if (index >= 0) {
            if (computers[index] != card) computers[index] = card
        } else {
            val after = computers.indexOfFirst { it.name.lowercase() > card.name.lowercase() }
            computers.add(if (after >= 0) after else computers.size, card)
        }
    }

    fun remove(uuid: String) {
        details.remove(uuid)
        computers.removeAll { it.uuid == uuid }
    }

    fun details(uuid: String): ComputerDetails? = details[uuid]

    /** Pass null when the lookup has finished. */
    fun setConnecting(uuid: String?) {
        connectingUuid = uuid
    }

    fun toast(text: String) = overlay.toast(text)

    fun showMessage(title: String, message: String, okLabel: String) {
        overlay.showAlert(
            AlertRequest(title = title, message = message, longMessage = true, confirmLabel = okLabel, onConfirm = {}),
        )
    }

    fun showPairing(title: String, message: String, pin: String, help: String, okLabel: String) {
        overlay.showAlert(
            AlertRequest(
                title = title,
                message = message,
                emphasis = pin,
                footnote = help,
                confirmLabel = okLabel,
                onConfirm = {},
            ),
        )
    }

    fun confirm(
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String,
        destructive: Boolean,
        onConfirm: Runnable,
    ) {
        overlay.showAlert(
            AlertRequest(
                title = title,
                message = message,
                confirmLabel = confirmLabel,
                cancelLabel = cancelLabel,
                destructive = destructive,
                onConfirm = { onConfirm.run() },
            ),
        )
    }

    fun dismissAlert() = overlay.dismissAlert()
}

private fun ComputerDetails.toCard() = PcCardModel(
    uuid = uuid,
    name = name.orEmpty(),
    status = when {
        state == ComputerDetails.State.UNKNOWN -> PcStatus.Checking
        state == ComputerDetails.State.OFFLINE -> PcStatus.Offline
        pairState != PairingManager.PairState.PAIRED -> PcStatus.NotPaired
        runningGameId != 0 -> PcStatus.Session
        else -> PcStatus.Ready
    },
    canWake = macAddress != null,
    isGameStream = nvidiaServer,
)

/** Java entry point: PcView calls [attach] once instead of inflating a layout. */
object PcHomeHost {
    @JvmStatic
    fun attach(activity: ComponentActivity, state: PcHomeState, actions: PcHomeActions) {
        activity.setGlassContent { PcHomeApp(state, actions) }
    }
}
