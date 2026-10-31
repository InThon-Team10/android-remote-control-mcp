package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ElderGuidanceGeometry")
class ElderGuidanceGeometryTest {
    private fun node(
        id: String,
        bounds: BoundsData,
        visible: Boolean = true,
        children: List<AccessibilityNodeData> = emptyList(),
    ) = AccessibilityNodeData(id = id, bounds = bounds, visible = visible, children = children)

    private fun window(
        layer: Int,
        tree: AccessibilityNodeData,
    ) = WindowData(windowId = layer, windowType = "APPLICATION", layer = layer, tree = tree)

    private fun boundsAt(
        root: AccessibilityNodeData,
        x: Float,
        y: Float,
    ) = ElderGuidanceGeometry.nodeBoundsAt(listOf(window(0, root)), x, y)

    @Test
    fun `clamp limits bounds to the screen`() {
        val clamped = ElderGuidanceGeometry.clamp(BoundsData(-20, -5, 1200, 500), 1080, 2400)

        assertEquals(BoundsData(0, 0, 1080, 500), clamped)
    }

    @Test
    fun `clamp returns null when nothing is on screen`() {
        assertNull(ElderGuidanceGeometry.clamp(BoundsData(1100, 10, 1200, 50), 1080, 2400))
        assertNull(ElderGuidanceGeometry.clamp(BoundsData(10, 10, 10, 50), 1080, 2400))
    }

    @Test
    fun `fallback box is 72dp square centred on the point`() {
        val box = ElderGuidanceGeometry.fallbackBox(500f, 800f, 420, 1080, 2400)

        assertEquals(BoundsData(406, 706, 594, 894), box)
    }

    @Test
    fun `fallback box is clamped near the screen edge`() {
        val box = ElderGuidanceGeometry.fallbackBox(10f, 10f, 420, 1080, 2400)

        assertEquals(BoundsData(0, 0, 104, 104), box)
    }

    @Test
    fun `fallback box is null for a point far outside the screen`() {
        assertNull(ElderGuidanceGeometry.fallbackBox(5000f, 10f, 420, 1080, 2400))
    }

    @Test
    fun `nodeBoundsAt returns the deepest visible node containing the point`() {
        val inner = node("inner", BoundsData(100, 100, 200, 200))
        val outer = node("outer", BoundsData(50, 50, 400, 400), children = listOf(inner))
        val root = node("root", BoundsData(0, 0, 1080, 2400), children = listOf(outer))

        assertEquals(BoundsData(100, 100, 200, 200), boundsAt(root, 150f, 150f))
        assertEquals(BoundsData(50, 50, 400, 400), boundsAt(root, 300f, 300f))
    }

    @Test
    fun `nodeBoundsAt skips invisible nodes`() {
        val hidden = node("hidden", BoundsData(100, 100, 200, 200), visible = false)
        val outer = node("outer", BoundsData(50, 50, 400, 400), children = listOf(hidden))
        val root = node("root", BoundsData(0, 0, 1080, 2400), children = listOf(outer))

        assertEquals(BoundsData(50, 50, 400, 400), boundsAt(root, 150f, 150f))
    }

    @Test
    fun `nodeBoundsAt is null when only the window root contains the point`() {
        val root = node("root", BoundsData(0, 0, 1080, 2400))

        assertNull(boundsAt(root, 500f, 500f))
    }

    @Test
    fun `nodeBoundsAt uses the topmost window containing the point`() {
        val belowChild = node("below", BoundsData(0, 0, 500, 500))
        val aboveChild = node("above", BoundsData(0, 0, 300, 300))
        val below = node("b", BoundsData(0, 0, 1080, 2400), children = listOf(belowChild))
        val above = node("a", BoundsData(0, 0, 600, 600), children = listOf(aboveChild))

        val bounds = ElderGuidanceGeometry.nodeBoundsAt(listOf(window(0, below), window(5, above)), 100f, 100f)

        assertEquals(BoundsData(0, 0, 300, 300), bounds)
    }

    @Test
    fun `nodeBoundsAt prefers the later sibling drawn on top`() {
        val first = node("first", BoundsData(0, 0, 300, 300))
        val second = node("second", BoundsData(100, 100, 200, 200))
        val root = node("root", BoundsData(0, 0, 1080, 2400), children = listOf(first, second))

        assertEquals(BoundsData(100, 100, 200, 200), boundsAt(root, 150f, 150f))
    }

