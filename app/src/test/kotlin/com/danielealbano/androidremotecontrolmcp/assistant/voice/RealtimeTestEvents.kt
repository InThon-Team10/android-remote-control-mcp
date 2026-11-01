package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** Server events of the Realtime API as the SDK serialises them, for the voice tests. */
object RealtimeTestEvents {
    fun type(type: String): JsonObject = buildJsonObject { put("type", type) }

    fun created(responseId: String): JsonObject =
        buildJsonObject {
            put("type", "response.created")
            putJsonObject("response") { put("id", responseId) }
        }

    fun done(
        status: String = "completed",
        errorCode: String? = null,
        responseId: String? = null,
    ): JsonObject =
        buildJsonObject {
            put("type", "response.done")
            putJsonObject("response") {
                if (responseId != null) put("id", responseId)
                put("status", status)
                if (errorCode != null) {
                    putJsonObject("status_details") { putJsonObject("error") { put("code", errorCode) } }
                }
            }
        }

    fun audio(
        responseId: String,
        itemId: String,
        bytes: Int,
    ): JsonObject =
        buildJsonObject {
            put("type", "response.output_audio.delta")
            put("response_id", responseId)
            put("item_id", itemId)
            put("delta", Base64.getEncoder().encodeToString(ByteArray(bytes)))
        }

    fun transcript(
        responseId: String,
        text: String,
    ): JsonObject =
        buildJsonObject {
            put("type", "response.output_audio_transcript.delta")
            put("response_id", responseId)
            put("delta", text)
        }

    fun userSaid(text: String): JsonObject =
        buildJsonObject {
            put("type", "conversation.item.input_audio_transcription.completed")
            put("transcript", text)
        }

    fun call(
        name: String,
        callId: String,
        arguments: String = "{}",
    ): JsonObject =
        buildJsonObject {
            put("type", "response.output_item.done")
            putJsonObject("item") {
                put("type", "function_call")
                put("name", name)
                put("call_id", callId)
                put("arguments", arguments)
            }
        }

    fun error(
        code: String?,
        type: String = "invalid_request_error",
    ): JsonObject =
        buildJsonObject {
            put("type", "error")
            putJsonObject("error") {
                put("type", type)
                if (code != null) put("code", code)
            }
        }
}
