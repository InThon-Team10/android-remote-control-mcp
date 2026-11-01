package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.FakeOpenAiResponsesGateway
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.calls
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.text
import com.danielealbano.androidremotecontrolmcp.assistant.agent.RecordingAgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ElderToolGuidance
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceState
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

@OptIn(ExperimentalCoroutinesApi::class)
class GuideStepsImplTest {
    private val gateway = FakeOpenAiResponsesGateway()
    private val connection = mockk<AssistantMcpConnection>()
    private val assistantSettings = mockk<AssistantSettings>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val state = MutableStateFlow<GuidanceState?>(null)
    private val controller =
        mockk<GuidanceController>(relaxed = true) { every { this@mockk.state } returns this@GuideStepsImplTest.state }
    private val listener = RecordingAgentListener()
    private val highlightGate = CompletableDeferred<String>()
    private var stepId = 0L
    private val screen = "node_1\tbutton\t설정"
    private val tools =
        listOf("get_screen_state", "find_nodes", "click_node", "set_clipboard")
            .map { McpTool(name = "android_$it", description = "d", inputSchema = ToolSchema()) }

    private fun TestScope.guide() =
        GuideStepsImpl(
            gateway,
            connection,
            assistantSettings,
            settingsRepository,
            controller,
            StandardTestDispatcher(testScheduler),
        )

    private fun result(text: String) = Result.success(CallToolResult(content = listOf(TextContent(text = text))))

    private fun setUp() {
        coEvery { assistantSettings.getConfig() } returns
            AssistantConfig(enabled = true, privacyNoticeAccepted = true, apiKeyConfigured = true)
        coEvery { assistantSettings.getApiKey() } returns "key"
        coEvery { settingsRepository.getServerConfig() } returns ServerConfig(deviceSlug = "")
        coEvery { connection.listTools() } returns Result.success(tools)
        coEvery { connection.callTool("android_get_screen_state", any()) } returns result(screen)
        coEvery { connection.callTool("android_find_nodes", any()) } returns result("found node_1")
        coEvery { connection.callTool("android_click_node", any()) } coAnswers {
            state.value = GuidanceState(++stepId, GuidanceTarget(BoundsData(0, 0, 1, 1), GuidanceKind.TAP, "x"))
            result(highlightGate.await())
        }
    }

    private val click = "click_node" to """{"node_id":"node_1","instruction":"설정을 눌러 주세요"}"""

    @Test
    fun `visible step returns the instruction while the highlight call keeps waiting`() =
        runTest {
            setUp()
            gateway.enqueue(calls(click))
            val guide = guide()

            val deferred = async { guide.showNextStep("설정 열기", listener) }
            advanceUntilIdle()
            val step = deferred.await()

            assertEquals("설정을 눌러 주세요", step.say)
            assertTrue(step.showing)
            assertEquals(listOf("before"), listener.events)
            assertFalse(guide.planning.value)
            highlightGate.complete(ElderToolGuidance.USER_ACTED_TEXT)
        }

    @Test
    fun `planner request carries the prefetched screen and only the planner tools`() =
        runTest {
            setUp()
            gateway.enqueue(calls("answer_user" to """{"kind":"done","sentence":"끝"}"""))
            val guide = guide()

            guide.showNextStep("와이파이 켜기", listener)

            val params = gateway.calls.single().params
            val names = params.tools().get().map { it.asFunction().name() }
            assertEquals(listOf("get_screen_state", "find_nodes", "click_node", "answer_user"), names)
            assertTrue(
                gateway.calls
                    .single()
                    .input
                    .toString()
                    .contains("와이파이 켜기"),
            )
            assertTrue(
                gateway.calls
                    .single()
                    .input
                    .toString()
                    .contains(screen),
            )
            coVerify {
                connection.callTool(
                    "android_get_screen_state",
                    match<JsonObject> {
                        it["include_offscreen"] == JsonPrimitive(false) &&
                            it["include_hierarchy"] == JsonPrimitive(false)
                    },
                )
            }
        }

    @Test
    fun `read only call loops and an early highlight error is fed back to the planner`() =
        runTest {
            setUp()
            coEvery { connection.callTool("android_click_node", any()) } returns
                result(ElderToolGuidance.NODE_GONE_TEXT)
            gateway.enqueue(calls("find_nodes" to """{"by":"text","value":"설정"}"""))
            gateway.enqueue(calls(click))
            gateway.enqueue(calls("answer_user" to """{"kind":"say","sentence":"다시 볼게요"}"""))
            val guide = guide()

            val step = async { guide.showNextStep("설정 열기", listener) }
            advanceUntilIdle()

            assertEquals(GuideStepResult("다시 볼게요", showing = false, kind = "say"), step.await())
            assertEquals(3, gateway.calls.size)
            assertTrue(
                gateway.calls
                    .last()
                    .input
                    .toString()
                    .contains("no longer on the screen"),
            )
        }

    @Test
    fun `answer_user ends the step in words`() =
        runTest {
            setUp()
            gateway.enqueue(calls("answer_user" to """{"kind":"ask","sentence":"어느 앱인가요?"}"""))

            val step = guide().showNextStep("열기", listener)

            assertEquals(GuideStepResult("어느 앱인가요?", showing = false, kind = "ask"), step)
            assertEquals("""{"say":"어느 앱인가요?","kind":"ask"}""", step.toJson().toString())
        }

