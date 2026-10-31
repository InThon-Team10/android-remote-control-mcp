package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.assistant.agent.JsonBridge
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiRequestFactory
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ToolGuidanceClassification
import com.openai.models.responses.FunctionTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

/** The planner's tools under their un-prefixed names: a few read-only ones, every highlight tool, `answer_user`. */
internal object GuidePlannerTools {
    const val ANSWER_USER = "answer_user"
    const val SCREEN_STATE = "get_screen_state"
    val READ_ONLY: Set<String> = setOf(SCREEN_STATE, "find_nodes", "get_node_details", "list_apps")
    val HIGHLIGHT: Set<String> = ToolGuidanceClassification.HIGHLIGHT_TOOLS

    val KINDS = listOf("done", "say", "ask")

    fun build(
        mcpTools: List<McpTool>,
        deviceSlug: String,
    ): List<FunctionTool> {
        val prefix = McpToolUtils.buildToolNamePrefix(deviceSlug)
        val planned =
            mcpTools
                .filter { it.name.startsWith(prefix) && it.name.removePrefix(prefix) in READ_ONLY + HIGHLIGHT }
                .map {
                    OpenAiRequestFactory
                        .functionTool(it)
                        .toBuilder()
                        .name(it.name.removePrefix(prefix))
                        .build()
                }
        return planned + answerUser()
    }

    private fun answerUser(): FunctionTool {
        val schema =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("kind") {
                        put("type", "string")
                        put("enum", JsonArray(KINDS.map(::JsonPrimitive)))
                    }
                    putJsonObject("sentence") {
                        put("type", "string")
                        put("description", "One or two short Korean sentences for the user.")
                    }
                }
                put("required", JsonArray(listOf(JsonPrimitive("kind"), JsonPrimitive("sentence"))))
            }
        val parameters = FunctionTool.Parameters.builder()
        schema.forEach { (key, value) -> parameters.putAdditionalProperty(key, JsonBridge.toJsonValue(value)) }
        return FunctionTool
            .builder()
            .name(ANSWER_USER)
            .description(
                "Ends the step without a highlight: the goal is done, the step must be explained in words, or a " +
                    "question for the user.",
            ).parameters(parameters.build())
            .strict(false)
            .build()
    }
}
