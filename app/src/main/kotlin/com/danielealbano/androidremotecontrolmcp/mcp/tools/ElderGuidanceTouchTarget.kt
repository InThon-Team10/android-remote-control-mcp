package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind

/**
 * Where the user touches for a node highlight, and whether they can touch it there: pure functions over the nodes of
 * one tree read, in screen pixels.
 */
internal object ElderGuidanceTouchTarget {
    /** Kinds the user does by touching the target itself: a target mostly drawn over by something else is refused. */
    val COVERAGE_CHECKED_KINDS: Set<GuidanceKind> =
        setOf(GuidanceKind.TAP, GuidanceKind.DOUBLE_TAP, GuidanceKind.LONG_PRESS, GuidanceKind.TYPE)

    /** A target with fewer clear sample points than this (of five) is mostly hidden. */
    private const val MIN_CLEAR_POINTS = 3

    /** An ancestor taller than 1/[SCREEN_PARTS] of the screen is a container, not the row around a label. */
    private const val SCREEN_PARTS = 3
    private const val QUARTER = 0.25f
    private const val HALF = 0.5f
    private const val THREE_QUARTERS = 0.75f

    /** The sample points of a target, as fractions of its width and height: the centre, then the four 25%/75% ones. */
    private val SAMPLE_POINTS =
        listOf(
            HALF to HALF,
            QUARTER to QUARTER,
            THREE_QUARTERS to QUARTER,
            QUARTER to THREE_QUARTERS,
            THREE_QUARTERS to THREE_QUARTERS,
        )

    /**
     * The node a [kind] highlight on [node] shows. For a tap or double tap on a node that is not clickable (for a long
     * press: not long-clickable), such as a label, it is the nearest ancestor that is clickable (long-clickable),
     * visible and at most a third of [screenHeight] high: the label's row. Otherwise, and for the other kinds, it is
     * [node].
     */
    fun actionableNode(
        windows: List<WindowData>,
        node: AccessibilityNodeData,
        kind: GuidanceKind,
        screenHeight: Int,
    ): AccessibilityNodeData {
        val handles = touchHandler(kind)
        if (handles == null || handles(node)) return node
        val ancestors = ElderGuidanceGeometry.pathTo(windows, node.id)?.dropLast(1).orEmpty()
        return ancestors.lastOrNull {
            handles(it) && it.visible && (it.bounds.bottom - it.bounds.top) * SCREEN_PARTS <= screenHeight
        } ?: node
    }

    /**
     * True when [target] is mostly drawn over by something else (a floating bar, another window): fewer than
     * [MIN_CLEAR_POINTS] of five points of [bounds] (its centre and the points at 25% and 75% of its width and height)
     * are clear, i.e. have [target], one of its descendants or one of its ancestors on top. False when [target] is not
     * in [windows].
     */
    fun isMostlyHidden(
        windows: List<WindowData>,
        target: AccessibilityNodeData,
        bounds: BoundsData,
    ): Boolean {
        val targetPath = ElderGuidanceGeometry.pathTo(windows, target.id) ?: return false
        return samplePoints(bounds).count { (x, y) -> isClearAt(windows, targetPath, x, y) } < MIN_CLEAR_POINTS
    }

    /** What makes a node handle a [kind] touch itself; null for the kinds that always keep their node. */
    private fun touchHandler(kind: GuidanceKind): ((AccessibilityNodeData) -> Boolean)? =
        when (kind) {
            GuidanceKind.TAP, GuidanceKind.DOUBLE_TAP -> AccessibilityNodeData::clickable
            GuidanceKind.LONG_PRESS -> AccessibilityNodeData::longClickable
            GuidanceKind.TYPE, GuidanceKind.SCROLL -> null
        }

    /**
     * True when the node on top at the point is the last node of [targetPath] or one of its descendants (the target is
     * on the topmost path) or one of its ancestors (the topmost node is on the target's path). Nodes of the same read
     * are compared by identity, so a node of another window never counts.
     */
    private fun isClearAt(
        windows: List<WindowData>,
        targetPath: List<AccessibilityNodeData>,
        x: Float,
        y: Float,
    ): Boolean {
        val topPath = ElderGuidanceGeometry.topmostPathAt(windows, x, y) ?: return false
        val target = targetPath.last()
        val top = topPath.last()
        return topPath.any { it === target } || targetPath.any { it === top }
    }

    private fun samplePoints(bounds: BoundsData): List<Pair<Float, Float>> {
        val width = (bounds.right - bounds.left).toFloat()
        val height = (bounds.bottom - bounds.top).toFloat()
        return SAMPLE_POINTS.map { (fx, fy) -> Pair(bounds.left + width * fx, bounds.top + height * fy) }
    }
}
