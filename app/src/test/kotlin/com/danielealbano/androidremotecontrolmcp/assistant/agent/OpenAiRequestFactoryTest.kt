package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.openai.core.jsonMapper
import com.openai.models.ReasoningEffort
import com.openai.models.responses.ResponseIncludable
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

class OpenAiRequestFactoryTest {
    private val schema =
        ToolSchema(
            properties =
                buildJsonObject {
                    putJsonObject("x") {
                        put("type", "integer")
                        put("minimum", 0)
                    }
                    putJsonObject("instruction") { put("type", "string") }
                },
            required = listOf("x"),
        )

    @Test
    fun `function tool is non-strict and carries the raw MCP schema`() {
        val tool =
            OpenAiRequestFactory.functionTool(
                McpTool(name = "android_tap", description = "Taps", inputSchema = schema),
            )

        assertEquals("android_tap", tool.name())
        assertEquals("Taps", tool.description().get())
        assertEquals(false, tool.strict().get())
        val parameters = Json.parseToJsonElement(jsonMapper().writeValueAsString(tool.parameters().get()))

        assertEquals(McpToolSchemaMapper.parameters(McpTool(name = "t", inputSchema = schema)), parameters)
    }

    @Test
    fun `missing description becomes empty and tool order is kept`() {
        val tools =
            OpenAiRequestFactory.functionTools(
                listOf("a", "b", "c").map { McpTool(name = it, description = null, inputSchema = ToolSchema()) },
            )

        assertEquals(listOf("a", "b", "c"), tools.map { it.name() })
        assertEquals("", tools.first().description().get())
        assertFalse(
            tools
                .first()
                .parameters()
                .get()
                ._allProperties()
                .containsKey("required"),
        )
    }

    @Test
    fun `params replay the input with the stateless low-effort settings`() {
        val input = listOf(OpenAiRequestFactory.userMessage(AgentInput.Text("hi")))
        val tools = OpenAiRequestFactory.functionTools(listOf(McpTool(name = "t", inputSchema = ToolSchema())))

        val params = OpenAiRequestFactory.params("model-x", "prompt", tools, input)

        assertEquals("model-x", params.model().get().asString())
        assertEquals("prompt", params.instructions().get())
        assertEquals(input, params.input().get().asResponse())
        assertEquals(
            ReasoningEffort.LOW,
            params
                .reasoning()
                .get()
                .effort()
                .get(),
        )
        assertEquals(false, params.store().get())
        assertEquals(listOf(ResponseIncludable.REASONING_ENCRYPTED_CONTENT), params.include().get())
        assertEquals(listOf("t"), params.tools().get().map { it.asFunction().name() })
        assertEquals(false, params.parallelToolCalls().get())
    }

    @Test
    fun `user message is a user role text input`() {
        val message = OpenAiRequestFactory.userMessage(AgentInput.Text("hello")).asEasyInputMessage()

        assertEquals("user", message.role().asString())
        assertEquals("hello", message.content().asTextInput())
    }
}
