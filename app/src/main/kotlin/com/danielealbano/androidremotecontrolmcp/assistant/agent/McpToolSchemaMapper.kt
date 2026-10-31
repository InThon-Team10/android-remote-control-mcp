package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.google.genai.types.FunctionDeclaration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.google.genai.types.Tool as GeminiTool
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

    /** One Gemini [GeminiTool] holding a declaration per MCP tool (order preserved). */
    fun toGeminiTool(tools: List<McpTool>): GeminiTool =
        GeminiTool
            .builder()
            .functionDeclarations(tools.map(::toDeclaration))
            .build()

    /** A Gemini declaration with the [parameters] schema. */
    fun toDeclaration(tool: McpTool): FunctionDeclaration =
        FunctionDeclaration
            .builder()
            .name(tool.name)
            .description(tool.description.orEmpty())
            .parametersJsonSchema(JsonBridge.toMap(parameters(tool)))
            .build()
}
