package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.agent.FakeGeminiModelGateway
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiTestResponses
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityCapabilities
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelperAssistantConnectionTest {
    private val screen =
        "screen:720x1600 density:280 orientation:portrait\n" +
            "--- window:1 type:APPLICATION pkg:example title:Example layer:0 focused:true ---\n" +
            "node_id\tclass\ttext\tdesc\tres_id\tbounds\tflags\n" +
            "1:1\tButton\tSearch\t-\t-\t10,20,200,80\ton,clk,ena"
    private val json = """{"nodeId":"1:1","explanation":"검색 화면으로 이동할 수 있어요.","action":"검색 버튼을 눌러 주세요."}"""
    private val connection = mockk<AssistantMcpConnection>()
    private val settings = mockk<AssistantSettings>()
    private val serverSettings = mockk<SettingsRepository>()
    private val starter = mockk<AssistantServerStarter>()
    private val accessibility = mockk<AccessibilityServiceProvider>()
    private val gateway = FakeGeminiModelGateway()
    private val context = mockk<Context>()
    private val adapter = HelperAssistantConnection(connection, gateway, settings, serverSettings, starter, accessibility)

    private fun setup() {
        coEvery { settings.getConfig() } returns AssistantConfig(enabled = true, privacyNoticeAccepted = true, apiKeyConfigured = true)
        coEvery { settings.getApiKey() } returns "test-key"
        coEvery { accessibility.getStatus() } returns
            AccessibilityServiceStatus(
                connected = true,
                capabilities = AccessibilityCapabilities(canRetrieveWindowContent = true),
            )
        coEvery { starter.ensureRunning(context) } returns Result.success(Unit)
        coEvery { serverSettings.getServerConfig() } returns ServerConfig(deviceSlug = "")
        coEvery { connection.listTools() } returns
            Result.success(listOf(Tool(name = "android_get_screen_state", inputSchema = ToolSchema())))
        coEvery { connection.callTool(any(), any()) } returns Result.success(CallToolResult(content = listOf(TextContent(screen))))
        gateway.enqueue(GeminiTestResponses.text(json))
    }

    @Test fun `both variants consume same validated answer without action tools`() =
        runTest {
            setup()
            val answer = adapter.request(context, "검색하고 싶어요")
            assertEquals(parseHelperScreen(screen).nodes["1:1"], answer.step.target)
            assertFalse(answer.step.confirmed)
            GuideVariant.entries.forEach { variant ->
                val state = ConsequenceState()
                state.begin(answer.step, variant)
                assertEquals(if (variant == GuideVariant.A) ConsequencePhase.EXPLANATION else ConsequencePhase.TARGET, state.phase)
            }
            assertTrue(
                gateway.calls
                    .single()
                    .config
                    .tools()
                    .isEmpty,
            )
            coVerify(
                exactly = 2,
            ) { connection.callTool("android_get_screen_state", match { it["include_screenshot"].toString() == "false" }) }
            coVerify(exactly = 2) { connection.callTool(any(), any()) }
        }

    @Test fun `missing consent never reads screen or calls Gemini`() =
        runTest {
            setup()
            coEvery { settings.getConfig() } returns AssistantConfig(enabled = true, apiKeyConfigured = true)
            val failure = runCatching { adapter.request(context, "help") }.exceptionOrNull()
            assertTrue(failure is HelperGuideException)
            assertTrue(gateway.calls.isEmpty())
            coVerify(exactly = 0) { connection.callTool(any(), any()) }
        }

    @Test fun `changed screen rejects late response`() =
        runTest {
            setup()
            coEvery { connection.callTool(any(), any()) } returnsMany
                listOf(
                    Result.success(CallToolResult(content = listOf(TextContent(screen)))),
                    Result.success(CallToolResult(content = listOf(TextContent(screen.replace("Search", "Pay"))))),
                )
            assertTrue(runCatching { adapter.request(context, "help") }.exceptionOrNull() is HelperGuideException)
        }

    @Test fun `model cannot invent target or mark disabled and offscreen nodes clickable`() {
        val parsed = parseHelperScreen(screen)
        assertThrows(HelperGuideException::class.java) { decodeHelperAnswer(json.replace("1:1", "fake"), parsed) }
        assertTrue(parseHelperScreen(screen.replace("on,clk,ena", "off,clk,ena")).nodes.isEmpty())
        assertTrue(parseHelperScreen(screen.replace("on,clk,ena", "on,clk")).nodes.isEmpty())
        assertTrue(parseHelperScreen(screen.replace("10,20,200,80", "NaN,20,200,80")).nodes.isEmpty())
        assertTrue(parseHelperScreen(screen.replace("type:APPLICATION", "type:ACCESSIBILITY_OVERLAY")).nodes.isEmpty())
        assertEquals(androidx.compose.ui.geometry.Rect.Zero, decodeHelperAnswer(json.replace("\"1:1\"", "null"), parsed).step.target)
    }
}
