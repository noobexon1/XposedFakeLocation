package com.noobexon.xposedfakelocation.data.model

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
