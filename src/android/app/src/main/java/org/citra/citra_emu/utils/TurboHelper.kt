// Copyright 2025-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.utils

import android.widget.Toast
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.CitraSettings
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.features.settings.utils.SettingsFile

object TurboHelper {
    const val TURBO_SLOT_COUNT = 3
    const val TURBO_MIN_SPEED = 50
    const val TURBO_MAX_SPEED = 600
    const val TURBO_UNLIMITED_SPEED = 605

    private val defaultSlotSpeeds = intArrayOf(200, 200, 300, 400)

    private var turboSpeedEnabled = false
    var activeSlot = 0
        private set

    private val preferences
        get() = PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)

    fun isTurboSpeedEnabled(): Boolean = turboSpeedEnabled

    fun isSlotActive(slot: Int): Boolean = turboSpeedEnabled && activeSlot == slot

    fun slotForButtonId(buttonId: Int): Int = when (buttonId) {
        NativeLibrary.ButtonType.BUTTON_TURBO -> 1
        NativeLibrary.ButtonType.BUTTON_TURBO_2 -> 2
        NativeLibrary.ButtonType.BUTTON_TURBO_3 -> 3
        else -> 0
    }

    fun getTurboSpeed(slot: Int): Int {
        val clampedSlot = slot.coerceIn(1, TURBO_SLOT_COUNT)
        val key = "turboSpeed$clampedSlot"
        if (clampedSlot == 1 && !preferences.contains(key)) {
            return runCatching {
                CitraSettings.activeSettings.get(IntSetting.TURBO_LIMIT)
            }.getOrDefault(defaultSlotSpeeds[1]).coerceIn(TURBO_MIN_SPEED, TURBO_UNLIMITED_SPEED)
        }
        val def = defaultSlotSpeeds[clampedSlot]
        return preferences.getInt(key, def).coerceIn(TURBO_MIN_SPEED, TURBO_UNLIMITED_SPEED)
    }

    fun setTurboSpeed(slot: Int, speed: Int) {
        val clampedSlot = slot.coerceIn(1, TURBO_SLOT_COUNT)
        val clampedSpeed = speed.coerceIn(TURBO_MIN_SPEED, TURBO_UNLIMITED_SPEED)
        preferences.edit().putInt("turboSpeed$clampedSlot", clampedSpeed).apply()
        if (clampedSlot == 1 && clampedSpeed <= 400) {
            runCatching {
                CitraSettings.activeSettings.setByCurrentState(
                    IntSetting.TURBO_LIMIT,
                    clampedSpeed.coerceIn(100, 400)
                )
                SettingsFile.saveSetting(IntSetting.TURBO_LIMIT, CitraSettings.activeSettings)
            }
        }
        if (turboSpeedEnabled && activeSlot == clampedSlot) {
            applyActiveSpeed()
        }
    }

    fun formatSpeedLabel(speed: Int): String =
        if (speed <= 0 || speed >= TURBO_UNLIMITED_SPEED) "∞" else "$speed%"

    fun getSlotSpeedLabel(slot: Int): String = formatSpeedLabel(getTurboSpeed(slot))

    private fun applyActiveSpeed() {
        val slot = if (activeSlot in 1..TURBO_SLOT_COUNT) activeSlot else 1
        val speed = getTurboSpeed(slot)
        val nativeLimit =
            if (speed <= 0 || speed >= TURBO_UNLIMITED_SPEED) 0.0 else speed.toDouble()
        NativeLibrary.setTemporaryFrameLimit(nativeLimit)
    }

    fun reloadTurbo(showToast: Boolean) {
        val context = CitraApplication.appContext
        val toastMessage: String

        if (turboSpeedEnabled) {
            if (activeSlot !in 1..TURBO_SLOT_COUNT) {
                activeSlot = 1
            }
            applyActiveSpeed()
            val speedLabel = getSlotSpeedLabel(activeSlot)
            toastMessage = "${context.getString(R.string.turbo_enabled_toast)} ($speedLabel)"
        } else {
            activeSlot = 0
            NativeLibrary.disableTemporaryFrameLimit()
            toastMessage = context.getString(R.string.turbo_disabled_toast)
        }

        if (showToast) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun setTurboEnabled(state: Boolean, showToast: Boolean, slot: Int = 1) {
        turboSpeedEnabled = state
        activeSlot = if (state) slot.coerceIn(1, TURBO_SLOT_COUNT) else 0
        reloadTurbo(showToast)
    }

    fun toggleTurbo(showToast: Boolean) {
        toggleTurboSlot(1, showToast)
    }

    fun toggleTurboSlot(slot: Int, showToast: Boolean = true) {
        val targetSlot = slot.coerceIn(1, TURBO_SLOT_COUNT)
        if (turboSpeedEnabled && activeSlot == targetSlot) {
            setTurboEnabled(false, showToast, targetSlot)
        } else {
            setTurboEnabled(true, showToast, targetSlot)
        }
    }
}
