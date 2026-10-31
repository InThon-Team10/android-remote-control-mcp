package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.openai.core.jsonMapper
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.jvm.optionals.getOrNull
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

class McpToolSchemaMapperTest {
    private fun tool(
        name: String,
        description: String? = null,
        schema: ToolSchema = ToolSchema(),
    ) = McpTool(name = name, description = description, inputSchema = schema)

    private fun schemaOf(tool: McpTool): Map<*, *> = JsonBridge.toMap(McpToolSchemaMapper.parameters(tool))

    @Test
    fun `schema contains type object, properties and required`() {
        val properties =
            buildJsonObject {
                putJsonObject("x") {
                    put("type", "integer")
                }
            }

        val schema = schemaOf(tool("t", schema = ToolSchema(properties = properties, required = listOf("x"))))

        assertEquals("object", schema["type"])
        assertEquals(mapOf("x" to mapOf("type" to "integer")), schema["properties"])
        assertEquals(listOf("x"), schema["required"])
    }

    @Test
    fun `missing properties yield empty properties map and no required key`() {
        val schema = schemaOf(tool("t", schema = ToolSchema(properties = null, required = emptyList())))

        assertEquals("object", schema["type"])
        assertEquals(emptyMap<String, Any?>(), schema["properties"])
        assertFalse(schema.containsKey("required"))
    }

    @Test
    fun `parameters keep only type, properties and required`() {
        val properties = buildJsonObject { putJsonObject("x") { put("type", "integer") } }
        val schema = ToolSchema(properties = properties, required = listOf("x"))

        val parameters = McpToolSchemaMapper.parameters(tool("t", schema = schema))

        assertEquals(setOf("type", "properties", "required"), parameters.keys)
        assertEquals(properties, parameters["properties"])
    }

    @Test
    fun `JsonBridge converts kotlinx json to the OpenAI SDK json value`() {
        val json =
            buildJsonObject {
                put("s", "a")
                put("n", 5)
                putJsonObject("o") { put("b", true) }
            }

        val value = JsonBridge.toJsonValue(json)

        assertEquals(json, Json.parseToJsonElement(jsonMapper().writeValueAsString(value)))
    }

    @Test
    fun `JsonBridge round trips plain json types`() {
        val json =
            buildJsonObject {
                put("s", "a")
                put("b", true)
                put("n", 5)
                put("d", 1.5)
            }

        val back: JsonObject = JsonBridge.toJsonObject(JsonBridge.toMap(json))

        assertEquals(json, back)
        assertTrue(JsonBridge.toMap(json)["n"] is Number)
    }
}
