// Copyright 2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.overlay

import androidx.preference.PreferenceManager
import java.io.File
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.utils.EmulationMenuSettings
import org.citra.citra_emu.utils.TurboHelper
import org.json.JSONArray
import org.json.JSONObject

data class OverlayControlState(
    val enabled: Boolean,
    val x: Float,
    val y: Float,
    val scale: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("scale", scale)
    }

    companion object {
        fun fromJson(json: JSONObject, defaultControl: OverlayControl): OverlayControlState =
            OverlayControlState(
                enabled = json.optBoolean("enabled", defaultControl.defaultEnabled),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                scale = json.optInt("scale", 50).coerceIn(0, 150)
            )
    }
}

data class OverlayOrientationLayout(
    val controls: Map<String, OverlayControlState>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        for ((key, value) in controls) {
            put(key, value.toJson())
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): OverlayOrientationLayout {
            if (json == null) return OverlayOrientationLayout(emptyMap())
            val map = LinkedHashMap<String, OverlayControlState>()
            for (control in OverlayControl.entries) {
                val obj = json.optJSONObject(control.name) ?: continue
                map[control.name] = OverlayControlState.fromJson(obj, control)
            }
            return OverlayOrientationLayout(map)
        }
    }
}

data class OverlayProfile(
    val name: String,
    val landscape: OverlayOrientationLayout,
    val portrait: OverlayOrientationLayout,
    val globalScale: Int = 50,
    val opacity: Int = 50,
    val buttonSlide: Int = 0,
    val dpadSlide: Boolean = true,
    val joystickRelCenter: Boolean = true,
    val turboSpeed1: Int = 200,
    val turboSpeed2: Int = 300,
    val turboSpeed3: Int = 400
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("landscape", landscape.toJson())
        put("portrait", portrait.toJson())
        put("globalScale", globalScale)
        put("opacity", opacity)
        put("buttonSlide", buttonSlide)
        put("dpadSlide", dpadSlide)
        put("joystickRelCenter", joystickRelCenter)
        put("turboSpeed1", turboSpeed1)
        put("turboSpeed2", turboSpeed2)
        put("turboSpeed3", turboSpeed3)
    }

    companion object {
        fun fromJson(json: JSONObject): OverlayProfile? {
            val name = json.optString("name", "").trim()
            if (name.isEmpty()) return null
            return OverlayProfile(
                name = name,
                landscape = OverlayOrientationLayout.fromJson(json.optJSONObject("landscape")),
                portrait = OverlayOrientationLayout.fromJson(json.optJSONObject("portrait")),
                globalScale = json.optInt("globalScale", 50).coerceIn(0, 150),
                opacity = json.optInt("opacity", 50).coerceIn(0, 100),
                buttonSlide = json.optInt("buttonSlide", 0),
                dpadSlide = json.optBoolean("dpadSlide", true),
                joystickRelCenter = json.optBoolean("joystickRelCenter", true),
                turboSpeed1 = json.optInt("turboSpeed1", 200),
                turboSpeed2 = json.optInt("turboSpeed2", 300),
                turboSpeed3 = json.optInt("turboSpeed3", 400)
            )
        }
    }
}

object OverlayProfileManager {
    private const val KEY_PROFILES = "overlay.profiles.json"
    private const val KEY_ACTIVE_PROFILE = "overlay.activeProfile"
    private const val KEY_ACTIVE_GAME_PREFIX = "overlay.active.game."
    private const val KEY_GRID_SNAP = "overlay.gridSnap"
    private const val PROFILE_FILE_SUFFIX = ".azahar.touch.json"
    const val DEFAULT_PROFILE_NAME = "Default"

