package com.danielealbano.androidremotecontrolmcp.assistant.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

/** Maps MCP tool definitions to the model providers' function declarations. */
internal object McpToolSchemaMapper {
    /**
     * The tool's parameters schema rebuilt as `type: object` + `properties` + `required`; `$schema`/`$defs` and any
     * other top-level keys are dropped (providers reject unknown ones). Property sub-schemas are passed through
     * untouched, so the schema is not a strict one (optional properties, no `additionalProperties: false`).
     */
    fun parameters(tool: McpTool): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", tool.inputSchema.properties ?: JsonObject(emptyMap()))
            tool.inputSchema.required
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("required", JsonArray(it.map(::JsonPrimitive))) }
        }
}
