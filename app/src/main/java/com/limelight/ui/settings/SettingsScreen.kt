package com.limelight.ui.settings

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.components.AlertRequest
import com.limelight.ui.components.ContentMaxWidth
import com.limelight.ui.components.DestructiveRow
import com.limelight.ui.components.FlatCard
import com.limelight.ui.components.LargeTitle
import com.limelight.ui.components.LocalOverlay
import com.limelight.ui.components.MenuAction
import com.limelight.ui.components.NavRow
import com.limelight.ui.components.PickerRow
import com.limelight.ui.components.RowDivider
import com.limelight.ui.components.SectionHeader
import com.limelight.ui.components.SliderRow
import com.limelight.ui.components.ToggleRow
import java.util.Locale

private val SectionWidth = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
private val ItemGap = 8.dp
private val SectionGap = 22.dp

/**
 * Settings tab of the home screen. Keys and defaults match PreferenceConfiguration, which reads
 * them when a stream starts. A row that only matters with another option on stays hidden until
 * that option is on.
 */
@Composable
fun SettingsTab(state: SettingsState, padding: PaddingValues, listState: LazyListState) {
    LaunchedEffect(LocalConfiguration.current) { state.load() }

    LazyColumn(
        state = listState,
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
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        item(key = "title") {
            LargeTitle(
                stringResource(R.string.home_tab_settings),
                SectionWidth,
            )
        }
        item(key = "basic") { BasicSection(state) }
        item(key = "audio") { AudioSection(state) }
        // Separate items: a tall one composes in full on the frame it scrolls in.
        item(key = "gamepad") { GamepadSection(state) }
        if (state.hasUsbHost) {
            item(key = "gamepadUsb") { UsbGamepadGroup(state) }
        }
        item(key = "gamepadMotion") { GamepadMotionGroup(state) }
        item(key = "input") { InputSection(state) }
        if (state.hasOnScreenControls) {
            item(key = "onScreenControls") { OnScreenControlsSection(state) }
        }
        item(key = "host") { HostSection(state) }
        item(key = "interface") { InterfaceSection(state) }
        item(key = "advanced") { AdvancedSection(state) }
    }
}

@Composable
private fun BasicSection(state: SettingsState) {
    val overlay = LocalOverlay.current
    val warningText = stringResource(R.string.text_native_res_dialog)
    val nativeResolutionTitle = stringResource(R.string.title_native_res_dialog)
    val nativeFpsTitle = stringResource(R.string.title_native_fps_dialog)
    val okLabel = stringResource(android.R.string.ok)

    Section(R.string.category_basic_settings) {
        Group {
            OptionsRow(
                title = R.string.title_resolution_list,
                summary = R.string.summary_resolution_list,
                options = state.resolutions,
                selected = state.resolution,
            ) { option ->
                state.selectResolution(option.value)
                if (option.native) {
                    overlay.showAlert(AlertRequest(title = nativeResolutionTitle, message = warningText, confirmLabel = okLabel, onConfirm = {}))
                }
            }
            RowDivider()
            OptionsRow(
                title = R.string.title_fps_list,
                summary = R.string.summary_fps_list,
                options = state.frameRates,
                selected = state.frameRate,
            ) { option ->
                state.selectFrameRate(option.value)
                if (option.native) {
                    overlay.showAlert(AlertRequest(title = nativeFpsTitle, message = warningText, confirmLabel = okLabel, onConfirm = {}))
                }
            }
            RowDivider()
            BitrateRow(state)
            RowDivider()
            ChoiceRow(
                state,
                key = "frame_pacing",
                default = "latency",
                title = R.string.title_frame_pacing,
                summary = R.string.summary_frame_pacing,
                names = R.array.video_frame_pacing_names,
                values = R.array.video_frame_pacing_values,
            )
            RowDivider()
            FlagRow(state, "checkbox_stretch_video", R.string.title_checkbox_stretch_video)
        }
    }
}

