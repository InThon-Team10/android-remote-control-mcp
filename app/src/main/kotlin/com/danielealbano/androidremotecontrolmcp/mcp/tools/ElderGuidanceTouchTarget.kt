package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind

/**
 * What is on top at the five sample points of a highlight (see [ElderGuidanceTouchTarget.coverage]). Carries no
 * device text.
 */
internal data class Coverage(
    /** Points with the target, one of its descendants or one of its ancestors on top. */
    val clearPoints: Int,
    /** Type of the window on top at each other point ([ElderGuidanceTouchTarget.NO_WINDOW] where none takes it). */
    val coveringWindowTypes: Set<String>,
) {
    /** Fewer than [ElderGuidanceTouchTarget.MIN_CLEAR_POINTS] points are clear: the user cannot touch the target. */
    val isMostlyHidden: Boolean get() = clearPoints < ElderGuidanceTouchTarget.MIN_CLEAR_POINTS
}

/**
 * Where the user touches for a node highlight, and whether they can touch it there: pure functions over the nodes of
 * one tree read, in screen pixels.
 */
internal object ElderGuidanceTouchTarget {
    /** Kinds the user does by touching the target itself: a target mostly drawn over by something else is refused. */
    val COVERAGE_CHECKED_KINDS: Set<GuidanceKind> =
        setOf(GuidanceKind.TAP, GuidanceKind.DOUBLE_TAP, GuidanceKind.LONG_PRESS, GuidanceKind.TYPE)

    /** A target with fewer clear sample points than this (of five) is mostly hidden. */
    const val MIN_CLEAR_POINTS = 3

    /** The covering window type of a sample point that no window takes. */
    const val NO_WINDOW = "NONE"

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
     * [MIN_CLEAR_POINTS] of five points of [bounds] are clear (see [coverage]). False when [target] is not in
     * [windows].
     */
    fun isMostlyHidden(
        windows: List<WindowData>,
        target: AccessibilityNodeData,
        bounds: BoundsData,
    ): Boolean = coverage(windows, target, bounds)?.isMostlyHidden == true

    /**
     * The [Coverage] that refuses a [kind] highlight of [target] at [bounds]: only the kinds the user does by touching
     * the target itself ([COVERAGE_CHECKED_KINDS]) are checked, and the target must be mostly hidden. Null when the
     * highlight may be shown.
     */
    fun hiddenCoverage(
        windows: List<WindowData>,
        target: AccessibilityNodeData,
        bounds: BoundsData,
        kind: GuidanceKind,
    ): Coverage? {
        if (kind !in COVERAGE_CHECKED_KINDS) return null
        return coverage(windows, target, bounds)?.takeIf { it.isMostlyHidden }
    }

    /**
     * What is on top at five points of [bounds] (its centre and the points at 25% and 75% of its width and height):
     * a point is clear when [target], one of its descendants or one of its ancestors is on top there. Null when
     * [target] is not in [windows].
     */
    fun coverage(
        windows: List<WindowData>,
        target: AccessibilityNodeData,
        bounds: BoundsData,
    ): Coverage? {
        val targetPath = ElderGuidanceGeometry.pathTo(windows, target.id) ?: return null
        val covers = samplePoints(bounds).mapNotNull { (x, y) -> coverAt(windows, targetPath, x, y) }
        return Coverage(clearPoints = SAMPLE_POINTS.size - covers.size, coveringWindowTypes = covers.toSet())
    }

    /** What makes a node handle a [kind] touch itself; null for the kinds that always keep their node. */
    private fun touchHandler(kind: GuidanceKind): ((AccessibilityNodeData) -> Boolean)? =
        when (kind) {
            GuidanceKind.TAP, GuidanceKind.DOUBLE_TAP -> AccessibilityNodeData::clickable
            GuidanceKind.LONG_PRESS -> AccessibilityNodeData::longClickable
            GuidanceKind.TYPE, GuidanceKind.SCROLL -> null
        }

    /**
     * Null when the point is clear: the node on top there is the last node of [targetPath] or one of its descendants
     * (the target is on the topmost path) or one of its ancestors (the topmost node is on the target's path). Nodes of
     * the same read are compared by identity, so a node of another window never counts. Otherwise the type of the
     * window on top there, or [NO_WINDOW].
     */
    private fun coverAt(
        windows: List<WindowData>,
        targetPath: List<AccessibilityNodeData>,
        x: Float,
        y: Float,
    ): String? {
        val topPath = ElderGuidanceGeometry.topmostPathAt(windows, x, y) ?: return NO_WINDOW
        val target = targetPath.last()
        val top = topPath.last()
        val clear = topPath.any { it === target } || targetPath.any { it === top }
        return if (clear) null else windows.firstOrNull { it.tree === topPath.first() }?.windowType ?: NO_WINDOW
    }

    private fun samplePoints(bounds: BoundsData): List<Pair<Float, Float>> {
        val width = (bounds.right - bounds.left).toFloat()
        val height = (bounds.bottom - bounds.top).toFloat()
        return SAMPLE_POINTS.map { (fx, fy) -> Pair(bounds.left + width * fx, bounds.top + height * fy) }
    }
}
