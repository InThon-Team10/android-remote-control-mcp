package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** kotlinx `JsonElement` <-> the plain `Map` values the Gen AI SDK expects, converted through Jackson. */
internal object JsonBridge {
    private val mapper = ObjectMapper()

    fun toMap(element: JsonElement): Map<String, Any?> =
        mapper.readValue(element.toString(), object : TypeReference<Map<String, Any?>>() {})

    fun toJsonObject(map: Map<String, Any?>): JsonObject {
        val json = mapper.writeValueAsString(map)
        return Json.parseToJsonElement(json).jsonObject
    }
}
