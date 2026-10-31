package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.assistant.agent.JsonBridge
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiRequestFactory
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ToolGuidanceClassification
import com.openai.models.responses.FunctionTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
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

    /**
     * The planner's compact tool set: a few tools with one-line descriptions and only the parameters the planner
     * needs (the full MCP descriptions were ~21,000 characters per call). Only tools the app's MCP server offers.
     */
    fun build(
        mcpTools: List<McpTool>,
        deviceSlug: String,
    ): List<FunctionTool> {
        val prefix = McpToolUtils.buildToolNamePrefix(deviceSlug)
        val offered = mcpTools.mapTo(HashSet()) { it.name.removePrefix(prefix) }
        return COMPACT.filter { it.name in offered }.map { it.toFunctionTool() } + answerUser()
    }

    private class Compact(
        val name: String,
        val description: String,
        val properties: JsonObject,
        val required: List<String> = emptyList(),
    ) {
        fun toFunctionTool(): FunctionTool {
            val schema =
                buildJsonObject {
                    put("type", "object")
                    put("properties", properties)
                    if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
                }
            val parameters = FunctionTool.Parameters.builder()
            schema.forEach { (key, value) -> parameters.putAdditionalProperty(key, JsonBridge.toJsonValue(value)) }
            return FunctionTool
                .builder()
                .name(name)
                .description(description)
                .parameters(parameters.build())
                .strict(false)
                .build()
        }
    }

    private fun props(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

    private fun JsonObjectBuilder.type(
        name: String,
        type: String,
        vararg enum: String,
    ) {
        putJsonObject(name) {
            put("type", type)
            if (enum.isNotEmpty()) put("enum", JsonArray(enum.map(::JsonPrimitive)))
        }
    }

    private fun JsonObjectBuilder.target() {
        putJsonObject("node_id") {
            put("type", "string")
            put("description", "The node id from the screen data, with its prefix, e.g. node_3e6a9b95.")
        }
        type("by", "string", "text", "content_desc")
        type("value", "string")
    }

    private fun JsonObjectBuilder.guidance() {
        putJsonObject("instruction") {
            put("type", "string")
            put("description", "One short, warm Korean sentence telling the user what to do.")
        }
        putJsonObject("explanation") {
            put("type", "string")
            put("description", "Only when sending, paying, deleting, calling or sharing: what will happen.")
        }
    }

    private val COMPACT: List<Compact> =
        listOf(
            Compact(SCREEN_STATE, "Read the current screen again (on-screen elements).", props {}),
            Compact(
                "find_nodes",
                "Find elements by text or content description, including ones that need scrolling.",
                props {
                    type("by", "string", "text", "content_desc")
                    type("value", "string")
                },
                listOf("by", "value"),
            ),
            Compact(
                "click_node",
                "Highlight one element for the user to tap: node_id, or by + value matching exactly one element.",
                props {
                    target()
                    guidance()
                },
            ),
            Compact(
                "long_click_node",
                "Like click_node, for a long press.",
                props {
                    target()
                    guidance()
                },
            ),
            Compact(
                "type_append_text",
                "Highlight a text field; text is what the user should type.",
                props {
                    target()
                    type("text", "string")
                    guidance()
                },
                listOf("text"),
            ),
            Compact(
                "scroll_node",
                "Show the user how to scroll a list (the target is the list).",
                props {
                    target()
                    type("direction", "string", "up", "down", "left", "right")
                    guidance()
                },
                listOf("direction"),
            ),
            Compact(
                "scroll_to_node",
                "Show how to scroll until the named element appears.",
                props {
                    target()
                    type("direction", "string", "up", "down", "left", "right")
                    guidance()
                },
                listOf("direction"),
            ),
            Compact(
                "swipe",
                "Show a swipe the user should make, in screen pixels.",
                props {
                    type("x1", "number")
                    type("y1", "number")
                    type("x2", "number")
                    type("y2", "number")
                    guidance()
                },
                listOf("x1", "y1", "x2", "y2"),
            ),
        )

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
