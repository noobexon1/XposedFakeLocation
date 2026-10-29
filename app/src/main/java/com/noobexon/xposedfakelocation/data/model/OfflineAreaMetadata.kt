package com.noobexon.xposedfakelocation.data.model

import com.google.gson.Gson
import com.google.gson.JsonParseException

/**
 * Stored as JSON bytes in each MapLibre offline region. All regions of one offline area (one per
 * style) share [areaId]. [style] is one of [OfflineAreaStyle].
 */
data class OfflineAreaMetadata(
    val areaId: String,
    val name: String,
    val createdAt: Long,
    val style: String,
    val styleUrl: String,
)

/** Values of [OfflineAreaMetadata.style]. */
object OfflineAreaStyle {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val CUSTOM = "custom"
}

/** JSON (de)serialization of [OfflineAreaMetadata]. */
object OfflineAreaMetadataCodec {
    private val gson = Gson()

    fun encode(metadata: OfflineAreaMetadata): ByteArray = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

    /** `null` for bytes that aren't a complete metadata object. */
    fun decode(bytes: ByteArray?): OfflineAreaMetadata? {
        if (bytes == null || bytes.isEmpty()) return null
        val parsed = try {
            gson.fromJson(String(bytes, Charsets.UTF_8), OfflineAreaMetadata::class.java)
        } catch (e: JsonParseException) {
            null
        } ?: return null
        // Gson bypasses Kotlin null-safety, so missing fields arrive as null.
        @Suppress("SENSELESS_COMPARISON")
        val complete = parsed.areaId != null && parsed.name != null && parsed.style != null && parsed.styleUrl != null
        return parsed.takeIf { complete && it.areaId.isNotBlank() && it.name.isNotBlank() }
    }
}
