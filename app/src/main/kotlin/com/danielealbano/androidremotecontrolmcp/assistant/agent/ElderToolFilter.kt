package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ToolGuidanceClass
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ToolGuidanceClassification
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The in-app helper always runs in elder mode: tools that would be refused are never offered to the model. */
internal object ElderToolFilter {
    /** Keeps the tools that are READ_ONLY or HIGHLIGHT; a name without this device's prefix is REFUSE. */
    fun usable(
        tools: List<Tool>,
        deviceSlug: String,
    ): List<Tool> {
        val prefix = McpToolUtils.buildToolNamePrefix(deviceSlug)
        return tools.filter {
            it.name.startsWith(prefix) &&
                ToolGuidanceClassification.classify(it.name.removePrefix(prefix)) != ToolGuidanceClass.REFUSE
        }
    }

    /**
     * The helper's argument defaults: get_screen_state lists only what is on screen (what the user can see and act
     * on) unless the model asks for off-screen nodes. With off-screen nodes the app list alone exceeds one page of
     * the dump, so the model never saw icons that were right on screen.
     */
    fun withDefaults(
        toolName: String,
        arguments: JsonObject,
        deviceSlug: String,
    ): JsonObject {
        val name = toolName.removePrefix(McpToolUtils.buildToolNamePrefix(deviceSlug))
        if (name != SCREEN_STATE_TOOL || arguments.containsKey(INCLUDE_OFFSCREEN)) return arguments
        return JsonObject(arguments + (INCLUDE_OFFSCREEN to JsonPrimitive(false)))
    }

    private const val SCREEN_STATE_TOOL = "get_screen_state"
    private const val INCLUDE_OFFSCREEN = "include_offscreen"
}
