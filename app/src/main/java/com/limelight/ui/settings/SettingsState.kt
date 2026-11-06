package com.limelight.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Vibrator
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import androidx.annotation.ArrayRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.limelight.R
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.preferences.GlPreferences
import com.limelight.preferences.PreferenceConfiguration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry of a picker. [native] marks a mode of this display, which servers may not support. */
@Immutable
data class SettingsOption(val value: String, val name: String, val native: Boolean = false)

/**
 * The settings screen's view of the default SharedPreferences, which [PreferenceConfiguration]
 * reads when a stream starts, plus what this device can actually do. All methods must be called
 * on the main thread.
 */
@Stable
@Suppress("DEPRECATION") // PreferenceConfiguration reads the same framework default preferences.
class SettingsState(private val activity: Activity) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    private val vibrator = activity.getSystemService(Vibrator::class.java)

    private var stored by mutableStateOf<Map<String, *>>(prefs.all)
    private var decoderWidth = 0

    val hasOnScreenControls = hasFeature(PackageManager.FEATURE_TOUCHSCREEN)
    val hasUsbHost = hasFeature(PackageManager.FEATURE_USB_HOST)
    val hasMotionSensors = hasFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) ||
        hasFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)
    val hasVibrator = vibrator.hasVibrator()
    val hasVibrationStrength = hasVibrator && vibrator.hasAmplitudeControl()

    /** NVIDIA SHIELD delivers raw mouse input in pointer capture mode already. */
    val hasRemoteDesktopMouse = !hasFeature("com.nvidia.feature.shield")

    /** Fire OS has it, but Amazon's store guidelines forbid it. */
    val hasPictureInPicture = hasFeature("android.software.picture_in_picture") &&
        !hasFeature("com.amazon.software.fireos")

    var hasHdr10 by mutableStateOf(false)
        private set
    var resolutions by mutableStateOf(options(R.array.resolution_names, R.array.resolution_values))
        private set
    var frameRates by mutableStateOf(options(R.array.fps_names, R.array.fps_values))
        private set

    val resolution: String
        get() = choice(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION)
    val frameRate: String
        get() = choice(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS)
    val bitrate: Int
        get() = stored[PreferenceConfiguration.BITRATE_PREF_STRING] as? Int
            ?: PreferenceConfiguration.getDefaultBitrate(activity)

    fun flag(key: String, default: Boolean): Boolean = stored[key] as? Boolean ?: default
    fun choice(key: String, default: String): String = stored[key] as? String ?: default
    fun number(key: String, default: Int): Int = stored[key] as? Int ?: default

    fun setFlag(key: String, value: Boolean) {
        write { putBoolean(key, value) }
        if (key == PreferenceConfiguration.UNLOCK_FPS_STRING) rebuildOptions()
    }

    fun setChoice(key: String, value: String) = write { putString(key, value) }

    fun setNumber(key: String, value: Int) = write { putInt(key, value) }

    /** A different resolution or frame rate starts from its own default bitrate. */
    fun selectResolution(value: String) {
        if (value == resolution) return
        write {
            putString(PreferenceConfiguration.RESOLUTION_PREF_STRING, value)
            putInt(PreferenceConfiguration.BITRATE_PREF_STRING, PreferenceConfiguration.getDefaultBitrate(value, frameRate))
        }
    }

    fun selectFrameRate(value: String) {
        if (value == frameRate) return
        write {
            putString(PreferenceConfiguration.FPS_PREF_STRING, value)
            putInt(PreferenceConfiguration.BITRATE_PREF_STRING, PreferenceConfiguration.getDefaultBitrate(resolution, value))
        }
    }

    fun resetOscLayout() {
        activity.getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE)
            .edit { clear() }
    }

    fun openLanguageSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setData(Uri.parse("package:" + activity.packageName)),
        )
    }

    /**
     * Brings everything up to date with the preferences and the current display. Run it when
     * the screen shows and on configuration changes: a foldable swaps displays under the same id.
     */
    suspend fun load() {
        stored = prefs.all
        // Listing the decoders takes long enough to drop frames of the tab transition.
        decoderWidth = withContext(Dispatchers.Default) { widestDecoderWidth() }
        rebuildOptions()
    }

    private fun rebuildOptions() {
        val display = activity.display ?: return
        hasHdr10 = display.hdrCapabilities?.supportedHdrTypes?.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) == true
        resolutions = resolutionOptions(display)
        frameRates = frameRateOptions(display)
    }

    private fun resolutionOptions(display: Display): List<SettingsOption> {
        val list = options(R.array.resolution_names, R.array.resolution_values).toMutableList()
        var maxWidth = decoderWidth

        // Native size without the notch, for those who don't want the picture behind it.
        val cutoutSafe = cutoutSafeSize(display)
        cutoutSafe?.let { (width, height) -> list.addNative(width, height, insetsRemoved = false) }

        // Always allow what the display itself can show: decoders can report total nonsense.
        for (mode in display.supportedModes) {
            // Some devices report the portrait orientation; normalize to width > height.
            val width = max(mode.physicalWidth, mode.physicalHeight)
            val height = min(mode.physicalWidth, mode.physicalHeight)
            list.addNative(width, height, insetsRemoved = cutoutSafe != null)
            maxWidth = max(
                maxWidth,
                when {
                    width >= 3840 || height >= 2160 -> 3840
                    width >= 2560 || height >= 1440 -> 2560
                    width >= 1920 || height >= 1080 -> 1920
                    else -> 0
                },
            )
        }

        // 720p always stays. Each step falls back to the next, in case that one goes as well.
        val key = PreferenceConfiguration.RESOLUTION_PREF_STRING
        if (maxWidth != 0) {
            if (maxWidth < 3840) list.removeOption(key, PreferenceConfiguration.RES_4K, PreferenceConfiguration.RES_1440P)
            if (maxWidth < 2560) list.removeOption(key, PreferenceConfiguration.RES_1440P, PreferenceConfiguration.RES_1080P)
            if (maxWidth < 1920) list.removeOption(key, PreferenceConfiguration.RES_1080P, PreferenceConfiguration.RES_720P)
        }
        return list
    }

    private fun frameRateOptions(display: Display): List<SettingsOption> {
        val list = options(R.array.fps_names, R.array.fps_values).toMutableList()
        val maxFps = display.supportedModes.fold(display.refreshRate) { fastest, mode -> max(fastest, mode.refreshRate) }

        // 30 and 60 always stay. The margins cover a rate that is rounded down.
        val key = PreferenceConfiguration.FPS_PREF_STRING
        if (!flag(PreferenceConfiguration.UNLOCK_FPS_STRING, false)) {
            if (maxFps < 118) list.removeOption(key, "120", "90")
            if (maxFps < 88) list.removeOption(key, "90", "60")
        }

        val native = maxFps.roundToInt().toString()
        if (native != "0" && list.none { it.value == native }) {
            val name = activity.getString(R.string.resolution_prefix_native) +
                " ($native ${activity.getString(R.string.fps_suffix_fps)})"
            list += SettingsOption(native, name, native = true)
        }
        return list
    }

    /** Null when the display has no cutout. */
    private fun cutoutSafeSize(display: Display): Pair<Int, Int>? {
        val cutout = display.cutout ?: return null
        val widthInsets = cutout.safeInsetLeft + cutout.safeInsetRight
        val heightInsets = cutout.safeInsetTop + cutout.safeInsetBottom
        if (widthInsets == 0 && heightInsets == 0) return null

        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        val width = metrics.widthPixels - widthInsets
        val height = metrics.heightPixels - heightInsets
        return max(width, height) to min(width, height)
    }

    /** Squarish screens (foldables) get both orientations, since neither is the obvious one. */
    private fun MutableList<SettingsOption>.addNative(width: Int, height: Int, insetsRemoved: Boolean) {
        if (PreferenceConfiguration.isSquarishScreen(width, height)) {
            addNativeEntry(height, width, insetsRemoved, R.string.resolution_prefix_native_portrait)
            addNativeEntry(width, height, insetsRemoved, R.string.resolution_prefix_native_landscape)
        } else {
            addNativeEntry(width, height, insetsRemoved, null)
        }
    }

    private fun MutableList<SettingsOption>.addNativeEntry(width: Int, height: Int, insetsRemoved: Boolean, orientation: Int?) {
        val value = "${width}x$height"
        if (any { it.value == value }) return

        val prefix = if (insetsRemoved) R.string.resolution_prefix_native_fullscreen else R.string.resolution_prefix_native
        val name = buildString {
            append(activity.getString(prefix))
            orientation?.let { append(' ').append(activity.getString(it)) }
            append(" (").append(value).append(')')
        }
        add(SettingsOption(value, name, native = true))
    }

    /** Drops an option the device can't do; if it was selected, moves to [fallback]. */
    private fun MutableList<SettingsOption>.removeOption(key: String, value: String, fallback: String) {
        removeAll { it.value.equals(value, ignoreCase = true) }
        val selected = key == PreferenceConfiguration.RESOLUTION_PREF_STRING && resolution.equals(value, ignoreCase = true) ||
            key == PreferenceConfiguration.FPS_PREF_STRING && frameRate.equals(value, ignoreCase = true)
        if (selected) {
            setChoice(key, fallback)
            setNumber(PreferenceConfiguration.BITRATE_PREF_STRING, PreferenceConfiguration.getDefaultBitrate(resolution, frameRate))
        }
    }

    /** Widest video the AVC and HEVC decoders claim, in steps of 1280, 1920 and 3840; 0 if unknown. */
    private fun widestDecoderWidth(): Int {
        MediaCodecHelper.initialize(activity, GlPreferences.readPreferences(activity).glRenderer)
        return listOf("video/avc", "video/hevc").maxOf { mimeType ->
            val widths = MediaCodecHelper.findProbableSafeDecoder(mimeType, -1)
                ?.getCapabilitiesForType(mimeType)
                ?.videoCapabilities
                ?.supportedWidths
            when {
                // A decoder that doesn't report 720p can't be believed about anything.
                widths == null || !widths.contains(1280) -> 0
                widths.contains(3840) -> 3840
                widths.contains(1920) -> 1920
                else -> 1280
            }
        }
    }

    private fun options(@ArrayRes names: Int, @ArrayRes values: Int): List<SettingsOption> =
        activity.resources.getStringArray(names).zip(activity.resources.getStringArray(values)) { name, value ->
            SettingsOption(value, name)
        }

    private fun hasFeature(name: String) = activity.packageManager.hasSystemFeature(name)

    private fun write(change: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = change)
        stored = prefs.all
    }
}
