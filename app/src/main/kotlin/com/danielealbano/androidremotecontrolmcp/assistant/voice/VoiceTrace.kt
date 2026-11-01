package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Tag shared by the content-free voice traces (event types, sizes and timings only; never transcript text). */
internal const val VOICE_TRACE = "MCP:VoiceTrace"

/** One-line description of a Realtime server event for traces: type and payload size, never text. */
internal fun realtimeEventTrace(event: JsonObject): String {
    val type = event["type"]?.jsonPrimitive?.contentOrNull ?: "other"
    val size = (event["delta"] ?: event["transcript"])?.jsonPrimitive?.contentOrNull?.length
    return if (size == null) type else "$type (${size}c)"
}
