package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.google.genai.types.FunctionCall
import com.google.genai.types.Part
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.jvm.optionals.getOrNull

class ToolResultMapperTest {
    private val call =
        FunctionCall
            .builder()
            .id("x")
            .name("android_tap")
            .build()

    private fun response(part: Part) = part.functionResponse().get()

    @Test
    fun `text blocks become result map`() {
        val result = CallToolResult(content = listOf(TextContent("a"), TextContent("b")))

        val fr = response(ToolResultMapper.fromResult(call, result))

        assertEquals(mapOf("result" to "a\nb"), fr.response().get())
    }

    @Test
    fun `error result becomes error map`() {
        val result = CallToolResult(content = listOf(TextContent("boom")), isError = true)

        assertEquals(mapOf("error" to "boom"), response(ToolResultMapper.fromResult(call, result)).response().get())
    }

    @Test
    fun `denied and unknown tool results`() {
        assertEquals(
            mapOf("error" to "The user denied this tool call."),
            response(ToolResultMapper.denied(call)).response().get(),
        )
        assertEquals(mapOf("error" to "Unknown tool."), response(ToolResultMapper.unknownTool(call)).response().get())
    }

    @Test
    fun `image content becomes inline image part`() {
        val bytes = byteArrayOf(1, 2, 3)
        val data = Base64.getEncoder().encodeToString(bytes)
        val result = CallToolResult(content = listOf(ImageContent(data, "image/png")))

        val fr = response(ToolResultMapper.fromResult(call, result))

        assertEquals(mapOf("result" to "Image attached."), fr.response().get())
        val blob =
            fr
                .parts()
                .get()
                .single()
                .inlineData()
                .get()
        assertArrayEquals(bytes, blob.data().get())
        assertEquals("image/png", blob.mimeType().get())
    }

    @Test
    fun `invalid base64 image is dropped`() {
        val result = CallToolResult(content = listOf(ImageContent("!!!", "image/png")))

        val fr = response(ToolResultMapper.fromResult(call, result))

        assertTrue(fr.parts().getOrNull().isNullOrEmpty())
    }

    @Test
    fun `id and name are echoed`() {
        val fr = response(ToolResultMapper.denied(call))
        val noId = response(ToolResultMapper.denied(FunctionCall.builder().name("n").build()))

        assertEquals("x", fr.id().get())
        assertEquals("android_tap", fr.name().get())
        assertTrue(noId.id().isEmpty)
    }
}
