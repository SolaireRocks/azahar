// Copyright 2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.overlay

import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.features.input.Hotkey

enum class OverlayControl(
    val toggleIndex: Int,
    val id: Int,
    val scaleKeyId: Int,
    val shortLabel: String,
    @StringRes val titleRes: Int,
    val defaultEnabled: Boolean,
    val baseScale: Float,
    @IntegerRes val landscapeXRes: Int,
    @IntegerRes val landscapeYRes: Int,
    @IntegerRes val portraitXRes: Int,
    @IntegerRes val portraitYRes: Int
) {
    BUTTON_A(
        toggleIndex = 0,
        id = NativeLibrary.ButtonType.BUTTON_A,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_A,
        shortLabel = "A",
        titleRes = R.string.button_a,
        defaultEnabled = true,
        baseScale = 0.11f,
        landscapeXRes = R.integer.N3DS_BUTTON_A_X,
        landscapeYRes = R.integer.N3DS_BUTTON_A_Y,
        portraitXRes = R.integer.N3DS_BUTTON_A_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_A_PORTRAIT_Y
    ),
    BUTTON_B(
        toggleIndex = 1,
        id = NativeLibrary.ButtonType.BUTTON_B,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_B,
        shortLabel = "B",
        titleRes = R.string.button_b,
        defaultEnabled = true,
        baseScale = 0.11f,
        landscapeXRes = R.integer.N3DS_BUTTON_B_X,
        landscapeYRes = R.integer.N3DS_BUTTON_B_Y,
        portraitXRes = R.integer.N3DS_BUTTON_B_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_B_PORTRAIT_Y
    ),
    BUTTON_X(
        toggleIndex = 2,
        id = NativeLibrary.ButtonType.BUTTON_X,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_X,
        shortLabel = "X",
        titleRes = R.string.button_x,
        defaultEnabled = true,
        baseScale = 0.11f,
        landscapeXRes = R.integer.N3DS_BUTTON_X_X,
        landscapeYRes = R.integer.N3DS_BUTTON_X_Y,
        portraitXRes = R.integer.N3DS_BUTTON_X_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_X_PORTRAIT_Y
    ),
    BUTTON_Y(
        toggleIndex = 3,
        id = NativeLibrary.ButtonType.BUTTON_Y,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_Y,
        shortLabel = "Y",
        titleRes = R.string.button_y,
        defaultEnabled = true,
        baseScale = 0.11f,
        landscapeXRes = R.integer.N3DS_BUTTON_Y_X,
        landscapeYRes = R.integer.N3DS_BUTTON_Y_Y,
        portraitXRes = R.integer.N3DS_BUTTON_Y_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_Y_PORTRAIT_Y
    ),
    TRIGGER_L(
        toggleIndex = 4,
        id = NativeLibrary.ButtonType.TRIGGER_L,
        scaleKeyId = NativeLibrary.ButtonType.TRIGGER_L,
        shortLabel = "L",
        titleRes = R.string.button_l,
        defaultEnabled = true,
        baseScale = 0.18f,
        landscapeXRes = R.integer.N3DS_TRIGGER_L_X,
        landscapeYRes = R.integer.N3DS_TRIGGER_L_Y,
        portraitXRes = R.integer.N3DS_TRIGGER_L_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_TRIGGER_L_PORTRAIT_Y
    ),
    TRIGGER_R(
        toggleIndex = 5,
        id = NativeLibrary.ButtonType.TRIGGER_R,
        scaleKeyId = NativeLibrary.ButtonType.TRIGGER_R,
        shortLabel = "R",
        titleRes = R.string.button_r,
        defaultEnabled = true,
        baseScale = 0.18f,
        landscapeXRes = R.integer.N3DS_TRIGGER_R_X,
        landscapeYRes = R.integer.N3DS_TRIGGER_R_Y,
        portraitXRes = R.integer.N3DS_TRIGGER_R_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_TRIGGER_R_PORTRAIT_Y
    ),
    BUTTON_ZL(
        toggleIndex = 6,
        id = NativeLibrary.ButtonType.BUTTON_ZL,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_ZL,
        shortLabel = "ZL",
        titleRes = R.string.button_zl,
        defaultEnabled = false,
        baseScale = 0.18f,
        landscapeXRes = R.integer.N3DS_BUTTON_ZL_X,
        landscapeYRes = R.integer.N3DS_BUTTON_ZL_Y,
        portraitXRes = R.integer.N3DS_BUTTON_ZL_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_ZL_PORTRAIT_Y
    ),
    BUTTON_ZR(
        toggleIndex = 7,
        id = NativeLibrary.ButtonType.BUTTON_ZR,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_ZR,
        shortLabel = "ZR",
        titleRes = R.string.button_zr,
        defaultEnabled = false,
        baseScale = 0.18f,
        landscapeXRes = R.integer.N3DS_BUTTON_ZR_X,
        landscapeYRes = R.integer.N3DS_BUTTON_ZR_Y,
        portraitXRes = R.integer.N3DS_BUTTON_ZR_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_ZR_PORTRAIT_Y
    ),
    BUTTON_START(
        toggleIndex = 8,
        id = NativeLibrary.ButtonType.BUTTON_START,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_START,
        shortLabel = "START",
        titleRes = R.string.button_start,
        defaultEnabled = true,
        baseScale = 0.08f,
        landscapeXRes = R.integer.N3DS_BUTTON_START_X,
        landscapeYRes = R.integer.N3DS_BUTTON_START_Y,
        portraitXRes = R.integer.N3DS_BUTTON_START_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_START_PORTRAIT_Y
    ),
    BUTTON_SELECT(
        toggleIndex = 9,
        id = NativeLibrary.ButtonType.BUTTON_SELECT,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_SELECT,
        shortLabel = "SELECT",
        titleRes = R.string.button_select,
        defaultEnabled = true,
        baseScale = 0.08f,
        landscapeXRes = R.integer.N3DS_BUTTON_SELECT_X,
        landscapeYRes = R.integer.N3DS_BUTTON_SELECT_Y,
        portraitXRes = R.integer.N3DS_BUTTON_SELECT_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_SELECT_PORTRAIT_Y
    ),
    DPAD(
        toggleIndex = 10,
        id = NativeLibrary.ButtonType.DPAD_UP,
        scaleKeyId = NativeLibrary.ButtonType.DPAD,
        shortLabel = "D-Pad",
        titleRes = R.string.controller_dpad,
        defaultEnabled = true,
        baseScale = 0.22f,
        landscapeXRes = R.integer.N3DS_BUTTON_UP_X,
        landscapeYRes = R.integer.N3DS_BUTTON_UP_Y,
        portraitXRes = R.integer.N3DS_BUTTON_UP_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_UP_PORTRAIT_Y
    ),
    STICK_MAIN(
        toggleIndex = 11,
        id = NativeLibrary.ButtonType.STICK_LEFT,
        scaleKeyId = NativeLibrary.ButtonType.STICK_LEFT,
        shortLabel = "Circle Pad",
        titleRes = R.string.controller_circlepad,
        defaultEnabled = true,
        baseScale = 0.275f,
        landscapeXRes = R.integer.N3DS_STICK_MAIN_X,
        landscapeYRes = R.integer.N3DS_STICK_MAIN_Y,
        portraitXRes = R.integer.N3DS_STICK_MAIN_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_STICK_MAIN_PORTRAIT_Y
    ),
    STICK_C(
        toggleIndex = 12,
        id = NativeLibrary.ButtonType.STICK_C,
        scaleKeyId = NativeLibrary.ButtonType.STICK_C,
        shortLabel = "C-Stick",
        titleRes = R.string.controller_c,
        defaultEnabled = false,
        baseScale = 0.275f,
        landscapeXRes = R.integer.N3DS_STICK_C_X,
        landscapeYRes = R.integer.N3DS_STICK_C_Y,
        portraitXRes = R.integer.N3DS_STICK_C_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_STICK_C_PORTRAIT_Y
    ),
    BUTTON_HOME(
        toggleIndex = 13,
        id = NativeLibrary.ButtonType.BUTTON_HOME,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_HOME,
        shortLabel = "HOME",
        titleRes = R.string.button_home,
        defaultEnabled = false,
        baseScale = 0.08f,
        landscapeXRes = R.integer.N3DS_BUTTON_HOME_X,
        landscapeYRes = R.integer.N3DS_BUTTON_HOME_Y,
        portraitXRes = R.integer.N3DS_BUTTON_HOME_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_HOME_PORTRAIT_Y
    ),
    BUTTON_SWAP(
        toggleIndex = 14,
        id = NativeLibrary.ButtonType.BUTTON_SWAP,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_SWAP,
        shortLabel = "SWAP",
        titleRes = R.string.button_swap,
        defaultEnabled = false,
        baseScale = 0.08f,
        landscapeXRes = R.integer.N3DS_BUTTON_SWAP_X,
        landscapeYRes = R.integer.N3DS_BUTTON_SWAP_Y,
        portraitXRes = R.integer.N3DS_BUTTON_SWAP_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_SWAP_PORTRAIT_Y
    ),
    BUTTON_TURBO(
        toggleIndex = 15,
        id = NativeLibrary.ButtonType.BUTTON_TURBO,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_TURBO,
        shortLabel = "▶▶1",
        titleRes = R.string.button_turbo,
        defaultEnabled = false,
        baseScale = 0.10f,
        landscapeXRes = R.integer.N3DS_BUTTON_TURBO_X,
        landscapeYRes = R.integer.N3DS_BUTTON_TURBO_Y,
        portraitXRes = R.integer.N3DS_BUTTON_TURBO_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_TURBO_PORTRAIT_Y
    ),
    BUTTON_COMBO(
        toggleIndex = 16,
        id = Hotkey.COMBO_BUTTON.button,
        scaleKeyId = Hotkey.COMBO_BUTTON.button,
        shortLabel = "COMBO",
        titleRes = R.string.button_combo,
        defaultEnabled = false,
        baseScale = 0.11f,
        landscapeXRes = R.integer.N3DS_BUTTON_COMBO_X,
        landscapeYRes = R.integer.N3DS_BUTTON_COMBO_Y,
        portraitXRes = R.integer.N3DS_BUTTON_COMBO_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_COMBO_PORTRAIT_Y
    ),
    BUTTON_TURBO_2(
        toggleIndex = 17,
        id = NativeLibrary.ButtonType.BUTTON_TURBO_2,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_TURBO_2,
        shortLabel = "▶▶2",
        titleRes = R.string.button_turbo_2,
        defaultEnabled = false,
        baseScale = 0.10f,
        landscapeXRes = R.integer.N3DS_BUTTON_TURBO_2_X,
        landscapeYRes = R.integer.N3DS_BUTTON_TURBO_2_Y,
        portraitXRes = R.integer.N3DS_BUTTON_TURBO_2_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_TURBO_2_PORTRAIT_Y
    ),
    BUTTON_TURBO_3(
        toggleIndex = 18,
        id = NativeLibrary.ButtonType.BUTTON_TURBO_3,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_TURBO_3,
        shortLabel = "▶▶3",
        titleRes = R.string.button_turbo_3,
        defaultEnabled = false,
        baseScale = 0.10f,
        landscapeXRes = R.integer.N3DS_BUTTON_TURBO_3_X,
        landscapeYRes = R.integer.N3DS_BUTTON_TURBO_3_Y,
        portraitXRes = R.integer.N3DS_BUTTON_TURBO_3_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_TURBO_3_PORTRAIT_Y
    ),
    BUTTON_QUICKSAVE(
        toggleIndex = 19,
        id = NativeLibrary.ButtonType.BUTTON_QUICKSAVE,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_QUICKSAVE,
        shortLabel = "SAVE",
        titleRes = R.string.button_quicksave,
        defaultEnabled = false,
        baseScale = 0.10f,
        landscapeXRes = R.integer.N3DS_BUTTON_QUICKSAVE_X,
        landscapeYRes = R.integer.N3DS_BUTTON_QUICKSAVE_Y,
        portraitXRes = R.integer.N3DS_BUTTON_QUICKSAVE_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_QUICKSAVE_PORTRAIT_Y
    ),
    BUTTON_QUICKLOAD(
        toggleIndex = 20,
        id = NativeLibrary.ButtonType.BUTTON_QUICKLOAD,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_QUICKLOAD,
        shortLabel = "LOAD",
        titleRes = R.string.button_quickload,
        defaultEnabled = false,
        baseScale = 0.10f,
        landscapeXRes = R.integer.N3DS_BUTTON_QUICKLOAD_X,
        landscapeYRes = R.integer.N3DS_BUTTON_QUICKLOAD_Y,
        portraitXRes = R.integer.N3DS_BUTTON_QUICKLOAD_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_QUICKLOAD_PORTRAIT_Y
    ),
    BUTTON_PAUSE(
        toggleIndex = 21,
        id = NativeLibrary.ButtonType.BUTTON_PAUSE,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_PAUSE,
        shortLabel = "PAUSE",
        titleRes = R.string.button_pause,
        defaultEnabled = true,
        baseScale = 0.09f,
        landscapeXRes = R.integer.N3DS_BUTTON_PAUSE_X,
        landscapeYRes = R.integer.N3DS_BUTTON_PAUSE_Y,
        portraitXRes = R.integer.N3DS_BUTTON_PAUSE_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_PAUSE_PORTRAIT_Y
    ),
    BUTTON_SCREENSHOT(
        toggleIndex = 22,
        id = NativeLibrary.ButtonType.BUTTON_SCREENSHOT,
        scaleKeyId = NativeLibrary.ButtonType.BUTTON_SCREENSHOT,
        shortLabel = "SHOT",
        titleRes = R.string.button_screenshot,
        defaultEnabled = false,
        baseScale = 0.10f,
        landscapeXRes = R.integer.N3DS_BUTTON_SCREENSHOT_X,
        landscapeYRes = R.integer.N3DS_BUTTON_SCREENSHOT_Y,
        portraitXRes = R.integer.N3DS_BUTTON_SCREENSHOT_PORTRAIT_X,
        portraitYRes = R.integer.N3DS_BUTTON_SCREENSHOT_PORTRAIT_Y
    );

    val togglePrefKey: String
        get() = "buttonToggle$toggleIndex"

    val scalePrefKey: String
        get() = "controlScale-$scaleKeyId"

    fun xPrefKey(orientationSuffix: String): String = "$id$orientationSuffix-X"

    fun yPrefKey(orientationSuffix: String): String = "$id$orientationSuffix-Y"

    companion object {
        fun fromId(id: Int): OverlayControl? = entries.firstOrNull { it.id == id }

        fun fromScaleKeyId(scaleKeyId: Int): OverlayControl? =
            entries.firstOrNull { it.scaleKeyId == scaleKeyId }
    }
}
