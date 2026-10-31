package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceAnchor
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceSession
import com.danielealbano.androidremotecontrolmcp.services.guidance.HighlightBounds
import com.danielealbano.androidremotecontrolmcp.services.mcp.AccessibilityToolDeps
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/** Existing model tool calls are stopped before their handler can perform any device action. */
internal object GuidanceToolPolicy {
    private val readTools =
        setOf(
            "get_accessibility_status",
            "get_screen_state",
            "find_nodes",
            "get_node_details",
            "wait_for_idle",
            "wait_for_node",
            "list_apps",
            "get_clipboard",
            "notification_list",
            "list_storage_locations",
            "list_files",
            "read_file",
            "get_location",
            "list_cameras",
            "list_camera_photo_resolutions",
            "list_camera_video_resolutions",
        )
    private val guidanceTools =
        setOf(
            "guidance_start",
            "guidance_status",
            "guidance_show",
            "guidance_complete",
            "guidance_cancel",
        )

    fun allows(toolName: String): Boolean = toolName in readTools || toolName in guidanceTools

    fun blocked(): CallToolResult =
        guidanceError(
            "Guidance only: no action was executed. Use guidance_start, read the screen, then guidance_show " +
                "with the target and instruction. Wait for guidance_status to report NEEDS_ANALYSIS after the user's " +
                "action; read the screen again. Never treat showing a highlight as a successful action.",
        )
}

