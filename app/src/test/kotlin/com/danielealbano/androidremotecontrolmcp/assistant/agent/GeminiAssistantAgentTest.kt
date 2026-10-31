package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiTestResponses.calls
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiTestResponses.text
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import com.google.genai.types.BlockedReason
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.ThinkingLevel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.jvm.optionals.getOrNull
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiAssistantAgentTest {
    private val gateway = FakeGeminiModelGateway()
    private val connection = mockk<AssistantMcpConnection>()
    private val assistantSettings = mockk<AssistantSettings>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val listener = RecordingAgentListener()
    private var slug = ""
    private var config = AssistantConfig(enabled = true, privacyNoticeAccepted = true, apiKeyConfigured = true)

    private fun mcpTool(name: String) = McpTool(name = name, description = "d", inputSchema = ToolSchema())

    private val tools = listOf("android_tap", "android_get_screen_state", "android_set_clipboard").map(::mcpTool)

    private val agent by lazy {
        GeminiAssistantAgent(
            gateway,
            connection,
            assistantSettings,
            settingsRepository,
            GeminiToolCallRunner(connection),
        )
    }

    @BeforeEach
    fun setUp() {
        coEvery { assistantSettings.getConfig() } answers { config }
        coEvery { assistantSettings.getApiKey() } returns "key"
        coEvery { settingsRepository.getServerConfig() } answers { ServerConfig(deviceSlug = slug) }
        coEvery { connection.listTools() } answers { Result.success(tools) }
        coEvery { connection.callTool(any(), any()) } returns Result.success(McpToolUtils.textResult("ok"))
    }

    private fun userText(value: String) = AgentInput.Text(value)

    private fun Content.fnResponses() = parts().get().map { it.functionResponse().get() }

    @Test
    fun `text only answer completes, emits text and configures the request`() =
        runTest {
            gateway.enqueue(text("Hi"))

            val outcome = agent.run(userText("hello"), listener)

            assertEquals(AgentOutcome.Completed, outcome)
            assertEquals(listOf("text:Hi"), listener.events)
            val call = gateway.calls.single()
            assertEquals(AssistantConfig.DEFAULT_MODEL, call.model)
            assertEquals(
                ElderGuidancePrompt.TEXT,
                call.config
                    .systemInstruction()
                    .get()
                    .parts()
                    .get()
                    .single()
                    .text()
                    .get(),
            )
            assertEquals("key", call.apiKey)
            assertEquals(
                ThinkingLevel.Known.LOW,
                call.config
                    .thinkingConfig()
                    .get()
                    .thinkingLevel()
                    .get()
                    .knownEnum(),
            )
            assertEquals(
                true,
                call.config
                    .automaticFunctionCalling()
                    .get()
                    .disable()
                    .get(),
            )
            assertEquals(
                2,
                call.config
                    .tools()
                    .get()
                    .single()
                    .functionDeclarations()
                    .get()
                    .size,
            )
        }

    @Test
    fun `no tools omits tools`() =
        runTest {
            coEvery { connection.listTools() } returns Result.success(emptyList())
            gateway.enqueue(text("Hi"))

            agent.run(userText("hello"), listener)

            assertTrue(
                gateway.calls
                    .single()
                    .config
                    .tools()
                    .getOrNull()
                    .isNullOrEmpty(),
            )
        }

    @Test
    fun `tool call runs the loop and forwards arguments as json`() =
        runTest {
            gateway.enqueue(calls("android_tap" to mapOf("x" to 500, "y" to 800.5, "s" to "a", "b" to true)))
            gateway.enqueue(text("done"))

            val outcome = agent.run(userText("tap"), listener)

            assertEquals(AgentOutcome.Completed, outcome)
            assertEquals(
                listOf("before", "started:android_tap", "finished:android_tap:false", "text:done"),
                listener.events,
            )
            val expected =
                JsonObject(
                    mapOf(
                        "x" to JsonPrimitive(500),
                        "y" to JsonPrimitive(800.5),
                        "s" to JsonPrimitive("a"),
                        "b" to JsonPrimitive(true),
                    ),
                )
            coVerify { connection.callTool("android_tap", expected) }
            val history = gateway.calls[1].history
            assertEquals(listOf("user", "model", "user"), history.map { it.role().get() })
            assertEquals(
                "ok",
                history[2]
                    .fnResponses()
                    .single()
                    .response()
                    .get()["result"],
            )
        }

    @Test
    fun `parallel calls produce one user content in call order and preserve the thought signature`() =
        runTest {
            val signature = byteArrayOf(9, 8, 7)
            gateway.enqueue(
                calls("android_tap" to emptyMap(), "android_get_screen_state" to emptyMap(), signature = signature),
            )
            gateway.enqueue(text("done"))

            agent.run(userText("go"), listener)

            val history = gateway.calls[1].history
            assertEquals(3, history.size)
            assertEquals(
                listOf("android_tap", "android_get_screen_state"),
                history[2].fnResponses().map { it.name().get() },
            )
            val signatureBack =
                history[1]
                    .parts()
                    .get()
                    .first()
                    .thoughtSignature()
                    .get()
            assertArrayEquals(signature, signatureBack)
        }

    @Test
    fun `step limit stops with StepLimitReached and restores history`() =
        runTest {
            config = config.copy(stepLimit = 2)
            repeat(3) { gateway.enqueue(calls("android_tap" to emptyMap())) }

            val outcome = agent.run(userText("loop"), listener)
            gateway.enqueue(text("again"))
            agent.run(userText("next"), listener)

            assertEquals(AgentOutcome.StepLimitReached(2), outcome)
            assertEquals(
                1,
                gateway.calls
                    .last()
                    .history.size,
            )
            coVerify(exactly = 2) { connection.callTool("android_tap", any()) }
        }

    @Test
    fun `step is counted per response, not per call`() =
        runTest {
            config = config.copy(stepLimit = 1)
            val tap = "android_tap" to emptyMap<String, Any>()
            gateway.enqueue(calls(tap, tap, tap))
            gateway.enqueue(text("done"))

            assertEquals(AgentOutcome.Completed, agent.run(userText("x"), listener))
        }

    @Test
    fun `refused tools are not declared and a call to one is answered with an error`() =
        runTest {
            gateway.enqueue(calls("android_set_clipboard" to emptyMap()))
            gateway.enqueue(text("done"))

            agent.run(userText("x"), listener)

            val names =
                gateway.calls[0]
                    .config
                    .tools()
                    .get()
                    .single()
                    .functionDeclarations()
                    .get()
                    .map { it.name().get() }
            assertEquals(listOf("android_tap", "android_get_screen_state"), names)
            coVerify(exactly = 0) { connection.callTool(any(), any()) }
            assertEquals(listOf("text:done"), listener.events)
            val response =
                gateway.calls[1]
                    .history[2]
                    .fnResponses()
                    .single()
            assertEquals("Unknown tool.", response.response().get()["error"])
        }

    @Test
    fun `filtering uses the unprefixed name with a device slug and drops foreign prefixes`() =
        runTest {
            slug = "pixel7"
            coEvery { connection.listTools() } returns
                Result.success(
                    listOf("android_pixel7_close_app", "android_pixel7_tap", "android_other_tap").map(::mcpTool),
                )
            gateway.enqueue(text("done"))

            agent.run(userText("x"), listener)

            val names =
                gateway.calls
                    .single()
                    .config
                    .tools()
                    .get()
                    .single()
                    .functionDeclarations()
                    .get()
                    .map { it.name().get() }
            assertEquals(listOf("android_pixel7_tap"), names)
        }

    @Test
    fun `unknown tool is answered without an MCP call or listener events`() =
        runTest {
            gateway.enqueue(calls("nope" to emptyMap()))
            gateway.enqueue(text("done"))

            agent.run(userText("x"), listener)

            coVerify(exactly = 0) { connection.callTool(any(), any()) }
            assertEquals(listOf("text:done"), listener.events)
            val response =
                gateway.calls[1]
                    .history[2]
                    .fnResponses()
                    .single()
            assertEquals("Unknown tool.", response.response().get()["error"])
        }

    @Test
    fun `tool error and image results are mapped for the model`() =
        runTest {
            coEvery { connection.callTool("android_tap", any()) } returns
                Result.success(CallToolResult(content = listOf(TextContent("bad")), isError = true))
            coEvery { connection.callTool("android_get_screen_state", any()) } returns
                Result.success(CallToolResult(content = listOf(ImageContent("AQID", "image/png"))))
            gateway.enqueue(calls("android_tap" to emptyMap(), "android_get_screen_state" to emptyMap()))
            gateway.enqueue(text("done"))

            agent.run(userText("x"), listener)

            assertTrue("finished:android_tap:true" in listener.events)
            val responses = gateway.calls[1].history[2].fnResponses()
            assertEquals("bad", responses[0].response().get()["error"])
            assertEquals(1, responses[1].parts().get().size)
        }

    @Test
    fun `failure is not retried by the agent and keeps earlier turns`() =
        runTest {
            gateway.enqueue(text("one"))
            gateway.enqueueFailure(ApiException(400, "INVALID_ARGUMENT", "x"))
            gateway.enqueue(text("three"))

            agent.run(userText("1"), listener)
            val failed = agent.run(userText("2"), listener)
            agent.run(userText("3"), listener)

            assertEquals(AgentOutcome.Failed(AssistantError.GeminiRequestFailed("HTTP 400 INVALID_ARGUMENT")), failed)
            assertEquals(3, gateway.calls.size)
            assertEquals(
                3,
                gateway.calls
                    .last()
                    .history.size,
            )
        }

    @Test
    fun `gateway and response failures map to typed errors`() =
        runTest {
            // Explicit element type: the inferred common supertype would be the SDK's package-private BaseException.
            val cases =
                listOf<Pair<Throwable, AssistantError>>(
                    ApiException(401, "UNAUTHENTICATED", "x") to AssistantError.GeminiAuthFailed,
                    ApiException(429, "RESOURCE_EXHAUSTED", "x") to AssistantError.GeminiQuotaExceeded,
                    ApiException(503, "UNAVAILABLE", "x") to AssistantError.GeminiUnavailable,
                    GenAiIOException("x") to AssistantError.GeminiUnavailable,
                )
            cases.forEach { (failure, expected) ->
                gateway.enqueueFailure(failure)
                assertEquals(AgentOutcome.Failed(expected), agent.run(userText("x"), listener))
            }
            gateway.enqueue(GeminiTestResponses.promptBlocked(BlockedReason.Known.SAFETY))
            assertEquals(
                AgentOutcome.Failed(AssistantError.GeminiBlocked("SAFETY")),
                agent.run(userText("x"), listener),
            )
            gateway.enqueue(GeminiTestResponses.response(null, FinishReason.Known.SAFETY))
            assertEquals(
                AgentOutcome.Failed(AssistantError.GeminiBlocked("SAFETY")),
                agent.run(userText("x"), listener),
            )
        }

    @Test
    fun `list tools failure maps to McpUnavailable or the carried error and never reaches the gateway`() =
        runTest {
            coEvery { connection.listTools() } returns Result.failure(IllegalStateException("x"))
            assertEquals(
                AgentOutcome.Failed(AssistantError.McpUnavailable("IllegalStateException")),
                agent.run(userText("x"), listener),
            )
            coEvery { connection.listTools() } returns
                Result.failure(AssistantException(AssistantError.BearerDisabled))
            assertEquals(AgentOutcome.Failed(AssistantError.BearerDisabled), agent.run(userText("x"), listener))
            assertTrue(gateway.calls.isEmpty())
        }

    @Test
    fun `call tool failure finishes the row, maps to McpUnavailable and truncates history`() =
        runTest {
            coEvery { connection.callTool(any(), any()) } returns Result.failure(IllegalStateException())
            gateway.enqueue(calls("android_tap" to emptyMap()))
            gateway.enqueue(text("ok"))

            val outcome = agent.run(userText("x"), listener)
            agent.run(userText("y"), listener)

            assertEquals(AgentOutcome.Failed(AssistantError.McpUnavailable("IllegalStateException")), outcome)
            assertTrue("finished:android_tap:true" in listener.events)
            assertEquals(
                1,
                gateway.calls
                    .last()
                    .history.size,
            )
        }

    @Test
    fun `missing api key, disabled assistant and unaccepted privacy notice fail without calling anything`() =
        runTest {
            coEvery { assistantSettings.getApiKey() } returns null
            assertEquals(AgentOutcome.Failed(AssistantError.ApiKeyMissing), agent.run(userText("x"), listener))
            config = config.copy(enabled = false)
            assertEquals(AgentOutcome.Failed(AssistantError.AssistantDisabled), agent.run(userText("x"), listener))
            config = config.copy(enabled = true, privacyNoticeAccepted = false)
            assertEquals(
                AgentOutcome.Failed(AssistantError.PrivacyNoticeNotAccepted),
                agent.run(userText("x"), listener),
            )
            coVerify(exactly = 0) { connection.listTools() }
            assertTrue(gateway.calls.isEmpty())
        }

    @Test
    fun `cancellation during a tool call truncates the incomplete turn`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            coEvery { connection.callTool(any(), any()) } coAnswers
                {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    Result.success(McpToolUtils.textResult("never"))
                }
            gateway.enqueue(calls("android_tap" to emptyMap()))
            val job = launch { agent.run(userText("cancel me"), listener) }
            started.await()
            job.cancelAndJoin()
            gateway.enqueue(text("after"))

            agent.run(userText("next"), listener)

            assertEquals(
                1,
                gateway.calls
                    .last()
                    .history.size,
            )
        }

    @Test
    fun `second input continues history, reset clears it`() =
        runTest {
            gateway.enqueue(text("a"))
            gateway.enqueue(text("b"))
            gateway.enqueue(text("c"))

            agent.run(userText("1"), listener)
            agent.run(userText("2"), listener)
            assertEquals(3, gateway.calls[1].history.size)
            agent.reset()
            agent.run(userText("3"), listener)

            assertEquals(1, gateway.calls[2].history.size)
        }

    @Test
    fun `model and step limit come from settings`() =
        runTest {
            config = config.copy(model = "gemini-3.5-flash-lite", stepLimit = 5)
            gateway.enqueue(text("ok"))

            agent.run(userText("hi"), listener)

            assertEquals("gemini-3.5-flash-lite", gateway.calls.single().model)
        }

    @Test
    fun `concurrent runs are serialised`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { connection.callTool(any(), any()) } coAnswers
                {
                    gate.await()
                    Result.success(McpToolUtils.textResult("ok"))
                }
            gateway.enqueue(calls("android_tap" to emptyMap()))
            gateway.enqueue(text("first"))
            gateway.enqueue(text("second"))

            val first = async { agent.run(userText("1"), listener) }
            advanceUntilIdle()
            val second = async { agent.run(userText("2"), listener) }
            advanceUntilIdle()
            assertEquals(1, gateway.calls.size)
            gate.complete(Unit)
            first.await()
            second.await()

            assertEquals(3, gateway.calls.size)
            assertSame(AgentOutcome.Completed, second.await())
        }
}