@Composable
private fun BitrateRow(state: SettingsState) {
    val unit = stringResource(R.string.suffix_seekbar_bitrate_mbps)
    SliderRow(
        label = stringResource(R.string.title_seekbar_bitrate),
        summary = stringResource(R.string.summary_seekbar_bitrate),
        value = state.bitrate,
        range = 500..150_000,
        step = 500,
        valueText = { String.format(Locale.ROOT, "%.1f %s", it / 1000f, unit) },
        onValueChange = { state.setNumber(PreferenceConfiguration.BITRATE_PREF_STRING, it) },
    )
}

@Composable
private fun AudioSection(state: SettingsState) {
    Section(R.string.category_audio_settings) {
        Group {
            ChoiceRow(
                state,
                key = "list_audio_config",
                default = "2",
                title = R.string.title_audio_config_list,
                summary = R.string.summary_audio_config_list,
                names = R.array.audio_config_names,
                values = R.array.audio_config_values,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_enable_audiofx",
                R.string.title_checkbox_enable_audiofx,
                R.string.summary_checkbox_enable_audiofx,
            )
        }
    }
}

/** Three cards rather than one: a single card this tall is a very large glass layer to redraw. */
@Composable
private fun GamepadSection(state: SettingsState) {
    Section(R.string.category_gamepad_settings) {
        Group {
            PercentRow(
                state,
                key = "seekbar_deadzone",
                default = 7,
                range = 1..20,
                title = R.string.title_seekbar_deadzone,
                summary = R.string.summary_seekbar_deadzone,
                unit = R.string.suffix_seekbar_deadzone,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_multi_controller",
                R.string.title_checkbox_multi_controller,
                R.string.summary_checkbox_multi_controller,
                default = true,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_flip_face_buttons",
                R.string.title_checkbox_flip_face_buttons,
                R.string.summary_checkbox_flip_face_buttons,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_gamepad_touchpad_as_mouse",
                R.string.title_checkbox_gamepad_touchpad_as_mouse,
                R.string.summary_checkbox_gamepad_touchpad_as_mouse,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_mouse_emulation",
                R.string.title_checkbox_mouse_emulation,
                R.string.summary_checkbox_mouse_emulation,
                default = true,
            )
            DependentRows(state.flag("checkbox_mouse_emulation", true)) {
                ChoiceRow(
                    state,
                    key = "analog_scrolling",
                    default = "right",
                    title = R.string.title_analog_scrolling,
                    summary = R.string.summary_analog_scrolling,
                    names = R.array.analog_scrolling_names,
                    values = R.array.analog_scrolling_values,
                )
            }
        }
    }
}

@Composable
private fun UsbGamepadGroup(state: SettingsState) {
    Group(SectionWidth) {
        FlagRow(
            state,
            "checkbox_usb_driver",
            R.string.title_checkbox_xb1_driver,
            R.string.summary_checkbox_xb1_driver,
            default = true,
        )
        DependentRows(state.flag("checkbox_usb_driver", true)) {
            FlagRow(
                state,
                "checkbox_usb_bind_all",
                R.string.title_checkbox_usb_bind_all,
                R.string.summary_checkbox_usb_bind_all,
            )
        }
    }
}

@Composable
private fun GamepadMotionGroup(state: SettingsState) {
    Group(SectionWidth) {
        FlagRow(
            state,
            "checkbox_gamepad_motion_sensors",
            R.string.title_checkbox_gamepad_motion_sensors,
            R.string.summary_checkbox_gamepad_motion_sensors,
            default = true,
        )
        if (state.hasMotionSensors) {
            RowDivider()
            FlagRow(
                state,
                "checkbox_gamepad_motion_fallback",
                R.string.title_checkbox_gamepad_motion_fallback,
                R.string.summary_checkbox_gamepad_motion_fallback,
            )
        }
        if (state.hasVibrator) {
            RowDivider()
            FlagRow(
                state,
                "checkbox_vibrate_fallback",
                R.string.title_checkbox_vibrate_fallback,
                R.string.summary_checkbox_vibrate_fallback,
            )
        }
        if (state.hasVibrationStrength) {
            DependentRows(state.flag("checkbox_vibrate_fallback", false)) {
                PercentRow(
                    state,
                    key = "seekbar_vibrate_fallback_strength",
                    default = 100,
                    range = 1..200,
                    title = R.string.title_seekbar_vibrate_fallback_strength,
                    summary = R.string.summary_seekbar_vibrate_fallback_strength,
                    unit = R.string.suffix_seekbar_vibrate_fallback_strength,
                )
            }
        }
    }
}