    @Test
    fun `plain text reply is treated as an answer`() =
        runTest {
            setUp()
            gateway.enqueue(text("done: 이미 켜져 있어요"))
            gateway.enqueue(text("""{"kind":"ask","sentence":"무엇을 할까요?"}"""))
            gateway.enqueue(text("그냥 말해요"))
            val guide = guide()

            assertEquals(GuideStepResult("이미 켜져 있어요", false, "done"), guide.showNextStep("a", listener))
            assertEquals(GuideStepResult("무엇을 할까요?", false, "ask"), guide.showNextStep("a", listener))
            assertEquals(GuideStepResult("그냥 말해요", false, "say"), guide.showNextStep("a", listener))
        }

    @Test
    fun `history of shown steps and outcomes is passed to the planner`() =
        runTest {
            setUp()
            gateway.enqueue(calls(click))
            gateway.enqueue(calls("answer_user" to """{"kind":"done","sentence":"끝"}"""))
            val guide = guide()

            val first = async { guide.showNextStep("설정 열기", listener) }
            advanceUntilIdle()
            first.await()
            highlightGate.complete(ElderToolGuidance.USER_ACTED_TEXT)
            advanceUntilIdle()
            guide.showNextStep("설정 열기", listener)

            val sent =
                gateway.calls
                    .last()
                    .input
                    .toString()
            assertTrue(sent.contains("1. '설정을 눌러 주세요' -> the user did it"))
        }

    @Test
    fun `wait_for_user resolves with the outcome and then reports nothing highlighted`() =
        runTest {
            setUp()
            gateway.enqueue(calls(click))
            val guide = guide()
            val step = async { guide.showNextStep("g", listener) }
            advanceUntilIdle()
            step.await()

            val waiting = async { guide.waitForUser() }
            advanceUntilIdle()
            assertFalse(waiting.isCompleted)
            highlightGate.complete(ElderToolGuidance.SCREEN_CHANGED_TEXT)
            advanceUntilIdle()

            assertEquals(GuideOutcomeTexts.SCREEN_CHANGED, waiting.await())
            assertEquals(GuideOutcomeTexts.NOTHING_HIGHLIGHTED, guide.waitForUser())
        }

    @Test
    fun `wait_for_user answers when its highlight is superseded by a reset`() =
        runTest {
            setUp()
            gateway.enqueue(calls(click))
            val guide = guide()
            val step = async { guide.showNextStep("g", listener) }
            advanceUntilIdle()
            step.await()
            val waiting = async { guide.waitForUser() }
            advanceUntilIdle()

            guide.reset()
            advanceUntilIdle()

            assertEquals(GuideOutcomeTexts.REPLACED, waiting.await())
        }

    @Test
    fun `wait_for_user after a timeout waits again on the same highlight`() =
        runTest {
            setUp()
            gateway.enqueue(calls(click))
            val guide = guide()
            val step = async { guide.showNextStep("g", listener) }
            advanceUntilIdle()
            step.await()
            highlightGate.complete(ElderToolGuidance.TIMED_OUT_TEXT)
            advanceUntilIdle()

            assertEquals(GuideOutcomeTexts.TIMED_OUT, guide.waitForUser())
            coEvery { connection.callTool("android_click_node", any()) } coAnswers {
                CompletableDeferred<Unit>().await()
                result("never")
            }
            val again = async { guide.waitForUser() }
            advanceUntilIdle()

            assertFalse(again.isCompleted)
            coVerify(exactly = 2) { connection.callTool("android_click_node", any()) }
            again.cancel()
        }

    @Test
    fun `cancelling a run clears the guidance and drops the pending call`() =
        runTest {
            setUp()
            val release = CompletableDeferred<Unit>()
            gateway.enqueue(calls("find_nodes" to """{"by":"text","value":"x"}"""))
            coEvery { connection.callTool("android_find_nodes", any()) } coAnswers {
                release.await()
                result("late")
            }
            val guide = guide()

            val step = async { guide.showNextStep("g", listener) }
            advanceUntilIdle()
            step.cancelAndJoin()

            verify { controller.cancel() }
            assertFalse(guide.planning.value)
            assertEquals(GuideOutcomeTexts.NOTHING_HIGHLIGHTED, guide.waitForUser())
        }

    @Test
    fun `planner call limit ends with a spoken fallback`() =
        runTest {
            setUp()
            repeat(6) { gateway.enqueue(calls("find_nodes" to """{"by":"text","value":"x"}""")) }

            val step = guide().showNextStep("g", listener)

            assertEquals(6, gateway.calls.size)
            assertFalse(step.showing)
            assertEquals("say", step.kind)
        }

    @Test
    fun `missing api key fails with a typed error and planning is reset`() =
        runTest {
            setUp()
            coEvery { assistantSettings.getApiKey() } returns null
            val guide = guide()

            val error = assertThrows<AssistantException> { guide.showNextStep("g", listener) }

            assertEquals(AssistantError.ApiKeyMissing, error.error)
            assertFalse(guide.planning.value)
            assertNull(gateway.calls.firstOrNull())
        }
}
