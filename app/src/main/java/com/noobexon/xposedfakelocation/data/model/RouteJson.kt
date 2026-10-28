package com.noobexon.xposedfakelocation.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/** Gson setup and tolerant parsing for stored and backed-up routes. */
object RouteJson {
    val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(SpeedChoice::class.java, SpeedChoiceAdapter)
        .create()

    private val routeListType: Type = object : TypeToken<List<Route?>>() {}.type

    /** Saved routes from [json]; malformed input gives an empty list and invalid routes are dropped. */
    fun parseRoutes(json: String?): List<Route> {
        if (json.isNullOrBlank()) return emptyList()
        val parsed: List<Route?> = try {
            gson.fromJson<List<Route?>?>(json, routeListType)
        } catch (e: RuntimeException) {
            null
        } ?: return emptyList()
        return parsed.filterNotNull().filter { it.isValid() }
    }

    /** The editing draft from [json], or `null` when it is missing or broken. */
    fun parseDraft(json: String?): RouteDraft? {
        if (json.isNullOrBlank()) return null
        val draft: RouteDraft = try {
            gson.fromJson<RouteDraft?>(json, RouteDraft::class.java)
        } catch (e: RuntimeException) {
            null
        } ?: return null
        @Suppress("SENSELESS_COMPARISON")
        val routeOk = draft.route != null && draft.route.isValid(requireName = false, minPoints = 0)
        return draft.takeIf { routeOk }
    }
}

/** Writes `{"type":"preset","preset":"WALK"}` or `{"type":"custom","speedMps":4.2}`; unknown shapes read as `null`. */
private object SpeedChoiceAdapter : JsonSerializer<SpeedChoice>, JsonDeserializer<SpeedChoice> {
    override fun serialize(src: SpeedChoice, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonObject().apply {
            when (src) {
                is SpeedChoice.Preset -> {
                    addProperty("type", "preset")
                    addProperty("preset", src.preset.name)
                }
                is SpeedChoice.Custom -> {
                    addProperty("type", "custom")
                    addProperty("speedMps", src.speedMps)
                }
            }
        }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SpeedChoice? {
        val obj = json.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return when (obj.string("type")) {
            "preset" -> JoystickPreset.entries.firstOrNull { it.name == obj.string("preset") }?.let(SpeedChoice::Preset)
            "custom" -> obj.get("speedMps")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asFloat
                ?.let(SpeedChoice::Custom)
            else -> null
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
