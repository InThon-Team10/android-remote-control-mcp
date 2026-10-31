package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData

/** Screen geometry of the elder-guidance highlight: pure functions, in screen pixels. */
internal object ElderGuidanceGeometry {
    /** Side of the box highlighted around a point that falls on no node. */
    const val FALLBACK_BOX_DP = 72

    private const val BASE_DENSITY_DPI = 160
    private const val HALF = 2

    /** [bounds] limited to the screen of [width] x [height]; null when nothing of it is on screen. */
    fun clamp(
        bounds: BoundsData,
        width: Int,
        height: Int,
    ): BoundsData? {
        val clamped =
            BoundsData(
                left = bounds.left.coerceAtLeast(0),
                top = bounds.top.coerceAtLeast(0),
                right = bounds.right.coerceAtMost(width),
                bottom = bounds.bottom.coerceAtMost(height),
            )
        return clamped.takeIf { it.right > it.left && it.bottom > it.top }
    }

    /** Margin around a swipe path, so the user's finger has room inside the highlighted area. */
    const val SWIPE_MARGIN_DP = 48

    /** The rectangle around the swipe from (x1, y1) to (x2, y2) plus [SWIPE_MARGIN_DP], limited to the screen. */
    fun swipeArea(
        start: Pair<Float, Float>,
        end: Pair<Float, Float>,
        densityDpi: Int,
        width: Int,
        height: Int,
    ): BoundsData? {
        val margin = SWIPE_MARGIN_DP * densityDpi / BASE_DENSITY_DPI
        val area =
            BoundsData(
                left = minOf(start.first, end.first).toInt() - margin,
                top = minOf(start.second, end.second).toInt() - margin,
                right = maxOf(start.first, end.first).toInt() + margin,
                bottom = maxOf(start.second, end.second).toInt() + margin,
            )
        return clamp(area, width, height)
    }

    /** The direction the user's finger moves for a swipe of ([dx], [dy]); the larger axis wins, ties are vertical. */
    fun swipeDirection(
        dx: Float,
        dy: Float,
    ): ScrollDirection =
        when {
            kotlin.math.abs(dy) >= kotlin.math.abs(dx) -> if (dy < 0) ScrollDirection.UP else ScrollDirection.DOWN
            dx < 0 -> ScrollDirection.LEFT
            else -> ScrollDirection.RIGHT
        }

    /** The [FALLBACK_BOX_DP] square centred on the point, at [densityDpi], limited to the screen. */
    fun fallbackBox(
        x: Float,
        y: Float,
        densityDpi: Int,
        width: Int,
        height: Int,
    ): BoundsData? {
        val half = FALLBACK_BOX_DP * densityDpi / BASE_DENSITY_DPI / HALF
        val box = BoundsData(x.toInt() - half, y.toInt() - half, x.toInt() + half, y.toInt() + half)
        return clamp(box, width, height)
    }

    /**
     * Bounds of the deepest visible node that contains the point, in the topmost window that takes the touch there
     * (see [topmostPathAt]). Null when only the window's root does (highlighting a whole window is no guidance).
     */
    fun nodeBoundsAt(
        windows: List<WindowData>,
        x: Float,
        y: Float,
    ): BoundsData? = topmostPathAt(windows, x, y)?.takeIf { it.size > 1 }?.last()?.bounds

    /**
     * Root-to-node path of what the user touches at the point: the deepest visible node that contains it, in the
     * topmost window that takes the touch there (its root is visible and contains the point, and so does its
     * [WindowData.touchableBounds] when known). Null when no window does.
     *
     * Among overlapping siblings the later one is taken as the one on top. That is an approximation: accessibility
     * lists children in reading order, not in drawing order, so a sibling listed later but drawn below (elevation,
     * custom drawing order) is wrongly taken as on top.
     */
    fun topmostPathAt(
        windows: List<WindowData>,
        x: Float,
        y: Float,
    ): List<AccessibilityNodeData>? {
        val window =
            windows.sortedByDescending { it.layer }.firstOrNull { window ->
                window.tree.visible &&
                    contains(window.tree.bounds, x, y) &&
                    window.touchableBounds?.let { contains(it, x, y) } != false
            } ?: return null
        val path =
            generateSequence(window.tree) { node ->
                node.children.lastOrNull { it.visible && contains(it.bounds, x, y) }
            }
        return path.toList()
    }

    /** The nearest scrollable ancestor of the node [nodeId] (the node itself excluded), or null. */
    fun scrollableAncestor(
        windows: List<WindowData>,
        nodeId: String,
    ): AccessibilityNodeData? = pathTo(windows, nodeId)?.dropLast(1)?.lastOrNull { it.scrollable }

    /** Root-to-node path of the node [nodeId] in the first window that has it, or null. */
    fun pathTo(
        windows: List<WindowData>,
        nodeId: String,
    ): List<AccessibilityNodeData>? = windows.firstNotNullOfOrNull { pathTo(it.tree, nodeId) }

    private fun pathTo(
        node: AccessibilityNodeData,
        nodeId: String,
    ): List<AccessibilityNodeData>? =
        if (node.id == nodeId) {
            listOf(node)
        } else {
            node.children.firstNotNullOfOrNull { child -> pathTo(child, nodeId)?.let { listOf(node) + it } }
        }

    private fun contains(
        bounds: BoundsData,
        x: Float,
        y: Float,
    ): Boolean = x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom
}
