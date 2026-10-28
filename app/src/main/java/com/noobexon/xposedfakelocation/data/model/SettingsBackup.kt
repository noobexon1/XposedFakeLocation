package com.noobexon.xposedfakelocation.data.model

import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import com.noobexon.xposedfakelocation.data.JOYSTICK_MIN_SPEED_MPS

/** Current on-disk schema version for exported settings. Bump only with a migration. */
const val SETTINGS_BACKUP_VERSION = 1

/** Root of an exported settings file. */
data class SettingsBackup(
    val version: Int,
    val exportedAt: Long,
    val appVersion: String,
    val spoofing: SpoofingBackup,
    val favorites: List<FavoriteLocation>,
    val appearance: AppearanceBackup,
    /** Absent in files exported before the joystick feature; import then leaves joystick settings untouched. */
    val joystick: JoystickBackup? = null,
    /** Absent in files exported before the route feature; import then leaves saved routes untouched. */
    val routes: List<Route>? = null,
    /** Absent in files exported before address search; import then leaves the search server untouched. */
    val geocodingServerUrl: String? = null,
    /** Absent in files exported before road routing; import then leaves the routing server untouched. */
    val routingServerUrl: String? = null,
)

/** All spoofing-related values (LSPosed remote prefs). */
data class SpoofingBackup(
    val useRandomize: Boolean,
    val randomizeRadius: Double,
    val useAccuracy: Boolean,
    val accuracy: Double,
    val useAltitude: Boolean,
    val altitude: Double,
    val useVerticalAccuracy: Boolean,
    val verticalAccuracy: Float,
    val useMeanSeaLevel: Boolean,
    val meanSeaLevel: Double,
    val useMeanSeaLevelAccuracy: Boolean,
    val meanSeaLevelAccuracy: Float,
    val useSpeed: Boolean,
    val speed: Float,
    val useSpeedAccuracy: Boolean,
    val speedAccuracy: Float,
    val hideFakeLocationToast: Boolean,
    val targetApps: List<String>,
    val wifiIdentityEnabled: Boolean,
    val wifiSsid: String,
    val wifiBssid: String,
    val wifiRssi: Int,
)

/** Manager-only appearance preferences (local prefs). */
data class AppearanceBackup(
    val themeOption: String,
    val languageTag: String,
)

/** Manager-only joystick preferences (local prefs). Speeds in m/s; enums stored by name. */
data class JoystickBackup(
    val mode: String,
    val maxSpeed: Float,
    val walkSpeed: Float,
    val runSpeed: Float,
    val bikeSpeed: Float,
    val driveSpeed: Float,
    val selectedPreset: String,
) {
    fun isValid(): Boolean = listOf(maxSpeed, walkSpeed, runSpeed, bikeSpeed, driveSpeed)
        .all { it.isFinite() && it in JOYSTICK_MIN_SPEED_MPS..JOYSTICK_MAX_SPEED_MPS }

    fun toSettings(): JoystickSettings {
        val defaults = JoystickSettings()
        return JoystickSettings(
            mode = JoystickMode.entries.firstOrNull { it.name == mode } ?: defaults.mode,
            maxSpeedMps = maxSpeed,
            walkSpeedMps = walkSpeed,
            runSpeedMps = runSpeed,
            bikeSpeedMps = bikeSpeed,
            driveSpeedMps = driveSpeed,
            selectedPreset = JoystickPreset.entries.firstOrNull { it.name == selectedPreset } ?: defaults.selectedPreset,
        )
    }

    companion object {
        fun from(settings: JoystickSettings) = JoystickBackup(
            mode = settings.mode.name,
            maxSpeed = settings.maxSpeedMps,
            walkSpeed = settings.walkSpeedMps,
            runSpeed = settings.runSpeedMps,
            bikeSpeed = settings.bikeSpeedMps,
            driveSpeed = settings.driveSpeedMps,
            selectedPreset = settings.selectedPreset.name,
        )
    }
}
