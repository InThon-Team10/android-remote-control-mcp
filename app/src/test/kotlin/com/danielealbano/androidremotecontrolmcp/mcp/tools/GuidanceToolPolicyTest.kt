package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.integration.McpIntegrationTestHelper
import com.danielealbano.androidremotecontrolmcp.mcp.InFlightToolCalls
import com.danielealbano.androidremotecontrolmcp.services.mcp.AccessibilityToolDeps
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidanceToolPolicyTest {
    @Test
    fun `every action category and unknown future tools are blocked`() {
        val actions =
            listOf(
                "tap",
                "click_node",
                "swipe",
                "custom_gesture",
                "type_append_text",
                "press_home",
                "open_app",
                "send_intent",
                "open_uri",
                "set_clipboard",
                "notification_open",
                "write_file",
                "delete_file",
                "take_camera_photo",
                "get_shared_content",
                "future_tool",
            )
        actions.forEach { assertFalse(GuidanceToolPolicy.allows(it), it) }
        listOf("get_screen_state", "find_nodes", "list_apps", "guidance_show").forEach {
            assertTrue(GuidanceToolPolicy.allows(it), it)
        }
    }

    @Test
    fun `guard runs before action handler even without a session`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            val guide =
                GuidanceTools(
                    AccessibilityToolDeps(
                        deps.actionExecutor,
                        deps.accessibilityServiceProvider,
                        deps.treeParser,
                        deps.elementFinder,
                        deps.nodeCache,
                        deps.typeInputController,
                    ),
                )
            val sdk =
                Server(
                    Implementation(name = "test", version = "1"),
                    ServerOptions(
                        capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
                    ),
                )
            val registrar =
                LoggedToolRegistrar(
                    sdk,
                    RecordingServerLogRepository(),
                    inFlightToolCalls = InFlightToolCalls(),
                )
            registrar.guidance = guide
            var executed = false
            registrar.addTool("press_home", "android_press_home", "home", ToolSchema()) {
                executed = true
                CallToolResult(content = listOf(TextContent(text = "executed")))
            }
            val request = mockk<CallToolRequest>()
            every { request.arguments } returns null
            val connection = mockk<ClientConnection>(relaxed = true)
            val result = sdk.tools.getValue("android_press_home").handler(connection, request)
            assertTrue(result.isError == true)
            assertFalse(executed)
        }
}
