package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeminiToolCallRunnerTest {
    private val connection =
        mockk<AssistantMcpConnection> {
            coEvery { callTool(any(), any()) } returns Result.success(McpToolUtils.textResult("ok"))
        }
    private val runner = GeminiToolCallRunner(connection)

    private fun context(
        slug: String,
        vararg names: String,
    ) = RunContext("key", "model", GenerateContentConfig.builder().build(), 5, slug, names.toSet())

    private fun call(name: String) = FunctionCall.builder().name(name).build()

    @Test
    fun `gated tool asks confirmation and runs when allowed`() =
        runTest {
            val listener = RecordingAgentListener()

            runner.execute(call("android_set_clipboard"), listener, context("", "android_set_clipboard"))

            assertEquals(
                listOf(
                    "confirm:android_set_clipboard",
                    "before",
                    "started:android_set_clipboard",
                    "finished:android_set_clipboard:false",
                ),
                listener.events,
            )
        }

    @Test
    fun `denied gated tool does not run and reports the denial`() =
        runTest {
            val listener = RecordingAgentListener { false }

            val part = runner.execute(call("android_set_clipboard"), listener, context("", "android_set_clipboard"))

            assertEquals(listOf("confirm:android_set_clipboard"), listener.events)
            assertEquals(
                "The user denied this tool call.",
                part
                    .functionResponse()
                    .get()
                    .response()
                    .get()["error"],
            )
            coVerify(exactly = 0) { connection.callTool(any(), any()) }
        }

    @Test
    fun `gated tool is matched on the unprefixed name with a device slug`() =
        runTest {
            val listener = RecordingAgentListener()
            val context = context("pixel7", "android_pixel7_close_app", "android_pixel7_tap")

            runner.execute(call("android_pixel7_close_app"), listener, context)
            runner.execute(call("android_pixel7_tap"), listener, context)

            val confirms = listener.events.filter { it.startsWith("confirm") }
            assertEquals(listOf("confirm:android_pixel7_close_app"), confirms)
        }
}