    private val preferences
        get() = PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)

    private var loaded = false
    private val profiles = mutableListOf<OverlayProfile>()
    private var editSessionSnapshot: OverlayProfile? = null

    var activeProfileName: String = DEFAULT_PROFILE_NAME
        private set

    var gridSnapEnabled: Boolean
        get() = preferences.getBoolean(KEY_GRID_SNAP, false)
        set(value) {
            preferences.edit().putBoolean(KEY_GRID_SNAP, value).apply()
        }

    fun getProfiles(): List<OverlayProfile> {
        ensureLoaded()
        return profiles.toList()
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        profiles.clear()

        val rawJson = preferences.getString(KEY_PROFILES, null)
        if (!rawJson.isNullOrEmpty()) {
            runCatching {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    OverlayProfile.fromJson(obj)?.let { profiles.add(it) }
                }
            }
        }

        importFolderProfiles()

        if (profiles.isEmpty()) {
            profiles.add(captureCurrentPreferencesAsProfile(DEFAULT_PROFILE_NAME))
        }

        val savedActive =
            preferences.getString(KEY_ACTIVE_PROFILE, DEFAULT_PROFILE_NAME) ?: DEFAULT_PROFILE_NAME
        activeProfileName = if (profiles.any { it.name == savedActive }) {
            savedActive
        } else {
            profiles.first().name
        }
        persistProfiles()
    }

    fun captureCurrentPreferencesAsProfile(name: String): OverlayProfile {
        val landscapeMap = LinkedHashMap<String, OverlayControlState>()
        val portraitMap = LinkedHashMap<String, OverlayControlState>()

        for (control in OverlayControl.entries) {
            val enabled = preferences.getBoolean(control.togglePrefKey, control.defaultEnabled)
            val scale = preferences.getInt(control.scalePrefKey, 50).coerceIn(0, 150)

            val landX = preferences.getFloat(control.xPrefKey(""), 0f)
            val landY = preferences.getFloat(control.yPrefKey(""), 0f)
            landscapeMap[control.name] = OverlayControlState(enabled, landX, landY, scale)

            val portX = preferences.getFloat(control.xPrefKey("-Portrait"), 0f)
            val portY = preferences.getFloat(control.yPrefKey("-Portrait"), 0f)
            portraitMap[control.name] = OverlayControlState(enabled, portX, portY, scale)
        }

        return OverlayProfile(
            name = name,
            landscape = OverlayOrientationLayout(landscapeMap),
            portrait = OverlayOrientationLayout(portraitMap),
            globalScale = preferences.getInt("controlScale", 50).coerceIn(0, 150),
            opacity = preferences.getInt("controlOpacity", 50).coerceIn(0, 100),
            buttonSlide = EmulationMenuSettings.buttonSlide,
            dpadSlide = EmulationMenuSettings.dpadSlide,
            joystickRelCenter = EmulationMenuSettings.joystickRelCenter,
            turboSpeed1 = TurboHelper.getTurboSpeed(1),
            turboSpeed2 = TurboHelper.getTurboSpeed(2),
            turboSpeed3 = TurboHelper.getTurboSpeed(3)
        )
    }

    fun applyProfileToPreferences(profile: OverlayProfile) {
        val editor = preferences.edit()
        editor.putInt("controlScale", profile.globalScale)
        editor.putInt("controlOpacity", profile.opacity)

        for (control in OverlayControl.entries) {
            val landState = profile.landscape.controls[control.name]
            val portState = profile.portrait.controls[control.name]
            val activeState = landState ?: portState
            if (activeState != null) {
                editor.putBoolean(control.togglePrefKey, activeState.enabled)
                editor.putInt(control.scalePrefKey, activeState.scale)
            }
            if (landState != null && (landState.x != 0f || landState.y != 0f)) {
                editor.putFloat(control.xPrefKey(""), landState.x)
                editor.putFloat(control.yPrefKey(""), landState.y)
            }
            if (portState != null && (portState.x != 0f || portState.y != 0f)) {
                editor.putFloat(control.xPrefKey("-Portrait"), portState.x)
                editor.putFloat(control.yPrefKey("-Portrait"), portState.y)
            }
        }
        editor.apply()

        EmulationMenuSettings.buttonSlide = profile.buttonSlide
        EmulationMenuSettings.dpadSlide = profile.dpadSlide
        EmulationMenuSettings.joystickRelCenter = profile.joystickRelCenter
        TurboHelper.setTurboSpeed(1, profile.turboSpeed1)
        TurboHelper.setTurboSpeed(2, profile.turboSpeed2)
        TurboHelper.setTurboSpeed(3, profile.turboSpeed3)
    }

    fun beginEditSession() {
        ensureLoaded()
        editSessionSnapshot = captureCurrentPreferencesAsProfile(activeProfileName)
    }

    fun commitEditSession() {
        ensureLoaded()
        val updated = captureCurrentPreferencesAsProfile(activeProfileName)
        val index = profiles.indexOfFirst { it.name == activeProfileName }
        if (index >= 0) {
            profiles[index] = updated
        } else {
            profiles.add(updated)
        }
        editSessionSnapshot = null
        persistProfiles()
    }

    fun discardEditSession() {
        ensureLoaded()
        val snapshot = editSessionSnapshot
        if (snapshot != null) {
            applyProfileToPreferences(snapshot)
            editSessionSnapshot = null
        }
    }

    fun saveAsNewProfile(name: String, bindToCurrentGame: Boolean = true): Boolean {
        ensureLoaded()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val newProfile = captureCurrentPreferencesAsProfile(trimmed)
        val existingIdx = profiles.indexOfFirst { it.name.equals(trimmed, ignoreCase = false) }
        if (existingIdx >= 0) {
            profiles[existingIdx] = newProfile
        } else {
            profiles.add(newProfile)
        }
        activeProfileName = trimmed
        editSessionSnapshot = newProfile
        if (bindToCurrentGame) {
            bindProfileToRunningGame(trimmed)
        }
        persistProfiles()
        return true
    }

    fun switchProfile(name: String, bindToCurrentGame: Boolean = true): Boolean {
        ensureLoaded()
        val match = profiles.firstOrNull { it.name == name } ?: return false
        activeProfileName = match.name
        applyProfileToPreferences(match)
        if (editSessionSnapshot != null) {
            editSessionSnapshot = captureCurrentPreferencesAsProfile(match.name)
        }
        if (bindToCurrentGame) {
            bindProfileToRunningGame(match.name)
        }
        persistProfiles()
        return true
    }

    fun deleteProfile(name: String): Boolean {
        ensureLoaded()
        if (profiles.size <= 1) return false
        val idx = profiles.indexOfFirst { it.name == name }
        if (idx < 0) return false
        profiles.removeAt(idx)
        clearGameBindingsForProfile(name)
        if (activeProfileName == name) {
            val fallback = profiles.first()
            activeProfileName = fallback.name
            applyProfileToPreferences(fallback)
        }
        persistProfiles()
        return true
    }

    fun applyProfileForRunningGame() {
        ensureLoaded()
        val titleId = runCatching { NativeLibrary.getRunningTitleId() }.getOrDefault(0L)
        if (titleId == 0L) return
        val key = "$KEY_ACTIVE_GAME_PREFIX${String.format("%016X", titleId)}"
        val boundProfileName = preferences.getString(key, null) ?: return
        val match = profiles.firstOrNull { it.name == boundProfileName } ?: return
        if (activeProfileName != match.name) {
            activeProfileName = match.name
            applyProfileToPreferences(match)
        }
    }

    fun getBoundProfileForRunningGame(): String? {
        val titleId = runCatching { NativeLibrary.getRunningTitleId() }.getOrDefault(0L)
        if (titleId == 0L) return null
        val key = "$KEY_ACTIVE_GAME_PREFIX${String.format("%016X", titleId)}"
        return preferences.getString(key, null)
    }

    private fun bindProfileToRunningGame(profileName: String) {
        val titleId = runCatching { NativeLibrary.getRunningTitleId() }.getOrDefault(0L)
        if (titleId == 0L) return
        val key = "$KEY_ACTIVE_GAME_PREFIX${String.format("%016X", titleId)}"
        preferences.edit().putString(key, profileName).apply()
    }

    private fun clearGameBindingsForProfile(profileName: String) {
        val editor = preferences.edit()
        for ((k, v) in preferences.all) {
            if (k.startsWith(KEY_ACTIVE_GAME_PREFIX) && v == profileName) {
                editor.remove(k)
            }
        }
        editor.apply()
    }

    private fun persistProfiles() {
        val arr = JSONArray()
        for (p in profiles) {
            arr.put(p.toJson())
        }
        preferences.edit()
            .putString(KEY_PROFILES, arr.toString())
            .putString(KEY_ACTIVE_PROFILE, activeProfileName)
            .apply()
        syncProfilesFolder()
    }

    private fun profilesDir(): File? {
        val userDir = runCatching { NativeLibrary.getUserDirectory() }.getOrDefault("")
        if (userDir.isEmpty()) return null
        val dir = File(userDir, "inputprofiles")
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    private fun fileNameFor(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _.-]"), "_") + PROFILE_FILE_SUFFIX

    private fun syncProfilesFolder() {
        val dir = profilesDir() ?: return
        runCatching {
            val wanted = HashMap<String, OverlayProfile>()
            for (p in profiles) {
                wanted[fileNameFor(p.name)] = p
            }
            for ((fileName, profile) in wanted) {
                File(dir, fileName).writeText(profile.toJson().toString(2))
            }
            dir.listFiles { f -> f.name.endsWith(PROFILE_FILE_SUFFIX) }?.forEach { f ->
                if (f.name !in wanted) {
                    runCatching { f.delete() }
                }
            }
        }
    }

    private fun importFolderProfiles() {
        val dir = profilesDir() ?: return
        runCatching {
            val existingNames = profiles.map { it.name }.toHashSet()
            dir.listFiles { f -> f.name.endsWith(PROFILE_FILE_SUFFIX) }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    runCatching {
                        val profile = OverlayProfile.fromJson(JSONObject(file.readText()))
                        if (profile != null && profile.name !in existingNames) {
                            profiles.add(profile)
                            existingNames.add(profile.name)
                        }
                    }
                }
        }
    }
}
