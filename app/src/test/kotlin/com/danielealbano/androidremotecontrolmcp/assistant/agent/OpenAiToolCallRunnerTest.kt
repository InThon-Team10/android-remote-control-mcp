package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseInputItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OpenAiToolCallRunnerTest {
    private val connection =
        mockk<AssistantMcpConnection> {
            coEvery { callTool(any(), any()) } returns Result.success(McpToolUtils.textResult("ok"))
        }
    private val runner = OpenAiToolCallRunner(connection)

    private fun context(
        slug: String,
        vararg names: String,
    ) = OpenAiRunContext("key", "model", emptyList(), 5, slug, names.toSet())

    private fun call(
        name: String,
        arguments: String = "{}",
    ) = ResponseFunctionToolCall
        .builder()
        .name(name)
        .arguments(arguments)
        .callId("call_9")
        .build()

    private fun ResponseInputItem.output(): String = asFunctionCallOutput().output().asString()

    @Test
    fun `allowed call runs through MCP with the helper defaults and answers its call id`() =
        runTest {
            val listener = RecordingAgentListener()

            val item =
                runner.execute(
                    call("android_get_screen_state"),
                    listener,
                    context("", "android_get_screen_state"),
                )

            val expected = JsonObject(mapOf("include_offscreen" to JsonPrimitive(false)))
            coVerify { connection.callTool("android_get_screen_state", expected) }
            assertEquals(
                listOf("before", "started:android_get_screen_state", "finished:android_get_screen_state:false"),
                listener.events,
            )
            assertEquals("call_9", item.asFunctionCallOutput().callId().get())
            assertEquals("ok", item.output())
        }

    @Test
    fun `blank arguments are read as an empty object`() =
        runTest {
            runner.execute(call("android_tap", ""), RecordingAgentListener(), context("", "android_tap"))

            coVerify { connection.callTool("android_tap", JsonObject(emptyMap())) }
        }

    @Test
    fun `arguments that are not a json object are refused without asking or calling`() =
        runTest {
            val listener = RecordingAgentListener()
            val context = context("", "android_set_clipboard")

            val malformed = runner.execute(call("android_set_clipboard", "{\"x\":"), listener, context)
            val array = runner.execute(call("android_set_clipboard", "[1]"), listener, context)

            assertEquals("ERROR: The arguments are not a JSON object.", malformed.output())
            assertEquals("ERROR: The arguments are not a JSON object.", array.output())
            assertEquals(emptyList<String>(), listener.events)
            coVerify(exactly = 0) { connection.callTool(any(), any()) }
        }

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

            val item = runner.execute(call("android_set_clipboard"), listener, context("", "android_set_clipboard"))

            assertEquals(listOf("confirm:android_set_clipboard"), listener.events)
            assertEquals("ERROR: The user denied this tool call.", item.output())
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

    @Test
    fun `MCP failure finishes the row as an error and throws the mapped failure`() =
        runTest {
            coEvery { connection.callTool(any(), any()) } returns Result.failure(IllegalStateException())
            val listener = RecordingAgentListener()

            val failure =
                assertThrows<AgentFailure> {
                    runner.execute(call("android_tap"), listener, context("", "android_tap"))
                }

            assertEquals(AssistantError.McpUnavailable("IllegalStateException"), failure.error)
            assertEquals("finished:android_tap:true", listener.events.last())
        }
}
