package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseFunctionToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A terminal reply is explicitly a question, a verified completion, or a limitation; never a manual. */
internal object GuidanceReply {
    const val NAME = "reply_to_user"
    private val schema =
        Json
            .parseToJsonElement(
                """{"type":"object","properties":{"kind":{"type":"string","enum":["question","completed","unavailable"]},
            "message":{"type":"string"}},"required":["kind","message"],"additionalProperties":false}""",
            ).jsonObject
    val tool: FunctionTool =
        FunctionTool
            .builder()
            .name(NAME)
            .description(
                "End the turn only to ask one essential question, confirm verified completion, or explain why guidance is unavailable. " +
                    "Never give phone interaction instructions here: show those with highlight tools. For a new send request confirm the recipient first.",
            ).parameters(
                FunctionTool.Parameters
                    .builder()
                    .apply {
                        schema.forEach { (key, value) -> putAdditionalProperty(key, JsonBridge.toJsonValue(value)) }
                    }.build(),
            ).strict(true)
            .build()

    fun message(call: ResponseFunctionToolCall): String? =
        runCatching {
            val args = Json.parseToJsonElement(call.arguments()).jsonObject
            val kind = args["kind"]?.jsonPrimitive?.content
            val message = args["message"]?.jsonPrimitive?.content?.trim()
            message?.takeIf { it.isNotBlank() && kind in setOf("question", "completed", "unavailable") }
        }.getOrNull()
}
