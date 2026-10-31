package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.privacy.PlaceholderSubstitutor
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceOutcome
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A tool as it is registered under elder guidance. */
internal class GuardedTool(
    val description: String,
    val inputSchema: ToolSchema,
    val handler: suspend (CallToolRequest) -> CallToolResult,
    /** False for guided calls: the user acts there, so the "AI is controlling" indicator stays off. */
    val showsToolCallIndicator: Boolean,
)

/**
 * Elder guidance: the one guard every tool registration goes through. Read-only tools run unchanged, targeted
 * actions highlight their target and wait for the user's own action, anything else is refused. The device is never
 * acted on, and no text the AI wanted to type or any device text is ever put in an answer.
 */
class ElderToolGuidance internal constructor(
    private val controller: GuidanceController,
    private val targets: ElderGuidanceTargets,
    private val substitutor: PlaceholderSubstitutor,
) {
    /** [handler], [description] and [inputSchema] of the un-prefixed tool [toolName], as guidance changes them. */
    internal fun guard(
        toolName: String,
        description: String,
        inputSchema: ToolSchema,
        handler: suspend (CallToolRequest) -> CallToolResult,
    ): GuardedTool =
        when (ToolGuidanceClassification.classify(toolName)) {
            ToolGuidanceClass.READ_ONLY -> {
                GuardedTool(description, inputSchema, handler, showsToolCallIndicator = true)
            }

            ToolGuidanceClass.REFUSE -> {
                GuardedTool(description, inputSchema, { refusal(toolName) }, showsToolCallIndicator = true)
            }

            ToolGuidanceClass.HIGHLIGHT -> {
                GuardedTool(
                    description = "$description $DESCRIPTION_SUFFIX",
                    inputSchema = withGuidanceProperties(inputSchema),
                    handler = { request -> highlight(toolName, request.arguments) },
                    showsToolCallIndicator = false,
                )
            }
        }

    private fun refusal(toolName: String): CallToolResult {
        Log.i(TAG, "$toolName: refused")
        return CallToolResult(content = textContent(REFUSAL_TEXT.format(toolName)), isError = true)
    }

    private suspend fun highlight(
        toolName: String,
        arguments: JsonObject?,
    ): CallToolResult {
        val instruction = optionalText(arguments, PARAM_INSTRUCTION, MAX_INSTRUCTION_CHARS)
        val explanation = optionalText(arguments, PARAM_EXPLANATION, MAX_EXPLANATION_CHARS)
        val step =
            when (val resolved = resolveTarget(toolName, arguments)) {
                is GuidanceStep.Answer -> {
                    Log.i(TAG, "$toolName: answered without a highlight (isError=${resolved.result.isError == true})")
                    return resolved.result
                }

                is GuidanceStep.Show -> {
                    resolved
                }
            }
        val outcome =
            controller.guide(
                GuidanceTarget(
                    bounds = step.bounds,
                    kind = step.kind,
                    instruction = substitutor.substitute(instruction),
                    explanation = explanation.takeIf { it.isNotEmpty() }?.let(substitutor::substitute),
                    scrollDirection = step.scrollDirection,
                    swipeStart = step.swipeStart,
                ),
            )
        Log.i(TAG, "$toolName: $outcome")
        return outcomeResult(outcome)
    }

    /** Node ids change whenever the screen changes: a vanished node tells the model how to recover. */
    private suspend fun resolveTarget(
        toolName: String,
        arguments: JsonObject?,
    ): GuidanceStep =
        try {
            targets.resolve(toolName, arguments)
        } catch (e: McpToolException.NodeNotFound) {
            Log.i(TAG, "$toolName: target gone (${e.javaClass.simpleName})")
            GuidanceStep.Answer(CallToolResult(content = textContent(NODE_GONE_TEXT), isError = true))
        }

    private fun outcomeResult(outcome: GuidanceOutcome): CallToolResult {
        val text =
            when (outcome) {
                GuidanceOutcome.USER_ACTED -> USER_ACTED_TEXT
                GuidanceOutcome.SCREEN_CHANGED -> SCREEN_CHANGED_TEXT
                GuidanceOutcome.TIMED_OUT -> TIMED_OUT_TEXT
                GuidanceOutcome.CANCELLED -> CANCELLED_TEXT
                GuidanceOutcome.SUPERSEDED -> SUPERSEDED_TEXT
                GuidanceOutcome.UNAVAILABLE -> UNAVAILABLE_TEXT
            }
        val isError = outcome in ERROR_OUTCOMES
        return CallToolResult(content = textContent(text), isError = isError)
    }

    /** The trimmed optional text [name]; blank when absent. Longer than [maxChars] is an invalid-params error. */
    private fun optionalText(
        arguments: JsonObject?,
        name: String,
        maxChars: Int,
    ): String {
        val text = McpToolUtils.optionalString(arguments, name, "").trim()
        if (text.length > maxChars) {
            throw McpToolException.InvalidParams("Parameter '$name' must be at most $maxChars characters")
        }
        return text
    }

    private fun withGuidanceProperties(schema: ToolSchema): ToolSchema {
        val extra =
            buildJsonObject {
                putJsonObject(PARAM_INSTRUCTION) {
                    put("type", "string")
                    put("maxLength", MAX_INSTRUCTION_CHARS)
                    put(
                        "description",
                        "One short sentence telling the user what to do, in the user's language.",
                    )
                }
                putJsonObject(PARAM_EXPLANATION) {
                    put("type", "string")
                    put("maxLength", MAX_EXPLANATION_CHARS)
                    put("description", "What will happen once the user does it.")
                }
            }
        return schema.copy(properties = JsonObject((schema.properties ?: JsonObject(emptyMap())) + extra))
    }

    private fun textContent(text: String) = listOf(TextContent(text = text))

    companion object {
        private const val TAG = "MCP:ElderGuidance"
        const val PARAM_INSTRUCTION = "instruction"
        const val PARAM_EXPLANATION = "explanation"
        const val MAX_INSTRUCTION_CHARS = 200
        const val MAX_EXPLANATION_CHARS = 500

        const val DESCRIPTION_SUFFIX =
            "Elder guidance: highlights the target and waits up to 45 s for the user to do it; " +
                "it never acts on the device."

        const val REFUSAL_TEXT =
            "Elder guidance mode: '%s' would change the device by itself, so it is disabled. Tell the user in " +
                "simple words how to do this step themselves, or point at one element on screen with a highlight " +
                "tool (click_node, tap_node, long_click_node, tap, long_press, double_tap, type_append_text, " +
                "type_insert_text, type_replace_text, type_clear_text, scroll_node, scroll_to_node, swipe)."
        const val USER_ACTED_TEXT =
            "The user did it. Call get_screen_state to see the new screen before the next step."
        const val SCREEN_CHANGED_TEXT =
            "The screen changed before the user acted on the highlight. Call get_screen_state and guide again."
        const val TIMED_OUT_TEXT =
            "The user has not acted yet (waited 45 s). The highlight stays on screen. Call the same tool with " +
                "the same target to keep waiting, or ask the user whether they need help."
        const val CANCELLED_TEXT = "The user closed the guidance. Ask what they want to do."
        const val NODE_GONE_TEXT =
            "That element is no longer on the screen: the screen changed since it was read, so its node_id is " +
                "stale. Read the screen again (get_screen_state or find_nodes) and highlight it with the new id, or " +
                "target it by name with by and value. This is not something to tell the user."
        const val HIDDEN_TARGET_TEXT =
            "That element is mostly hidden behind another control, so the user cannot touch it. First show the " +
                "user how to bring it into view (for example scroll_node on its list, or a swipe), then highlight " +
                "it again. This is not something to tell the user."
        const val SUPERSEDED_TEXT = "A newer guidance step replaced this one."
        const val UNAVAILABLE_TEXT =
            "The guidance overlay is not available because the helper's accessibility service is not connected. " +
                "Ask the user to turn it on."

        private val ERROR_OUTCOMES =
            setOf(GuidanceOutcome.CANCELLED, GuidanceOutcome.SUPERSEDED, GuidanceOutcome.UNAVAILABLE)
    }
}
