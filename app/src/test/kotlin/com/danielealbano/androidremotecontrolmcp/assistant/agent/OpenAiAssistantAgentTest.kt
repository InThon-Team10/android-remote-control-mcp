package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.calls
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.errorObject
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.headers
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.openai.errors.BadRequestException
import com.openai.errors.InternalServerException
import com.openai.errors.OpenAIIoException
import com.openai.errors.RateLimitException
import com.openai.errors.UnauthorizedException
import com.openai.models.ReasoningEffort
import com.openai.models.responses.ResponseIncludable
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ToolChoiceOptions
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.jvm.optionals.getOrNull
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.text as rawText
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiAssistantAgentTest {
    private val gateway = FakeOpenAiResponsesGateway()
    private val connection = mockk<AssistantMcpConnection>()
    private val assistantSettings = mockk<AssistantSettings>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val listener = RecordingAgentListener()
    private var slug = ""
    private var config = AssistantConfig(enabled = true, privacyNoticeAccepted = true, apiKeyConfigured = true)

    private fun mcpTool(name: String) = McpTool(name = name, description = "d", inputSchema = ToolSchema())

    private val tools = listOf("android_tap", "android_get_screen_state", "android_set_clipboard").map(::mcpTool)

    private val agent by lazy {
        OpenAiAssistantAgent(
            gateway,
            connection,
            assistantSettings,
            settingsRepository,
            OpenAiToolCallRunner(connection),
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

    private fun text(message: String) =
        calls(
            GuidanceReply.NAME to
                JsonObject(mapOf("kind" to JsonPrimitive("completed"), "message" to JsonPrimitive(message))).toString(),
        )

    @Test
    fun `plain instructions are withheld and retried as a highlight`() =
        runTest {
            gateway.enqueue(rawText("설정을 누르고 와이파이를 켜세요"))
            gateway.enqueue(calls("android_tap" to """{"x":500,"y":800}"""))
            gateway.enqueue(text("완료했어요"))
            assertEquals(AgentOutcome.Completed, agent.run(userText("와이파이 켜기"), listener))
            assertFalse(listener.events.any { it.contains("설정을 누르고") })
            assertTrue(listener.events.contains("started:android_tap"))
        }

    @Test
    fun `recipient question ends the turn without interacting with the phone`() =
        runTest {
            gateway.enqueue(calls(GuidanceReply.NAME to """{"kind":"question","message":"본인의 나와의 채팅으로 보낼까요?"}"""))
            assertEquals(AgentOutcome.Completed, agent.run(userText("나한테 보내줘"), listener))
            assertEquals(listOf("text:본인의 나와의 채팅으로 보낼까요?"), listener.events)
            coVerify(exactly = 0) { connection.callTool(any(), any()) }
        }

    private fun userText(value: String) = AgentInput.Text(value)

    private fun FakeOpenAiResponsesGateway.Call.toolNames() =
        params
            .tools()
            .getOrNull()
            .orEmpty()
            .map { it.asFunction().name() }

    private fun ResponseInputItem.kind(): String =
        when {
            isEasyInputMessage() -> "user"
            isReasoning() -> "reasoning"
            isResponseOutputMessage() -> "message"
            isFunctionCall() -> "function_call"
            isFunctionCallOutput() -> "function_call_output"
            else -> "other"
        }

    private fun ResponseInputItem.outputText(): String = asFunctionCallOutput().output().asString()

    @Test
    fun `text only answer completes, emits text and configures a stateless low-effort request`() =
        runTest {
            gateway.enqueue(text("Hi"))

            val outcome = agent.run(userText("hello"), listener)

            assertEquals(AgentOutcome.Completed, outcome)
            assertEquals(listOf("text:Hi"), listener.events)
            val call = gateway.calls.single()
            val params = call.params
            assertEquals("key", call.apiKey)
            assertEquals(AssistantConfig.DEFAULT_MODEL, params.model().get().asString())
            assertEquals(ElderGuidancePrompt.TEXT, params.instructions().get())
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
            assertEquals(false, params.parallelToolCalls().get())
            assertEquals(ToolChoiceOptions.REQUIRED, params.toolChoice().get().asOptions())
            assertEquals(listOf("android_tap", "android_get_screen_state", GuidanceReply.NAME), call.toolNames())
            assertTrue(
                params
                    .tools()
                    .get()
                    .filter { it.asFunction().name() != GuidanceReply.NAME }
                    .all { !it.asFunction().strict().get() },
            )
            assertEquals(listOf("user"), call.input.map { it.kind() })
            assertEquals(
                "hello",
                call.input
                    .single()
                    .asEasyInputMessage()
                    .content()
                    .asTextInput(),
            )
        }

    @Test
    fun `without device tools a clarification tool remains available`() =
        runTest {
            coEvery { connection.listTools() } returns Result.success(emptyList())
            gateway.enqueue(text("연결을 확인해 주세요"))
            agent.run(userText("hello"), listener)
            assertEquals(listOf(GuidanceReply.NAME), gateway.calls.single().toolNames())
            assertEquals(
                ToolChoiceOptions.REQUIRED,
                gateway.calls
                    .single()
                    .params
                    .toolChoice()
                    .get()
                    .asOptions(),
            )
        }

    @Test
    fun `tool call runs the loop, forwards arguments as json and replays the output items`() =
        runTest {
            gateway.enqueue(calls("android_tap" to """{"x":500,"y":800.5,"s":"a","b":true}"""))
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
            val input = gateway.calls[1].input
            assertEquals(listOf("user", "reasoning", "function_call", "function_call_output"), input.map { it.kind() })
            assertEquals(
                OpenAiTestResponses.ENCRYPTED_REASONING,
                input[1]
                    .asReasoning()
                    .encryptedContent()
                    .get(),
            )
            assertEquals("call_1", input[2].asFunctionCall().callId())
            assertEquals("call_1", input[3].asFunctionCallOutput().callId().get())
            assertEquals("ok", input[3].outputText())
        }

    @Test
    fun `completed turn keeps the assistant message in the history`() =
        runTest {
            gateway.enqueue(text("a"))
            gateway.enqueue(text("b"))

            agent.run(userText("1"), listener)
            agent.run(userText("2"), listener)

            assertEquals(
                listOf("user", "reasoning", "function_call", "function_call_output", "user"),
                gateway.calls[1].input.map { it.kind() },
            )
        }

    @Test
    fun `several calls in one response are answered in call order`() =
        runTest {
            gateway.enqueue(calls("android_tap" to "{}", "android_get_screen_state" to "{}"))
            gateway.enqueue(text("done"))

            agent.run(userText("go"), listener)

            val outputs = gateway.calls[1].input.filter { it.isFunctionCallOutput() }
            assertEquals(listOf("call_1", "call_2"), outputs.map { it.asFunctionCallOutput().callId().get() })
        }

    @Test
    fun `step limit stops with StepLimitReached and restores history`() =
        runTest {
            config = config.copy(stepLimit = 2)
            repeat(3) { gateway.enqueue(calls("android_tap" to "{}")) }

            val outcome = agent.run(userText("loop"), listener)
            gateway.enqueue(text("again"))
            agent.run(userText("next"), listener)

            assertEquals(AgentOutcome.StepLimitReached(2), outcome)
            assertEquals(
                1,
                gateway.calls
                    .last()
                    .input.size,
            )
            coVerify(exactly = 2) { connection.callTool("android_tap", any()) }
        }

    @Test
    fun `step is counted per response, not per call`() =
        runTest {
            config = config.copy(stepLimit = 1)
            gateway.enqueue(calls("android_tap" to "{}", "android_tap" to "{}", "android_tap" to "{}"))
            gateway.enqueue(text("done"))

            assertEquals(AgentOutcome.Completed, agent.run(userText("x"), listener))
        }

    @Test
    fun `refused tools are not declared and a call to one is answered with an error`() =
        runTest {
            gateway.enqueue(calls("android_set_clipboard" to "{}"))
            gateway.enqueue(text("done"))

            agent.run(userText("x"), listener)

            assertEquals(listOf("android_tap", "android_get_screen_state", GuidanceReply.NAME), gateway.calls[0].toolNames())
            coVerify(exactly = 0) { connection.callTool(any(), any()) }
            assertEquals(listOf("text:done"), listener.events)
            assertEquals("ERROR: Unknown tool.", gateway.calls[1].input[3].outputText())
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

            assertEquals(listOf("android_pixel7_tap", GuidanceReply.NAME), gateway.calls.single().toolNames())
        }

    @Test
    fun `unknown tool and malformed arguments are answered without an MCP call or listener events`() =
        runTest {
            gateway.enqueue(OpenAiTestResponses.response(OpenAiTestResponses.call("nope")))
            gateway.enqueue(OpenAiTestResponses.response(OpenAiTestResponses.call("android_tap", "[1")))
            gateway.enqueue(text("done"))

            agent.run(userText("x"), listener)

            coVerify(exactly = 0) { connection.callTool(any(), any()) }
            assertEquals(listOf("text:done"), listener.events)
            val outputs =
                gateway.calls[2]
                    .input
                    .filter { it.isFunctionCallOutput() }
                    .map { it.outputText() }
            assertEquals(listOf("ERROR: Unknown tool.", "ERROR: The arguments are not a JSON object."), outputs)
        }

    @Test
    fun `tool error and image results are mapped for the model`() =
        runTest {
            coEvery { connection.callTool("android_tap", any()) } returns
                Result.success(CallToolResult(content = listOf(TextContent("bad")), isError = true))
            coEvery { connection.callTool("android_get_screen_state", any()) } returns
                Result.success(CallToolResult(content = listOf(ImageContent("AQID", "image/png"))))
            gateway.enqueue(calls("android_tap" to "{}", "android_get_screen_state" to "{}"))
            gateway.enqueue(text("done"))

            agent.run(userText("x"), listener)

            assertTrue("finished:android_tap:true" in listener.events)
            val outputs = gateway.calls[1].input.filter { it.isFunctionCallOutput() }
            assertEquals("ERROR: bad", outputs[0].outputText())
            val parts =
                outputs[1]
                    .asFunctionCallOutput()
                    .output()
                    .asResponseFunctionCallOutputItemList()
            assertEquals("Image attached.", parts[0].asInputText().text())
            assertEquals(
                "data:image/png;base64,AQID",
                parts[1]
                    .asInputImage()
                    .imageUrl()
                    .get(),
            )
        }

    @Test
    fun `failure is not retried by the agent and keeps earlier turns`() =
        runTest {
            gateway.enqueue(text("one"))
            gateway.enqueueFailure(
                BadRequestException
                    .builder()
                    .headers(headers())
                    .error(errorObject(null))
                    .build(),
            )
            gateway.enqueue(text("three"))

            agent.run(userText("1"), listener)
            val failed = agent.run(userText("2"), listener)
            agent.run(userText("3"), listener)

            assertEquals(AgentOutcome.Failed(AssistantError.ModelRequestFailed("HTTP 400")), failed)
            assertEquals(3, gateway.calls.size)
            assertEquals(
                listOf("user", "reasoning", "function_call", "function_call_output", "user"),
                gateway.calls
                    .last()
                    .input
                    .map { it.kind() },
            )
        }

    @Test
    fun `gateway and response failures map to typed errors`() =
        runTest {
            val cases =
                listOf<Pair<Throwable, AssistantError>>(
                    UnauthorizedException.builder().headers(headers()).build() to AssistantError.ModelAuthFailed,
                    RateLimitException.builder().headers(headers()).build() to AssistantError.ModelQuotaExceeded,
                    InternalServerException
                        .builder()
                        .statusCode(503)
                        .headers(headers())
                        .build() to
                        AssistantError.ModelUnavailable,
                    OpenAIIoException("x") to AssistantError.ModelUnavailable,
                )
            cases.forEach { (failure, expected) ->
                gateway.enqueueFailure(failure)
                assertEquals(AgentOutcome.Failed(expected), agent.run(userText("x"), listener))
            }
            gateway.enqueue(
                OpenAiTestResponses.response(
                    status = "failed",
                    extra = ""","error":{"code":"invalid_prompt","message":"flagged"}""",
                ),
            )
            assertEquals(
                AgentOutcome.Failed(AssistantError.ModelBlocked("invalid_prompt")),
                agent.run(userText("x"), listener),
            )
            gateway.enqueue(OpenAiTestResponses.response(OpenAiTestResponses.refusal("no")))
            assertEquals(
                AgentOutcome.Failed(AssistantError.ModelBlocked("refusal")),
                agent.run(userText("x"), listener),
            )
            assertTrue(listener.events.isEmpty())
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
            gateway.enqueue(calls("android_tap" to "{}"))
            gateway.enqueue(text("ok"))

            val outcome = agent.run(userText("x"), listener)
            agent.run(userText("y"), listener)

            assertEquals(AgentOutcome.Failed(AssistantError.McpUnavailable("IllegalStateException")), outcome)
            assertTrue("finished:android_tap:true" in listener.events)
            assertEquals(
                1,
                gateway.calls
                    .last()
                    .input.size,
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
            gateway.enqueue(calls("android_tap" to "{}"))
            val job = launch { agent.run(userText("cancel me"), listener) }
            started.await()
            job.cancelAndJoin()
            gateway.enqueue(text("after"))

            agent.run(userText("next"), listener)

            assertEquals(
                1,
                gateway.calls
                    .last()
                    .input.size,
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
            assertEquals(5, gateway.calls[1].input.size)
            agent.reset()
            agent.run(userText("3"), listener)

            assertEquals(1, gateway.calls[2].input.size)
        }

    @Test
    fun `model and step limit come from settings`() =
        runTest {
            config = config.copy(model = "gpt-test-model", stepLimit = 1)
            repeat(2) { gateway.enqueue(calls("android_tap" to "{}")) }

            val outcome = agent.run(userText("hi"), listener)

            assertEquals(AgentOutcome.StepLimitReached(1), outcome)
            assertEquals(
                "gpt-test-model",
                gateway.calls[0]
                    .params
                    .model()
                    .get()
                    .asString(),
            )
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
            gateway.enqueue(calls("android_tap" to "{}"))
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
