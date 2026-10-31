package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.google.genai.types.FunctionDeclaration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.google.genai.types.Tool as GeminiTool
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

/** Maps MCP tool definitions to Gemini function declarations. */
internal object McpToolSchemaMapper {
    /** One Gemini [GeminiTool] holding a declaration per MCP tool (order preserved). */
    fun toGeminiTool(tools: List<McpTool>): GeminiTool =
        GeminiTool
            .builder()
            .functionDeclarations(tools.map(::toDeclaration))
            .build()

    /**
     * The schema is rebuilt as `type: object` + `properties` + `required`; `$schema`/`$defs` are dropped because
     * Gemini rejects unknown top-level keys. Property sub-schemas are passed through untouched.
     */
    fun toDeclaration(tool: McpTool): FunctionDeclaration {
        val schema =
            buildJsonObject {
                put("type", "object")
                put("properties", tool.inputSchema.properties ?: JsonObject(emptyMap()))
                tool.inputSchema.required
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { put("required", JsonArray(it.map(::JsonPrimitive))) }
            }
        return FunctionDeclaration
            .builder()
            .name(tool.name)
            .description(tool.description.orEmpty())
            .parametersJsonSchema(JsonBridge.toMap(schema))
            .build()
    }
}
