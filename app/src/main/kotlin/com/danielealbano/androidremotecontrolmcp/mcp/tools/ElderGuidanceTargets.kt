package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.privacy.PlaceholderSubstitutor
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeCache
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityTreeParser
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementFinder
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScreenInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind
import com.danielealbano.androidremotecontrolmcp.services.guidance.ScreenPoint
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.JsonObject

/** What a guided tool call turns into: a step to highlight, or an answer to return as is. */
internal sealed interface GuidanceStep {
    data class Show(
        val bounds: BoundsData,
        val kind: GuidanceKind,
        val scrollDirection: ScrollDirection? = null,
        val swipeStart: ScreenPoint? = null,
    ) : GuidanceStep

    data class Answer(
        val result: CallToolResult,
    ) : GuidanceStep
}

/**
 * Validates the parameters of a HIGHLIGHT tool exactly like the tool does, and resolves what it would act on
 * (through the same [NodeTargetResolver] and a fresh read) into the bounds to highlight. Never acts on the device.
 */
internal class ElderGuidanceTargets(
    private val treeParser: AccessibilityTreeParser,
    private val accessibilityServiceProvider: AccessibilityServiceProvider,
    private val nodeCache: AccessibilityNodeCache,
    private val elementFinder: ElementFinder,
    private val nodeTargetResolver: NodeTargetResolver,
    substitutor: PlaceholderSubstitutor,
) {
    private val typeParams = ElderTypeParams(substitutor)

    suspend fun resolve(
        toolName: String,
        arguments: JsonObject?,
    ): GuidanceStep =
        when (toolName) {
            "tap" -> pointStep(arguments, GuidanceKind.TAP)
            "long_press" -> pointStep(arguments, GuidanceKind.LONG_PRESS)
            "double_tap" -> pointStep(arguments, GuidanceKind.DOUBLE_TAP)
            "click_node", "tap_node" -> nodeStep(arguments, GuidanceKind.TAP)
            "long_click_node" -> nodeStep(arguments, GuidanceKind.LONG_PRESS)
            "scroll_node" -> scrollNodeStep(arguments)
            "scroll_to_node" -> scrollToNodeStep(arguments)
            "swipe" -> swipeStep(arguments)
            else -> typeStep(toolName, arguments)
        }

    private fun pointStep(
        arguments: JsonObject?,
        kind: GuidanceKind,
    ): GuidanceStep {
        val x = McpToolUtils.requireFloat(arguments, "x")
        val y = McpToolUtils.requireFloat(arguments, "y")
        McpToolUtils.validateNonNegative(x, "x")
        McpToolUtils.validateNonNegative(y, "y")
        if (kind == GuidanceKind.LONG_PRESS) {
            val duration = McpToolUtils.optionalLong(arguments, "duration", 1L)
            McpToolUtils.validatePositiveRange(duration, "duration", McpToolUtils.MAX_DURATION_MS)
        }
        val screen = accessibilityServiceProvider.getScreenInfo()
        val windows = freshWindows().windows
        val bounds =
            ElderGuidanceGeometry
                .nodeBoundsAt(windows, x, y)
                ?.let { ElderGuidanceGeometry.clamp(it, screen.width, screen.height) }
                ?: ElderGuidanceGeometry.fallbackBox(x, y, screen.densityDpi, screen.width, screen.height)
                ?: throw McpToolException.InvalidParams("The point ($x, $y) is outside the screen")
        return GuidanceStep.Show(bounds, kind)
    }

    /**
     * The swipe the user should make (e.g. up from the home screen to open the app list), with its start point on
     * screen so the hint shows where the finger goes down; nothing is swiped.
     */
    private fun swipeStep(arguments: JsonObject?): GuidanceStep {
        val x1 = McpToolUtils.requireFloat(arguments, "x1")
        val y1 = McpToolUtils.requireFloat(arguments, "y1")
        val x2 = McpToolUtils.requireFloat(arguments, "x2")
        val y2 = McpToolUtils.requireFloat(arguments, "y2")
        val duration = McpToolUtils.optionalLong(arguments, "duration", SWIPE_DEFAULT_DURATION_MS)
        McpToolUtils.validateNonNegative(x1, "x1")
        McpToolUtils.validateNonNegative(y1, "y1")
        McpToolUtils.validateNonNegative(x2, "x2")
        McpToolUtils.validateNonNegative(y2, "y2")
        McpToolUtils.validatePositiveRange(duration, "duration", McpToolUtils.MAX_DURATION_MS)
        val screen = accessibilityServiceProvider.getScreenInfo()
        val bounds =
            ElderGuidanceGeometry.swipeArea(x1 to y1, x2 to y2, screen.densityDpi, screen.width, screen.height)
                ?: throw McpToolException.InvalidParams("The swipe ($x1, $y1) -> ($x2, $y2) is outside the screen")
        return GuidanceStep.Show(
            bounds,
            GuidanceKind.SCROLL,
            ElderGuidanceGeometry.swipeDirection(x2 - x1, y2 - y1),
            ElderGuidanceGeometry.clampPoint(x1, y1, screen.width, screen.height),
        )
    }

    /**
     * Highlights the resolved node, or for a tap-like step on a label its clickable row (see
     * [ElderGuidanceTouchTarget.actionableNode]). A tap-like or type target the user could not touch because something
     * else is drawn over most of it (see [ElderGuidanceTouchTarget.hiddenCoverage]) is not highlighted: the model is
     * told to bring it into view first, and the coverage (no device text) is logged.
     */
    private suspend fun nodeStep(
        arguments: JsonObject?,
        kind: GuidanceKind,
        direction: ScrollDirection? = null,
    ): GuidanceStep {
        val target = nodeTargetResolver.parse(arguments)
        val windows = freshWindows().windows
        val node =
            when (val found = findNode(target, windows, onScreenOnly = true)) {
                is Found.Node -> found.node
                is Found.Answer -> return GuidanceStep.Answer(found.result)
            }
        val screen = accessibilityServiceProvider.getScreenInfo()
        val shown = ElderGuidanceTouchTarget.actionableNode(windows, node, kind, screen.height)
        val bounds = onScreen(shown.bounds, screen)
        val hidden = ElderGuidanceTouchTarget.hiddenCoverage(windows, shown, bounds, kind)
        return if (hidden != null) {
            Log.i(
                TAG,
                "$kind target mostly hidden (${hidden.clearPoints}/5 clear, covered by ${hidden.coveringWindowTypes})",
            )
            GuidanceStep.Answer(McpToolUtils.textResult(ElderToolGuidance.HIDDEN_TARGET_TEXT).copy(isError = true))
        } else {
            GuidanceStep.Show(bounds, kind, direction)
        }
    }

    private suspend fun typeStep(
        toolName: String,
        arguments: JsonObject?,
    ): GuidanceStep {
        typeParams.validate(toolName, arguments)
        return nodeStep(arguments, GuidanceKind.TYPE)
    }

    private suspend fun scrollNodeStep(arguments: JsonObject?): GuidanceStep {
        val direction = requireScrollDirection(arguments)
        return nodeStep(arguments, GuidanceKind.SCROLL, direction)
    }

    /**
     * A node the user can already touch (visible and not mostly drawn over) is answered as already on screen;
     * otherwise its nearest scrollable ancestor is highlighted, so a highlight refused as hidden is not followed by
     * an "already on screen" answer.
     */
    private suspend fun scrollToNodeStep(arguments: JsonObject?): GuidanceStep {
        val target = nodeTargetResolver.parse(arguments)
        val direction = requireScrollDirection(arguments)
        val windows = freshWindows().windows
        val node =
            when (val found = findNode(target, windows, onScreenOnly = false)) {
                is Found.Node -> found.node
                is Found.Answer -> return GuidanceStep.Answer(found.result)
            }
        val screen = accessibilityServiceProvider.getScreenInfo()
        val inView =
            node.visible &&
                ElderGuidanceGeometry
                    .clamp(node.bounds, screen.width, screen.height)
                    ?.let { !ElderGuidanceTouchTarget.isMostlyHidden(windows, node, it) } == true
        return if (inView) {
            GuidanceStep.Answer(McpToolUtils.textResult(ALREADY_ON_SCREEN_TEXT))
        } else {
            val container =
                ElderGuidanceGeometry.scrollableAncestor(windows, node.id)
                    ?: throw McpToolException.ActionFailed("No scrollable container found for node '${node.id}'")
            GuidanceStep.Show(onScreen(container.bounds, screen), GuidanceKind.SCROLL, direction)
        }
    }

    private sealed interface Found {
        data class Node(
            val node: AccessibilityNodeData,
        ) : Found

        data class Answer(
            val result: CallToolResult,
        ) : Found
    }

    private suspend fun findNode(
        target: NodeTarget,
        windows: List<WindowData>,
        onScreenOnly: Boolean,
    ): Found {
        val nodeId =
            when (val resolution = nodeTargetResolver.resolve(target, windows, onScreenOnly)) {
                is NodeResolution.Resolved -> resolution.nodeId
                is NodeResolution.Ambiguous -> return Found.Answer(resolution.result)
            }
        val node =
            elementFinder.findNodeById(windows, nodeId)
                ?: throw McpToolException.NodeNotFound("Node '$nodeId' not found")
        return Found.Node(node)
    }

    /** [bounds] limited to the [screen]; a node with nothing on screen cannot be highlighted. */
    private fun onScreen(
        bounds: BoundsData,
        screen: ScreenInfo,
    ): BoundsData =
        ElderGuidanceGeometry.clamp(bounds, screen.width, screen.height)
            ?: throw McpToolException.ActionFailed("The target is not on screen: scroll to it first")

    private fun freshWindows() = getFreshWindows(treeParser, accessibilityServiceProvider, nodeCache)

    companion object {
        private const val TAG = "MCP:ElderGuidance"

        const val ALREADY_ON_SCREEN_TEXT =
            "The target is already on screen, nothing to scroll. Call get_screen_state to see it and guide the " +
                "user to it with a highlight."
    }
}

/** Same default as the swipe tool, used only to validate the optional duration. */
private const val SWIPE_DEFAULT_DURATION_MS = 300L