@Composable
private fun InputSection(state: SettingsState) {
    Section(R.string.category_input_settings) {
        Group {
            FlagRow(
                state,
                "checkbox_touchscreen_trackpad",
                R.string.title_checkbox_touchscreen_trackpad,
                R.string.summary_checkbox_touchscreen_trackpad,
                default = true,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_mouse_nav_buttons",
                R.string.title_checkbox_mouse_nav_buttons,
                R.string.summary_checkbox_mouse_nav_buttons,
            )
            if (state.hasRemoteDesktopMouse) {
                RowDivider()
                FlagRow(
                    state,
                    "checkbox_absolute_mouse_mode",
                    R.string.title_checkbox_absolute_mouse_mode,
                    R.string.summary_checkbox_absolute_mouse_mode,
                    default = true,
                )
            }
        }
    }
}

@Composable
private fun OnScreenControlsSection(state: SettingsState) {
    val overlay = LocalOverlay.current
    val resetTitle = stringResource(R.string.dialog_title_reset_osc)
    val resetText = stringResource(R.string.dialog_text_reset_osc)
    val resetDone = stringResource(R.string.toast_reset_osc_success)
    val yes = stringResource(R.string.yes)
    val no = stringResource(R.string.no)

    Section(R.string.category_on_screen_controls_settings) {
        Group {
            FlagRow(
                state,
                "checkbox_show_onscreen_controls",
                R.string.title_checkbox_show_onscreen_controls,
                R.string.summary_checkbox_show_onscreen_controls,
            )
            DependentRows(state.flag("checkbox_show_onscreen_controls", false)) {
                if (state.hasVibrator) {
                    FlagRow(
                        state,
                        "checkbox_vibrate_osc",
                        R.string.title_checkbox_vibrate_osc,
                        R.string.summary_checkbox_vibrate_osc,
                        default = true,
                    )
                    RowDivider()
                }
                FlagRow(state, "checkbox_only_show_L3R3", R.string.title_only_l3r3, R.string.summary_only_l3r3)
                RowDivider()
                FlagRow(
                    state,
                    "checkbox_show_guide_button",
                    R.string.title_show_guide_button,
                    R.string.summary_show_guide_button,
                    default = true,
                )
                RowDivider()
                PercentRow(
                    state,
                    key = "seekbar_osc_opacity",
                    default = 90,
                    range = 0..100,
                    title = R.string.dialog_title_osc_opacity,
                    summary = R.string.summary_osc_opacity,
                    unit = R.string.suffix_osc_opacity,
                )
                RowDivider()
                DestructiveRow(
                    label = stringResource(R.string.title_reset_osc),
                    summary = stringResource(R.string.summary_reset_osc),
                    onClick = {
                        overlay.showAlert(
                            AlertRequest(
                                title = resetTitle,
                                message = resetText,
                                confirmLabel = yes,
                                destructive = true,
                                cancelLabel = no,
                                onConfirm = {
                                    state.resetOscLayout()
                                    overlay.toast(resetDone)
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HostSection(state: SettingsState) {
    Section(R.string.category_host_settings) {
        Group {
            FlagRow(
                state,
                "checkbox_enable_sops",
                R.string.title_checkbox_enable_sops,
                R.string.summary_checkbox_enable_sops,
                default = true,
            )
            RowDivider()
            FlagRow(state, "checkbox_host_audio", R.string.title_checkbox_host_audio, R.string.summary_checkbox_host_audio)
        }
    }
}

@Composable
private fun InterfaceSection(state: SettingsState) {
    Section(R.string.category_ui_settings) {
        Group {
            NavRow(
                label = stringResource(R.string.title_language_list),
                summary = stringResource(R.string.summary_language_list),
                onClick = state::openLanguageSettings,
            )
            if (state.hasPictureInPicture) {
                RowDivider()
                FlagRow(
                    state,
                    "checkbox_enable_pip",
                    R.string.title_checkbox_enable_pip,
                    R.string.summary_checkbox_enable_pip,
                )
            }
        }
    }
}

@Composable
private fun AdvancedSection(state: SettingsState) {
    Section(R.string.category_advanced_settings) {
        Group {
            FlagRow(state, PreferenceConfiguration.UNLOCK_FPS_STRING, R.string.title_unlock_fps, R.string.summary_unlock_fps)
            RowDivider()
            FlagRow(
                state,
                "checkbox_reduce_refresh_rate",
                R.string.title_checkbox_reduce_refresh_rate,
                R.string.summary_checkbox_reduce_refresh_rate,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_disable_warnings",
                R.string.title_checkbox_disable_warnings,
                R.string.summary_checkbox_disable_warnings,
            )
            RowDivider()
            ChoiceRow(
                state,
                key = "video_format",
                default = "auto",
                title = R.string.title_video_format,
                summary = R.string.summary_video_format,
                names = R.array.video_format_names,
                values = R.array.video_format_values,
            )
            if (state.hasHdr10) {
                RowDivider()
                FlagRow(state, "checkbox_enable_hdr", R.string.title_enable_hdr, R.string.summary_enable_hdr)
            }
            RowDivider()
            FlagRow(state, "checkbox_full_range", R.string.title_full_range, R.string.summary_full_range)
            RowDivider()
            FlagRow(
                state,
                "checkbox_enable_perf_overlay",
                R.string.title_enable_perf_overlay,
                R.string.summary_enable_perf_overlay,
            )
            RowDivider()
            FlagRow(
                state,
                "checkbox_enable_post_stream_toast",
                R.string.title_enable_post_stream_toast,
                R.string.summary_enable_post_stream_toast,
            )
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        SectionWidth.padding(top = SectionGap - ItemGap),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(stringResource(title))
        content()
    }
}

/** Card of rows; the rows draw their own padding, so it's edge to edge. */
@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    FlatCard(modifier, contentPadding = PaddingValues(0.dp), content = content)
}

/** Rows that slide out of the card while the option above them is off. Brings its own divider. */
@Composable
private fun ColumnScope.DependentRows(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            RowDivider()
            content()
        }
    }
}

@Composable
private fun FlagRow(
    state: SettingsState,
    key: String,
    @StringRes title: Int,
    @StringRes summary: Int? = null,
    default: Boolean = false,
) {
    ToggleRow(
        label = stringResource(title),
        summary = summary?.let { stringResource(it) },
        checked = state.flag(key, default),
        onCheckedChange = { state.setFlag(key, it) },
    )
}

@Composable
private fun PercentRow(
    state: SettingsState,
    key: String,
    default: Int,
    range: IntRange,
    @StringRes title: Int,
    @StringRes summary: Int,
    @StringRes unit: Int,
) {
    val suffix = stringResource(unit)
    SliderRow(
        label = stringResource(title),
        summary = stringResource(summary),
        value = state.number(key, default),
        range = range,
        valueText = { "$it$suffix" },
        onValueChange = { state.setNumber(key, it) },
    )
}

@Composable
private fun ChoiceRow(
    state: SettingsState,
    key: String,
    default: String,
    @StringRes title: Int,
    @StringRes summary: Int,
    @ArrayRes names: Int,
    @ArrayRes values: Int,
) {
    val options = stringArrayResource(names).zip(stringArrayResource(values)) { name, value ->
        SettingsOption(value, name)
    }
    OptionsRow(title, summary, options, state.choice(key, default)) { state.setChoice(key, it.value) }
}

@Composable
private fun OptionsRow(
    @StringRes title: Int,
    @StringRes summary: Int,
    options: List<SettingsOption>,
    selected: String,
    onSelect: (SettingsOption) -> Unit,
) {
    val overlay = LocalOverlay.current
    val label = stringResource(title)
    PickerRow(
        label = label,
        summary = stringResource(summary),
        value = options.find { it.value == selected }?.name ?: selected,
        onClick = { anchor ->
            overlay.showMenu(
                anchor = anchor,
                actions = options.map { option ->
                    MenuAction(option.name, checked = option.value == selected) { onSelect(option) }
                },
                title = label,
            )
        },
    )
}