    @Test
    fun `topmostPathAt returns the root-to-node path on top and null outside every window`() {
        val inner = node("inner", BoundsData(100, 100, 200, 200))
        val outer = node("outer", BoundsData(50, 50, 400, 400), children = listOf(inner))
        val root = node("root", BoundsData(0, 0, 1080, 2400), children = listOf(outer))
        val windows = listOf(window(0, root))

        val onInner = ElderGuidanceGeometry.topmostPathAt(windows, 150f, 150f)?.map { it.id }
        val onRootOnly = ElderGuidanceGeometry.topmostPathAt(windows, 500f, 500f)?.map { it.id }

        assertEquals(listOf("root", "outer", "inner"), onInner)
        assertEquals(listOf("root"), onRootOnly)
        assertNull(ElderGuidanceGeometry.topmostPathAt(windows, 1500f, 500f))
    }

    @Test
    fun `topmostPathAt skips a window whose touchable bounds do not contain the point`() {
        // A keyboard window whose root reaches above the keys that take its touches
        val field = node("field", BoundsData(0, 1100, 1080, 1300))
        val app = window(0, node("app", BoundsData(0, 0, 1080, 2400), children = listOf(field)))
        val keys = node("keys", BoundsData(0, 1600, 1080, 2400))
        val keyboard =
            window(5, node("ime", BoundsData(0, 1000, 1080, 2400), children = listOf(keys)))
                .copy(touchableBounds = BoundsData(0, 1600, 1080, 2400))
        val windows = listOf(app, keyboard)

        assertEquals(listOf("app", "field"), ElderGuidanceGeometry.topmostPathAt(windows, 500f, 1200f)?.map { it.id })
        assertEquals(listOf("ime", "keys"), ElderGuidanceGeometry.topmostPathAt(windows, 500f, 2000f)?.map { it.id })
    }

    @Test
    fun `topmostPathAt skips a window whose root is not visible`() {
        val child = node("child", BoundsData(0, 0, 9, 9))
        val below = node("below", BoundsData(0, 0, 1080, 2400), children = listOf(child))
        val hidden = node("hidden", BoundsData(0, 0, 1080, 2400), visible = false)
        val windows = listOf(window(0, below), window(5, hidden))

        assertEquals(listOf("below", "child"), ElderGuidanceGeometry.topmostPathAt(windows, 5f, 5f)?.map { it.id })
    }

    @Test
    fun `pathTo returns the root-to-node path in the first window that has the node`() {
        val screen = BoundsData(0, 0, 1080, 2400)
        val small = BoundsData(0, 0, 9, 9)
        val windows =
            listOf(
                window(0, node("root0", screen, children = listOf(node("a", small)))),
                window(1, node("root1", screen, children = listOf(node("b", small)))),
            )

        assertEquals(listOf("root1", "b"), ElderGuidanceGeometry.pathTo(windows, "b")?.map { it.id })
        assertNull(ElderGuidanceGeometry.pathTo(windows, "missing"))
    }

    @Test
    fun `swipe area covers the path plus the margin`() {
        // 48dp at 160 dpi = 48 px
        val area = ElderGuidanceGeometry.swipeArea(540f to 1700f, 540f to 800f, 160, 1080, 2340)

        assertEquals(BoundsData(492, 752, 588, 1748), area)
    }

    @Test
    fun `swipe area is limited to the screen and null when off screen`() {
        assertEquals(
            BoundsData(0, 0, 100, 148),
            ElderGuidanceGeometry.swipeArea(10f to 100f, 52f to 20f, 160, 1080, 2340),
        )
        assertNull(ElderGuidanceGeometry.swipeArea(2000f to 3000f, 2100f to 3100f, 160, 1080, 2340))
    }

    @Test
    fun `swipe direction follows the finger and the larger axis`() {
        assertEquals(ScrollDirection.UP, ElderGuidanceGeometry.swipeDirection(0f, -900f))
        assertEquals(ScrollDirection.DOWN, ElderGuidanceGeometry.swipeDirection(30f, 400f))
        assertEquals(ScrollDirection.LEFT, ElderGuidanceGeometry.swipeDirection(-500f, 100f))
        assertEquals(ScrollDirection.RIGHT, ElderGuidanceGeometry.swipeDirection(500f, -100f))
        assertEquals(ScrollDirection.DOWN, ElderGuidanceGeometry.swipeDirection(200f, 200f))
    }
}
