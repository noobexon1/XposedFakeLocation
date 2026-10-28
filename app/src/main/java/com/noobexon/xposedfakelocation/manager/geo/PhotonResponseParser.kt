package com.noobexon.xposedfakelocation.manager.geo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** Turns a Photon GeoJSON `FeatureCollection` into [Place]s, skipping unusable features. */
object PhotonResponseParser {
    fun parse(json: String): List<Place> {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            throw GeocodingException(GeocodingException.Kind.PARSE, e)
        }
        val features = root.takeIf { it.isJsonObject }?.asJsonObject?.array("features")
            ?: throw GeocodingException(GeocodingException.Kind.PARSE)
        return features.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.let(::toPlace) }
    }

    private fun toPlace(feature: JsonObject): Place? {
        val coordinates = feature.obj("geometry")?.array("coordinates") ?: return null
        if (coordinates.size() < 2) return null
        val longitude = coordinates[0].doubleOrNull() ?: return null
        val latitude = coordinates[1].doubleOrNull() ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        val properties = feature.obj("properties") ?: return null
        val city = properties.text("city")
        val streetLine = listOfNotNull(properties.text("street"), properties.text("housenumber"))
            .joinToString(" ")
            .ifEmpty { null }
        val title = properties.text("name") ?: streetLine ?: city ?: return null
        val subtitle = listOfNotNull(city, properties.text("state"), properties.text("country"))
            .distinct()
            .filter { it != title }
            .joinToString(", ")
        return Place(title, subtitle, latitude, longitude)
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.ifEmpty { null }

    private fun JsonElement.doubleOrNull(): Double? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
}
