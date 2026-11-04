package com.limelight.ui.apps

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.limelight.nvstream.http.NvApp
import com.limelight.ui.components.AlertRequest
import com.limelight.ui.components.OverlayState
import com.limelight.ui.theme.setGlassContent

@Immutable
data class AppItem(val id: Int, val name: String)

/** Everything the screen can ask for. Implemented by AppView, which keeps the Java logic. */
interface AppListActions {
    fun onBack()
    fun onStartApp(appId: Int)
    fun onResumeApp(appId: Int)
    fun onQuitApp(appId: Int)
    fun onQuitAndStartApp(appId: Int)
    fun onToggleHiddenApp(appId: Int)
    fun onShowAppDetails(appId: Int)
    fun onCreateAppShortcut(appId: Int)
}

/**
 * State of the app list screen, fed from Java.
 * All methods must be called on the main thread.
 */
@Stable
class AppListState(val pcName: String, showHidden: Boolean, hidden: Collection<Int>) {
    val overlay = OverlayState()

    /** All apps of the PC, hidden ones included, sorted by name. */
    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    /** False until the first app list arrives, from the cache or the PC. */
    var loaded by mutableStateOf(false)
        private set

    /** 0 when nothing runs. */
    var runningId by mutableIntStateOf(0)
        private set

    private var hiddenIds by mutableStateOf(hidden.toSet())

    /**
     * Left out of the list. Fixed when the screen opens, so an app hidden just now stays visible
     * and dimmed, and the hide can be undone until the screen is left.
     */
    private var omittedIds by mutableStateOf(if (showHidden) emptySet() else hidden.toSet())

    private val artById = mutableStateMapOf<Int, ImageBitmap>()
    private val nvApps = HashMap<Int, NvApp>()

    /** The app in session gets the hero card, even if it is hidden from the list. */
    val runningApp: AppItem?
        get() = apps.firstOrNull { it.id == runningId }

    /** What goes in the list: everything but the running app, minus hidden ones. */
    val listedApps: List<AppItem>
        get() = apps.filter { it.id != runningId && it.id !in omittedIds }

    fun isHidden(appId: Int): Boolean = appId in hiddenIds

    fun art(appId: Int): ImageBitmap? = artById[appId]

    fun app(appId: Int): NvApp? = nvApps[appId]

    fun updateApps(list: List<NvApp>) {
        nvApps.clear()
        list.forEach { nvApps[it.appId] = it }
        apps = list
            .map { AppItem(it.appId, it.appName.orEmpty()) }
            .sortedBy { it.name.lowercase() }
        artById.keys.retainAll(nvApps.keys)
        loaded = true
    }

    fun updateRunning(appId: Int) {
        runningId = appId
    }

    fun setHidden(appId: Int, hidden: Boolean) {
        hiddenIds = if (hidden) hiddenIds + appId else hiddenIds - appId
        if (!hidden) omittedIds = omittedIds - appId
    }

    fun updateArt(appId: Int, art: Bitmap) {
        artById[appId] = art.asImageBitmap()
    }

    fun toast(text: String) = overlay.toast(text)

    fun showMessage(title: String, message: String, okLabel: String) {
        overlay.showAlert(
            AlertRequest(title = title, message = message, longMessage = true, confirmLabel = okLabel, onConfirm = {}),
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
}

/** Java entry point: AppView calls [attach] once instead of inflating a layout. */
object AppListHost {
    @JvmStatic
    fun attach(activity: ComponentActivity, state: AppListState, actions: AppListActions) {
        activity.setGlassContent { AppListApp(state, actions) }
    }
}
