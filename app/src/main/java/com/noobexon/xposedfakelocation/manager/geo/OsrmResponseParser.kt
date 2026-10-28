package com.noobexon.xposedfakelocation.manager.geo

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.noobexon.xposedfakelocation.data.EncodedPolyline

/** Reads OSRM `/route` responses. */
object OsrmResponseParser {
    private val NO_ROUTE_CODES = setOf("NoRoute", "NoSegment")

    fun parse(json: String): RoadGeometry {
        val root = parseJson(json)?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw RoutingException(RoutingException.Kind.PARSE)
        when (root.text("code")) {
            "Ok" -> Unit
            in NO_ROUTE_CODES -> throw RoutingException(RoutingException.Kind.NO_ROUTE)
            null -> throw RoutingException(RoutingException.Kind.PARSE)
            else -> throw RoutingException(RoutingException.Kind.SERVER)
        }
        val route = root.get("routes")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw RoutingException(RoutingException.Kind.PARSE)
        val geometry = route.text("geometry") ?: throw RoutingException(RoutingException.Kind.PARSE)
        val distance = route.get("distance")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: throw RoutingException(RoutingException.Kind.PARSE)
        if ((EncodedPolyline.decode(geometry)?.size ?: 0) < 2) throw RoutingException(RoutingException.Kind.PARSE)
        return RoadGeometry(geometry, distance)
    }

    /** OSRM answers "no route" with a non-2xx status; everything else non-2xx is a server problem. */
    fun errorKind(body: String?): RoutingException.Kind {
        val code = body?.let(::parseJson)?.takeIf { it.isJsonObject }?.asJsonObject?.text("code")
        return if (code in NO_ROUTE_CODES) RoutingException.Kind.NO_ROUTE else RoutingException.Kind.SERVER
    }

    private fun parseJson(json: String): JsonElement? = try {
        JsonParser.parseString(json)
    } catch (e: JsonParseException) {
        null
    }

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
