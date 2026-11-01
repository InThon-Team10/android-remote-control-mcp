package com.danielealbano.androidremotecontrolmcp.assistant.agent

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiToolOutputTest {
    private fun output(result: CallToolResult) = OpenAiToolOutput.fromResult("call_1", result).asFunctionCallOutput()

    @Test
    fun `text blocks are joined and keep their order`() {
        val item = output(CallToolResult(content = listOf(TextContent("warning"), TextContent("data"))))

        assertEquals("call_1", item.callId().get())
        assertEquals("warning\ndata", item.output().asString())
    }

    @Test
    fun `error result is prefixed`() {
        val item = output(CallToolResult(content = listOf(TextContent("bad")), isError = true))

        assertEquals("ERROR: bad", item.output().asString())
    }

    @Test
    fun `empty result is an empty string`() {
        assertEquals("", output(CallToolResult(content = emptyList())).output().asString())
    }

    @Test
    fun `images follow the text as data urls and an image alone gets a placeholder text`() {
        val withText = output(CallToolResult(content = listOf(TextContent("t"), ImageContent("AQID", "image/jpeg"))))
        val alone = output(CallToolResult(content = listOf(ImageContent("AQID", "image/png"))))

        val parts = withText.output().asResponseFunctionCallOutputItemList()
        assertEquals("t", parts[0].asInputText().text())
        assertEquals("data:image/jpeg;base64,AQID", parts[1].asInputImage().imageUrl().get())
        assertEquals(
            "Image attached.",
            alone
                .output()
                .asResponseFunctionCallOutputItemList()[0]
                .asInputText()
                .text(),
        )
    }

    @Test
    fun `undecodable image data is dropped`() {
        val item = output(CallToolResult(content = listOf(TextContent("t"), ImageContent("not base64!", "image/png"))))

        assertTrue(item.output().isString())
        assertEquals("t", item.output().asString())
    }

    @Test
    fun `guard outputs carry the call id and an error text`() {
        assertEquals(
            "ERROR: The user denied this tool call.",
            OpenAiToolOutput
                .denied("c")
                .asFunctionCallOutput()
                .output()
                .asString(),
        )
        assertEquals(
            "ERROR: Unknown tool.",
            OpenAiToolOutput
                .unknownTool("c")
                .asFunctionCallOutput()
                .output()
                .asString(),
        )
        assertEquals(
            "c",
            OpenAiToolOutput
                .invalidArguments("c")
                .asFunctionCallOutput()
                .callId()
                .get(),
        )
    }
}
