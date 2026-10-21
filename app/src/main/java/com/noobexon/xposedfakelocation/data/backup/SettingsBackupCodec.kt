package com.noobexon.xposedfakelocation.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup

/** Thrown (wrapped in [Result]) when a settings file cannot be parsed or is unsupported. */
class SettingsBackupException(message: String) : Exception(message)

/**
 * Pure-Kotlin JSON (de)serialization and validation for [SettingsBackup]. Holds no Android
 * dependencies so it can be unit-tested directly.
 */
class SettingsBackupCodec(
    private val gson: Gson = GsonBuilder().create()
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
        if (parsed.spoofing == null || parsed.appearance == null) {
            throw SettingsBackupException("Settings file is missing required sections")
        }
        val favorites = (parsed.favorites ?: emptyList()).filter { fav ->
            fav.name.isNotBlank() &&
                fav.latitude.isFinite() &&
                fav.longitude.isFinite()
        }
        parsed.copy(favorites = favorites)
    }
}
