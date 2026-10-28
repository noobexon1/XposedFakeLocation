package com.noobexon.xposedfakelocation.data.backup

import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository

/**
 * Maps between [SettingsBackup] and the live [PreferencesRepository]. Serialization/validation is
 * delegated to [SettingsBackupCodec]; this class owns the write-back ("apply") policy, including
 * the remote-vs-local split.
 */
class SettingsBackupManager(
    private val repository: PreferencesRepository,
    private val codec: SettingsBackupCodec = SettingsBackupCodec(),
) {

    /** Serialises an already-built [backup] to JSON. */
    fun toJson(backup: SettingsBackup): String = codec.toJson(backup)

    /** Parses and validates [json]; see [SettingsBackupCodec.fromJson]. */
    fun parse(json: String): Result<SettingsBackup> = codec.fromJson(json)

    /**
     * Writes [backup] into the repository with **replace** semantics.
     *
     * Favorites, appearance, joystick settings, saved routes and the search and routing servers (local prefs) are always applied;
     * a missing joystick, routes, search or routing server section leaves those untouched. Spoofing values (remote prefs) are applied only
     * when [canWriteRemote] is `true`; otherwise they are skipped so the caller can tell the user to re-import once the module is active.
     */
    suspend fun apply(backup: SettingsBackup, canWriteRemote: Boolean) {
        // Local parts — always safe to write.
        repository.replaceFavorites(backup.favorites)
        repository.saveThemeOption(backup.appearance.themeOption)
        repository.saveLanguageTag(backup.appearance.languageTag)
        backup.joystick?.let { repository.saveJoystickSettings(it.toSettings()) }
        backup.routes?.let { repository.replaceRoutes(it) }
        backup.geocodingServerUrl?.let { repository.saveGeocodingServerUrl(it) }
        backup.routingServerUrl?.let { repository.saveRoutingServerUrl(it) }

        if (!canWriteRemote) return

        val s = backup.spoofing
        repository.saveUseRandomize(s.useRandomize)
        repository.saveRandomizeRadius(s.randomizeRadius)
        repository.saveUseAccuracy(s.useAccuracy)
        repository.saveAccuracy(s.accuracy)
        repository.saveUseAltitude(s.useAltitude)
        repository.saveAltitude(s.altitude)
        repository.saveUseVerticalAccuracy(s.useVerticalAccuracy)
        repository.saveVerticalAccuracy(s.verticalAccuracy)
        repository.saveUseMeanSeaLevel(s.useMeanSeaLevel)
        repository.saveMeanSeaLevel(s.meanSeaLevel)
        repository.saveUseMeanSeaLevelAccuracy(s.useMeanSeaLevelAccuracy)
        repository.saveMeanSeaLevelAccuracy(s.meanSeaLevelAccuracy)
        repository.saveUseSpeed(s.useSpeed)
        repository.saveSpeed(s.speed)
        repository.saveUseSpeedAccuracy(s.useSpeedAccuracy)
        repository.saveSpeedAccuracy(s.speedAccuracy)
        repository.saveHideFakeLocationToast(s.hideFakeLocationToast)
        repository.saveTargetApps(s.targetApps.toSet())
        repository.saveEnableWifiIdentity(s.wifiIdentityEnabled)
        repository.saveWifiSsid(s.wifiSsid)
        repository.saveWifiBssid(s.wifiBssid)
        repository.saveWifiRssi(s.wifiRssi)
    }
}
