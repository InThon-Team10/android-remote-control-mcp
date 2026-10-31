package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import io.mockk.coEvery
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

@DisplayName("GeminiAssistantAgent fuzz")
class GeminiAssistantAgentFuzzTest {
    private val realTools = listOf("tap", "get_screen_state", "close_app", "set_clipboard", "take_camera_photo")
    private val gated = AssistantConfig.CONFIRMATION_TOOLS

    private class Harness(
        val stepLimit: Int,
        val slug: String,
        val tools: List<String>,
    ) {
        val gateway = FakeGeminiModelGateway()
        val executed = mutableListOf<String>()
        val connection = mockk<AssistantMcpConnection>()
        val settings = mockk<AssistantSettings>()
        val repository = mockk<SettingsRepository>()
        val agent: GeminiAssistantAgent

        init {
            val config = AssistantConfig(enabled = true, privacyNoticeAccepted = true, stepLimit = stepLimit)
            coEvery { settings.getConfig() } returns config
            coEvery { settings.getApiKey() } returns "key"
            coEvery { repository.getServerConfig() } returns ServerConfig(deviceSlug = slug)
            coEvery { connection.listTools() } returns
                Result.success(tools.map { McpTool(name = it, description = "d", inputSchema = ToolSchema()) })
            coEvery { connection.callTool(any(), any()) } answers {
                executed += firstArg<String>()
                Result.success(CallToolResult(content = listOf(TextContent("ok"))))
            }
            agent = GeminiAssistantAgent(gateway, connection, settings, repository, GeminiToolCallRunner(connection))
        }
    }

    private fun randomToolName(
        random: Random,
        prefix: String,
        real: List<String>,
    ): String =
        when (random.nextInt(6)) {
            0 -> real[random.nextInt(real.size)]
            1 -> real[random.nextInt(real.size)].uppercase()
            2 -> " " + real[random.nextInt(real.size)]
            3 -> real[random.nextInt(real.size)] + "\u200b"
            4 -> prefix + AssistantFuzzGen.identifier(random)
            else -> AssistantFuzzGen.string(random, 10)
        }

    private fun enqueueScript(
        random: Random,
        harness: Harness,
        prefix: String,
        real: List<String>,
    ) {
        repeat(1 + random.nextInt(9)) {
            if (random.nextInt(4) == 0) {
                harness.gateway.enqueue(GeminiTestResponses.text(AssistantFuzzGen.string(random)))
            } else {
                val calls =
                    Array(1 + random.nextInt(3)) {
                        randomToolName(random, prefix, real) to mapOf<String, Any>("a" to random.nextInt())
                    }
                harness.gateway.enqueue(GeminiTestResponses.calls(*calls))
            }
        }
    }

    /** A Completed run keeps its whole turn in the history; any other outcome leaves no trace of it. */
    private suspend fun assertHistoryAfter(
        harness: Harness,
        outcome: AgentOutcome,
        modelCalls: Int,
        label: String,
    ) {
        harness.gateway.enqueue(GeminiTestResponses.text("again"))
        harness.agent.run(AgentInput.Text("next"), RecordingAgentListener())
        val nextHistory = harness.gateway.calls[modelCalls].history
        val expectedSize =
            if (outcome == AgentOutcome.Completed) {
                harness.gateway.calls[modelCalls - 1]
                    .history.size + 2
            } else {
                1
            }
        val roles = nextHistory.map { it.role().orElse("") }
        assertEquals(expectedSize, nextHistory.size, "$label history after ${outcome::class.simpleName}: $roles")
    }

    @Test
    fun `random scripts end in a typed outcome, respect the step limit and never run gated tools when denied`() =
        runTest {
            var completed = 0
            var limited = 0
            for (seed in AssistantFuzzGen.SEEDS) {
                val random = Random(seed)
                repeat(25) { index ->
                    val label = "seed=$seed index=$index"
                    val slug = listOf("", "x", "a_b")[random.nextInt(3)]
                    val prefix = McpToolUtils.buildToolNamePrefix(slug)
                    val real = realTools.map { prefix + it }
                    val harness = Harness(1 + random.nextInt(5), slug, real)
                    enqueueScript(random, harness, prefix, real)

                    val denyAll = RecordingAgentListener(confirmAnswer = { false })
                    val outcome = harness.agent.run(AgentInput.Text("go"), denyAll)

                    val modelCalls = harness.gateway.calls.size
                    assertTrue(modelCalls <= harness.stepLimit + 1, "$label model calls $modelCalls > limit+1")
                    val gatedRan = harness.executed.filter { it.removePrefix(prefix) in gated }
                    assertTrue(gatedRan.isEmpty(), "$label gated tool executed despite deny: $gatedRan")
                    assertTrue(harness.executed.all { it in real }, "$label unknown tool executed: ${harness.executed}")
                    when (outcome) {
                        AgentOutcome.Completed -> {
                            completed++
                        }

                        is AgentOutcome.StepLimitReached -> {
                            limited++
                            assertEquals(harness.stepLimit, outcome.limit, label)
                        }

                        is AgentOutcome.Failed -> {
                            val error = outcome.error
                            assertTrue(error is AssistantError.GeminiRequestFailed, "$label failure $error")
                        }
                    }
                    assertHistoryAfter(harness, outcome, modelCalls, label)
                }
            }
            assertTrue(completed > 0 && limited > 0, "generator coverage completed=$completed limited=$limited")
        }
}
