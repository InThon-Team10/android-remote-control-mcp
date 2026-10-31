package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/** Exposes diagnostics from the existing Android accessibility service even while it is disconnected. */
class GetAccessibilityStatusTool
    @Inject
    constructor(
        private val accessibilityServiceProvider: AccessibilityServiceProvider,
    ) {
        suspend fun execute(): CallToolResult {
            val status = accessibilityServiceProvider.getStatus()
            val result =
                buildJsonObject {
                    put("enabled", status.enabled)
                    put("connected", status.connected)
                    putJsonObject("capabilities") {
                        put("can_retrieve_window_content", status.capabilities.canRetrieveWindowContent)
                        put("can_perform_gestures", status.capabilities.canPerformGestures)
                        put("can_take_screenshot", status.capabilities.canTakeScreenshot)
                        put("can_retrieve_interactive_windows", status.capabilities.canRetrieveInteractiveWindows)
                        put("can_report_view_ids", status.capabilities.canReportViewIds)
                    }
                    put("active_window_available", status.activeWindowAvailable)
                    put("interactive_window_count", status.interactiveWindowCount)
                }
            return McpToolUtils.textResult(result.toString())
        }

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Check Android accessibility service enablement, connection, granted capabilities, " +
                        "and window availability. Works even when accessibility is disabled. " +
                        "Does not read screen text or change the screen.",
                inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
            ) { execute() }
        }

        companion object {
            const val TOOL_NAME = "get_accessibility_status"
        }
    }