class GuidanceTools
    @Inject
    constructor(
        private val accessibility: AccessibilityToolDeps,
    ) {
        /** Converts an existing target-based action at the execution boundary into a visual instruction. */
        suspend fun intercept(
            toolName: String,
            arguments: JsonObject?,
        ): CallToolResult {
            if (toolName !in setOf("tap", "long_press", "double_tap", "click_node", "tap_node", "long_click_node")) {
                return GuidanceToolPolicy.blocked()
            }
            val revision = GuidanceController.state.value.revision
            val instruction =
                when (toolName) {
                    "long_press", "long_click_node" -> "표시된 부분을 길게 누르세요."
                    "double_tap" -> "표시된 부분을 두 번 누르세요."
                    else -> "표시된 부분을 누르세요."
                }
            val target =
                buildJsonObject {
                    put("revision", revision)
                    put("instruction", instruction)
                    if (toolName.endsWith("_node")) {
                        val nodeId = arguments?.get("node_id")
                        if (nodeId != null) put("node_id", nodeId)
                    } else {
                        // A 48px box marks the touch point in screen pixels.
                        val x = runCatching { McpToolUtils.requireInt(arguments, "x") }.getOrNull()
                        val y = runCatching { McpToolUtils.requireInt(arguments, "y") }.getOrNull()
                        val validPoint = x != null && y != null
                        if (validPoint && x >= 0 && y >= 0) {
                            put("left", maxOf(0, x - 24))
                            put("top", maxOf(0, y - 24))
                            put("right", x + 24)
                            put("bottom", y + 24)
                        }
                    }
                }
            return execute("show", target)
        }

        fun register(
            registrar: LoggedToolRegistrar,
            prefix: String,
        ) {
            registerTool(
                registrar,
                prefix,
                "start",
                "Start user-operated guidance. Default goal: 카카오톡 켜는법. Analyze the current screen next.",
            )
            registerTool(
                registrar,
                prefix,
                "status",
                "Read phase and screen revision. WAITING_USER means wait. " +
                    "NEEDS_ANALYSIS means read the screen again; it does not imply success.",
            )
            registerTool(
                registrar,
                prefix,
                "show",
                "Highlight one target without executing it. Supply revision from status BEFORE screen analysis, " +
                    "instruction, and node_id or screen bounds left/top/right/bottom. User performs the action.",
            )
            registerTool(
                registrar,
                prefix,
                "complete",
                "Finish only after analyzing the resulting screen and verifying the goal. Supply current revision. " +
                    "Test goal requires KakaoTalk foreground.",
            )
            registerTool(
                registrar,
                prefix,
                "cancel",
                "Cancel guidance and remove the highlight. Does not enable automated actions.",
            )
        }

        private fun registerTool(
            registrar: LoggedToolRegistrar,
            prefix: String,
            operation: String,
            description: String,
        ) {
            val required =
                when (operation) {
                    "show" -> listOf("revision", "instruction")
                    "complete" -> listOf("revision")
                    else -> emptyList()
                }
            registrar.addTool(
                toolName = "guidance_$operation",
                name = "${prefix}guidance_$operation",
                description = description,
                inputSchema =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                for (name in listOf("goal", "instruction", "node_id")) {
                                    putJsonObject(name) { put("type", "string") }
                                }
                                for (name in listOf("revision", "left", "top", "right", "bottom")) {
                                    putJsonObject(name) {
                                        put("type", "integer")
                                        put("minimum", 0)
                                    }
                                }
                            },
                        required = required,
                    ),
            ) { request -> execute(operation, request.arguments) }
        }

        internal suspend fun execute(
            operation: String,
            arguments: JsonObject?,
        ): CallToolResult =
            try {
                val state =
                    when (operation) {
                        "start" -> GuidanceController.start(string(arguments, "goal") ?: GuidanceSession.TEST_GOAL)
                        "status" -> GuidanceController.state.value
                        "show" -> show(arguments)
                        "complete" -> GuidanceController.complete(McpToolUtils.requireLong(arguments, "revision"))
                        "cancel" -> GuidanceController.cancel()
                        else -> error("Unknown guidance operation")
                    }
                McpToolUtils.untrustedTextResult(Json.encodeToString(state))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                guidanceError(error.message ?: "Invalid guidance arguments")
            } catch (error: IllegalStateException) {
                guidanceError(error.message ?: "Guidance unavailable")
            }

        private suspend fun show(arguments: JsonObject?) =
            withContext(Dispatchers.Main.immediate) {
                val revision = McpToolUtils.requireLong(arguments, "revision")
                val instruction = requireNotNull(string(arguments, "instruction")) { "instruction is required" }
                val nodeId = string(arguments, "node_id")
                var anchor: GuidanceAnchor? = null
                val bounds =
                    if (nodeId != null) {
                        require(listOf("left", "top", "right", "bottom").none { arguments?.containsKey(it) == true }) {
                            "Supply node_id or bounds, not both"
                        }
                        val a = accessibility
                        val windows = getFreshWindows(a.treeParser, a.accessibilityServiceProvider, a.nodeCache).windows
                        val node =
                            requireNotNull(a.elementFinder.findNodeById(windows, nodeId)) {
                                "Target no longer exists; analyze again"
                            }
                        require(node.visible && node.enabled) { "Target must be visible and enabled" }
                        node.bounds.let {
                            val target = HighlightBounds(it.left, it.top, it.right, it.bottom)
                            val live = requireNotNull(a.nodeCache.get(nodeId)) { "Target expired; analyze again" }
                            val tracked = GuidanceAnchor(live.node, target)
                            check(tracked.isValid()) { "Target changed; analyze again" }
                            anchor = tracked
                            target
                        }
                    } else {
                        HighlightBounds(
                            McpToolUtils.requireInt(arguments, "left"),
                            McpToolUtils.requireInt(arguments, "top"),
                            McpToolUtils.requireInt(arguments, "right"),
                            McpToolUtils.requireInt(arguments, "bottom"),
                        )
                    }
                GuidanceController.show(revision, instruction, bounds, anchor)
            }

        private fun string(
            arguments: JsonObject?,
            name: String,
        ): String? {
            val value = arguments?.get(name) ?: return null
            require(value is JsonPrimitive && value.isString) { "$name must be a string" }
            return value.content
        }
    }

private fun guidanceError(message: String): CallToolResult =
    CallToolResult(
        content = listOf(TextContent(text = message)),
        isError = true,
    )
