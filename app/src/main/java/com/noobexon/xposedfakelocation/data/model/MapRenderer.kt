package com.noobexon.xposedfakelocation.data.model

/** Which library draws the map. [tag] is the stored value. */
enum class MapRenderer(val tag: String) {
    OSMDROID("osmdroid"),
    MAPLIBRE("maplibre");

    companion object {
        /** Unknown or missing tags fall back to [OSMDROID]. */
        fun fromTag(tag: String?): MapRenderer = entries.firstOrNull { it.tag == tag } ?: OSMDROID
    }
}
