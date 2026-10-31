package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ElderGuidanceTouchTarget")
class ElderGuidanceTouchTargetTest {
    private val screenHeight = 2400
    private val fullScreen = BoundsData(0, 0, 1080, 2400)

    /** The Settings row of "연결": a clickable full-width row around a small label. */
    private val rowBounds = BoundsData(28, 1297, 1052, 1443)
    private val labelBounds = BoundsData(200, 1340, 280, 1405)

    private fun node(
        id: String,
        bounds: BoundsData,
        clickable: Boolean = false,
        children: List<AccessibilityNodeData> = emptyList(),
    ) = AccessibilityNodeData(id = id, bounds = bounds, clickable = clickable, visible = true, children = children)

    private fun window(
        layer: Int,
        tree: AccessibilityNodeData,
    ) = WindowData(windowId = layer, windowType = "APPLICATION", layer = layer, tree = tree)

    /** One full-screen window whose root holds [children], later ones drawn on top. */
    private fun screen(vararg children: AccessibilityNodeData) =
        listOf(window(0, node("node_root", fullScreen, children = children.toList())))

    @Nested
    @DisplayName("actionableNode")
    inner class ActionableNode {
        private fun actionable(
            windows: List<WindowData>,
            node: AccessibilityNodeData,
            kind: GuidanceKind,
        ) = ElderGuidanceTouchTarget.actionableNode(windows, node, kind, screenHeight)

        @Test
        fun `a label in a clickable row highlights the row`() {
            val label = node("node_label", labelBounds)
            val row = node("node_row", rowBounds, clickable = true, children = listOf(label))
            val windows = screen(row)

            assertSame(row, actionable(windows, label, GuidanceKind.TAP))
            assertSame(row, actionable(windows, label, GuidanceKind.DOUBLE_TAP))
        }

        @Test
        fun `a clickable node keeps itself`() {
            val switch = node("node_switch", BoundsData(900, 1320, 1020, 1420), clickable = true)
            val row = node("node_row", rowBounds, clickable = true, children = listOf(switch))

            assertSame(switch, actionable(screen(row), switch, GuidanceKind.TAP))
        }

        @Test
        fun `a clickable ancestor taller than a third of the screen is not used`() {
            val label = node("node_label", BoundsData(100, 300, 400, 360))
            val card = node("node_card", BoundsData(0, 200, 1080, 1001), clickable = true, children = listOf(label))

            assertSame(label, actionable(screen(card), label, GuidanceKind.TAP))
        }

        @Test
        fun `a clickable ancestor exactly a third of the screen high is used`() {
            val label = node("node_label", BoundsData(100, 300, 400, 360))
            val card = node("node_card", BoundsData(0, 200, 1080, 1000), clickable = true, children = listOf(label))

            assertSame(card, actionable(screen(card), label, GuidanceKind.TAP))
        }

        @Test
        fun `a node without a clickable ancestor keeps itself`() {
            // The quick settings gear: a button that is not clickable, in a header that is not clickable either
            val gear = node("node_gear", BoundsData(960, 180, 1040, 260))
            val header = node("node_header", BoundsData(0, 150, 1080, 300), children = listOf(gear))

            assertSame(gear, actionable(screen(header), gear, GuidanceKind.TAP))
        }

        @Test
        fun `an invisible clickable ancestor is not used`() {
            val label = node("node_label", labelBounds)
            val row = node("node_row", rowBounds, clickable = true, children = listOf(label)).copy(visible = false)

            assertSame(label, actionable(screen(row), label, GuidanceKind.TAP))
        }

        @Test
        fun `a long press uses the nearest long-clickable ancestor`() {
            val label = node("node_label", labelBounds)
            val row = node("node_row", rowBounds, clickable = true, children = listOf(label))
            val itemBounds = BoundsData(0, 1290, 1080, 1450)
            val item = node("node_item", itemBounds, children = listOf(row)).copy(longClickable = true)
            val windows = screen(item)

            assertSame(item, actionable(windows, label, GuidanceKind.LONG_PRESS))
            assertSame(item, actionable(windows, item, GuidanceKind.LONG_PRESS))
            assertSame(row, actionable(windows, label, GuidanceKind.TAP))
        }

        @Test
        fun `type and scroll steps keep their node`() {
            val label = node("node_label", labelBounds)
            val row = node("node_row", rowBounds, clickable = true, children = listOf(label))
            val windows = screen(row)

            assertSame(label, actionable(windows, label, GuidanceKind.TYPE))
            assertSame(label, actionable(windows, label, GuidanceKind.SCROLL))
        }
    }

    @Nested
    @DisplayName("isMostlyHidden")
    inner class IsMostlyHidden {
        private fun hidden(
            windows: List<WindowData>,
            target: AccessibilityNodeData,
        ) = ElderGuidanceTouchTarget.isMostlyHidden(windows, target, target.bounds)

        @Test
        fun `a later sibling drawn over most of the target hides it`() {
            // Samsung Settings: the floating search bar is drawn over the bottom row; only 2 of 5 points are clear
            val row = node("node_sound", BoundsData(28, 2083, 1052, 2229), clickable = true)
            val searchBar = node("node_search", BoundsData(197, 2124, 883, 2270), clickable = true)

            assertTrue(hidden(screen(row, searchBar), row))
            assertFalse(hidden(screen(searchBar, row), row), "an earlier sibling is drawn below the row")
        }

        @Test
        fun `a higher-layer window drawn over the target hides it`() {
            val field = node("node_field", BoundsData(40, 1520, 1040, 1700))
            val app = window(0, node("node_app", fullScreen, children = listOf(field)))
            val keys = node("node_keys", BoundsData(0, 1600, 1080, 2400))
            val keyboard = window(5, node("node_ime", BoundsData(0, 1500, 1080, 2400), children = listOf(keys)))

            assertTrue(hidden(listOf(app, keyboard), field))
        }

        @Test
        fun `a target covered along one edge with 3 clear points is not hidden`() {
            // The cover takes the two points at 75% of the height; the centre and the two at 25% stay clear
            val target = node("node_target", BoundsData(0, 1000, 1000, 1400), clickable = true)
            val edge = node("node_edge", BoundsData(0, 1250, 1000, 1400))

            assertFalse(hidden(screen(target, edge), target))
        }

        @Test
        fun `points on the target's own descendants are clear`() {
            // The sample points land on the text, on a child of the text and on the switch
            val icon = node("node_icon", BoundsData(28, 1297, 200, 1443))
            val badge = node("node_badge", BoundsData(250, 1320, 320, 1350))
            val text = node("node_text", BoundsData(200, 1297, 780, 1443), children = listOf(badge))
            val switch = node("node_switch", BoundsData(780, 1297, 1052, 1443), clickable = true)
            val row = node("node_row", rowBounds, clickable = true, children = listOf(icon, text, switch))

            assertFalse(hidden(screen(row), row))
        }

        @Test
        fun `points on an ancestor of the target are clear`() {
            // The target overflows its parent: its lower points have the window root on top
            val target = node("node_target", BoundsData(0, 1000, 1080, 1200), clickable = true)
            val parent = node("node_parent", BoundsData(0, 1000, 1080, 1100), children = listOf(target))

            assertFalse(hidden(screen(parent), target))
        }

        @Test
        fun `a target that is not in the read is not reported hidden`() {
            val elsewhere = node("node_elsewhere", rowBounds, clickable = true)

            assertFalse(hidden(screen(node("node_other", rowBounds)), elsewhere))
        }
    }
}
