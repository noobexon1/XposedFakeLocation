package com.noobexon.xposedfakelocation.data.backup

import com.google.gson.Gson
import com.noobexon.xposedfakelocation.data.model.MapRenderer
import com.noobexon.xposedfakelocation.data.model.RouteJson
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.model.isValid
import com.noobexon.xposedfakelocation.data.model.sanitizeRoadPaths
import com.noobexon.xposedfakelocation.data.model.withSanitizedLabels
import com.noobexon.xposedfakelocation.data.normalizeMapStyleUrl
import com.noobexon.xposedfakelocation.data.normalizeRoutingServerUrl
import com.noobexon.xposedfakelocation.data.normalizeServerUrl

/** Thrown (wrapped in [Result]) when a settings file cannot be parsed or is unsupported. */
class SettingsBackupException(message: String) : Exception(message)

/**
 * Pure-Kotlin JSON (de)serialization and validation for [SettingsBackup]. Holds no Android
 * dependencies so it can be unit-tested directly.
 */
class SettingsBackupCodec(
    private val gson: Gson = RouteJson.gson
) {

    fun toJson(backup: SettingsBackup): String = gson.toJson(backup)

    /**
     * Parses [json] into a [SettingsBackup]. Returns [Result.failure] with a
     * [SettingsBackupException] for malformed input, a missing root object, or an unsupported
     * [SettingsBackup.version]. Favorites with a blank name or non-finite coordinates are dropped
     * rather than failing the whole import.
     */
    fun fromJson(json: String): Result<SettingsBackup> = runCatching {
        val parsed = gson.fromJson(json, SettingsBackup::class.java)
            ?: throw SettingsBackupException("Empty or non-object settings file")
        if (parsed.version != SETTINGS_BACKUP_VERSION) {
            throw SettingsBackupException("Unsupported settings version: ${parsed.version}")
        }
        // Gson bypasses Kotlin non-null guarantees via Unsafe; these sections may actually be null.
        @Suppress("SENSELESS_COMPARISON")
        val hasMissingSections = parsed.spoofing == null || parsed.appearance == null
        if (hasMissingSections) {
            throw SettingsBackupException("Settings file is missing required sections")
        }
        val favorites = (parsed.favorites ?: emptyList()).filter { fav ->
            // Gson may leave name null even though Kotlin types say non-null (missing JSON field).
            @Suppress("SENSELESS_COMPARISON")
            val nameOk = fav.name != null && fav.name.isNotBlank()
            nameOk &&
                fav.latitude.isFinite() &&
                fav.longitude.isFinite()
        }
        @Suppress("SENSELESS_COMPARISON")
        val routes = parsed.routes?.filter { it != null && it.isValid() }?.map { it.withSanitizedLabels().sanitizeRoadPaths() }
        parsed.copy(
            favorites = favorites,
            joystick = parsed.joystick?.takeIf { it.isValid() },
            routes = routes,
            geocodingServerUrl = normalizeServerUrl(parsed.geocodingServerUrl),
            routingServerUrl = normalizeRoutingServerUrl(parsed.routingServerUrl),
            mapRenderer = parsed.mapRenderer?.takeIf { tag -> MapRenderer.entries.any { it.tag == tag } },
            mapStyleUrl = normalizeMapStyleUrl(parsed.mapStyleUrl),
        )
    }
}
