package com.danielealbano.androidremotecontrolmcp.services.guidance

import com.danielealbano.androidremotecontrolmcp.mcp.tools.GetScreenStateHandler
import com.danielealbano.androidremotecontrolmcp.mcp.tools.GuidanceTools
import com.danielealbano.androidremotecontrolmcp.services.accessibility.McpAccessibilityService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeepSeekGuidanceAgentTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(GuidanceController, McpAccessibilityService.Companion)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `changed screen cancels old analysis and waiting user never advances itself`() =
        runTest {
            val states = MutableStateFlow(GuidanceState())
            val service = mockk<McpAccessibilityService>()
            every { McpAccessibilityService.instance } returns service
            every { service.getServiceScope() } returns backgroundScope
            every { GuidanceController.state } returns states
            coEvery { GuidanceController.cancel() } answers {
                states.value = states.value.copy(phase = GuidancePhase.CANCELLED)
                states.value
            }
            coEvery { GuidanceController.start(any()) } answers {
                states.value = GuidanceState(goal = firstArg(), revision = 1, phase = GuidancePhase.NEEDS_ANALYSIS)
                states.value
            }
            val screen = mockk<GetScreenStateHandler>()
            val planner = mockk<DeepSeekGuidancePlanner>()
            val guidance = mockk<GuidanceTools>()
            val ok = CallToolResult(content = listOf(TextContent(text = "screen")))
            coEvery { screen.execute(any()) } returns ok
            val attempts = mutableListOf<CompletableDeferred<GuidanceDecision>>()
            coEvery { planner.plan(any(), any(), any()) } coAnswers {
                CompletableDeferred<GuidanceDecision>().also { attempts.add(it) }.await()
            }
            coEvery { guidance.execute("show", any()) } answers {
                states.value = states.value.copy(phase = GuidancePhase.WAITING_USER)
                ok
            }
            val agent = DeepSeekGuidanceAgent(screen, planner, guidance)
            agent.start("fake-key")
            advanceTimeBy(601)
            runCurrent()
            assertEquals(1, attempts.size)
            states.value = states.value.copy(revision = 2)
            runCurrent()
            advanceTimeBy(601)
            runCurrent()
            assertEquals(2, attempts.size)
            attempts[0].complete(GuidanceDecision("guide", "stale", nodeId = "old"))
            attempts[1].complete(GuidanceDecision("guide", "current", nodeId = "new"))
            runCurrent()
            advanceTimeBy(10000)
            runCurrent()
            coVerify(exactly = 1) {
                guidance.execute("show", match { it.get("revision")?.jsonPrimitive?.content == "2" })
            }
            assertEquals(2, attempts.size)
            assertEquals(GuidancePhase.WAITING_USER, states.value.phase)
            agent.stop()
        }
}
