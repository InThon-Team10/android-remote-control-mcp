package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.guidance.ScreenPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Spotlight geometry")
class SpotlightGeometryTest {
    private val screen = PxSize(1080, 2340)

    private val pill = SwipePillSize(thickness = 120, length = 240, gap = 16)

    private fun PxRect.overlaps(other: PxRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun PxRect.contains(p: ScreenPoint): Boolean = p.x in left until right && p.y in top until bottom

    private fun anchored(
        x: Int,
        y: Int,
        direction: ScrollDirection,
    ) = swipeArrowRect(PxRect(0, 0, 1080, 2340), ScreenPoint(x, y), direction, pill, screen)

    @Test
    fun `the ring grows the hole and stays on screen`() {
        assertEquals(PxRect(380, 640, 700, 1920), outlineRing(PxRect(400, 660, 680, 1900), 20, screen))
        assertEquals(PxRect(0, 0, 1080, 120), outlineRing(PxRect(10, 5, 1075, 100), 20, screen))
    }

    @Test
    fun `outline strips surround the hole and never cover it`() {
        val hole = PxRect(400, 660, 680, 1900)
        val strips = outlineStrips(hole, outlineRing(hole, 20, screen))

        assertEquals(4, strips.size)
        assertTrue(strips.none { it.overlaps(hole) })
        assertEquals(20 * (280 + 40) * 2 + 20 * 1240 * 2, strips.sumOf { it.width * it.height })
    }

    @Test
    fun `strips touching the screen edge are dropped`() {
        val hole = PxRect(0, 0, 500, 500)

        assertEquals(2, outlineStrips(hole, outlineRing(hole, 20, screen)).size)
    }

    @Test
    fun `without a start point a vertical swipe pill is centred on the swipe area`() {
        val area = PxRect(400, 700, 680, 1900)

        assertEquals(PxRect(480, 1180, 600, 1420), swipeArrowRect(area, null, ScrollDirection.UP, pill, screen))
    }

    @Test
    fun `without a start point a horizontal swipe pill lies across the swipe axis`() {
        val area = PxRect(100, 1000, 980, 1300)

        assertEquals(PxRect(420, 1090, 660, 1210), swipeArrowRect(area, null, ScrollDirection.LEFT, pill, screen))
    }

    @Test
    fun `without a start point the swipe pill stays on screen near the edges`() {
        assertEquals(
            PxRect(0, 0, 120, 240),
            swipeArrowRect(PxRect(0, 0, 60, 100), null, ScrollDirection.DOWN, pill, screen),
        )
        assertEquals(
            PxRect(960, 2100, 1080, 2340),
            swipeArrowRect(PxRect(1000, 2300, 1080, 2340), null, ScrollDirection.UP, pill, screen),
        )
    }

    @Test
    fun `a swipe down from the top edge begins its pill the gap below the start point`() {
        assertEquals(PxRect(840, 16, 960, 256), anchored(900, 0, ScrollDirection.DOWN))
    }

    @Test
    fun `a swipe up from 75 percent of the height ends its pill the gap above the start point`() {
        assertEquals(PxRect(480, 1499, 600, 1739), anchored(540, 1755, ScrollDirection.UP))
    }

    @Test
    fun `left and right swipes begin their pill the gap beside the start point`() {
        assertEquals(PxRect(116, 1140, 356, 1260), anchored(100, 1200, ScrollDirection.RIGHT))
        assertEquals(PxRect(744, 1140, 984, 1260), anchored(1000, 1200, ScrollDirection.LEFT))
    }

    @Test
    fun `near the edges the pill is clamped across and shortened along, never moved over the start point`() {
        assertEquals(PxRect(960, 16, 1080, 256), anchored(1075, 0, ScrollDirection.DOWN))
        assertEquals(PxRect(480, 2216, 600, 2340), anchored(540, 2200, ScrollDirection.DOWN))
        assertEquals(PxRect(0, 2220, 130, 2340), anchored(146, 2330, ScrollDirection.LEFT))
    }

    @Test
    fun `no pill is drawn when there is no room past the start point`() {
        assertNull(anchored(540, 2300, ScrollDirection.DOWN))
        assertNull(anchored(540, 50, ScrollDirection.UP))
        assertNull(anchored(1000, 1200, ScrollDirection.RIGHT))
        assertNull(anchored(60, 1200, ScrollDirection.LEFT))
    }

    @Test
    fun `the start point is never inside the pill and the pill stays on screen`() {
        val xs = listOf(0, 1, 60, 540, 1019, 1079)
        val ys = listOf(0, 1, 60, 1170, 1755, 2279, 2339)
        val bounds = PxRect(0, 0, screen.width, screen.height)
        var drawn = 0

        for (x in xs) {
            for (y in ys) {
                ScrollDirection.entries.forEach { direction ->
                    val rect = anchored(x, y, direction) ?: return@forEach
                    drawn++
                    assertFalse(rect.contains(ScreenPoint(x, y)), "$direction from ($x, $y): $rect")
                    assertFalse(rect.left < bounds.left || rect.top < bounds.top, "$direction from ($x, $y): $rect")
                    assertFalse(rect.right > bounds.right || rect.bottom > bounds.bottom, "$direction ($x, $y): $rect")
                }
            }
        }
        assertTrue(drawn > 0)
    }

    @Test
    fun `an anchored pill keeps its full size away from the edges`() {
        assertEquals(PxRect(480, 1186, 600, 1426), anchored(540, 1170, ScrollDirection.DOWN))
    }
}
