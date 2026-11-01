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

    private val pill = SwipePillSize(thickness = 120, length = 240)

    private fun PxRect.overlaps(other: PxRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun PxRect.contains(p: ScreenPoint): Boolean = p.x in left until right && p.y in top until bottom

    private val metrics =
        SwipePathMetrics(gapHalf = 30, ringOuter = 60, lineHalf = 60, endPad = 20, endInset = 120, minLine = 60)

    private fun path(
        x: Int,
        y: Int,
        direction: ScrollDirection,
        area: PxRect = PxRect(0, 0, 1080, 2340),
    ) = swipePathLayout(area, ScreenPoint(x, y), direction, metrics, screen)

    @Test
    fun `full screen target keeps a caption band and a usable touch area`() {
        val target = PxRect(0, 0, 1080, 2340)
        val hole = requireNotNull(reserveCaptionRoom(target, screen, 650, 160))
        assertTrue(hole.top >= 650 || screen.height - hole.bottom >= 650)
        assertTrue(hole.height >= 160)
        assertTrue(hole.top >= target.top && hole.bottom <= target.bottom)
    }

    @Test
    fun `large target with keyboard and large font still leaves half viewport for caption`() {
        val viewport = PxSize(1080, 1000)
        val hole = requireNotNull(reserveCaptionRoom(PxRect(0, 0, 1080, 1000), viewport, 500, 160))
        assertEquals(PxRect(0, 500, 1080, 1000), hole)
    }

    @Test
    fun `small target with room is unchanged and impossible placement is rejected`() {
        val target = PxRect(100, 900, 900, 1100)
        assertEquals(target, reserveCaptionRoom(target, screen, 650, 160))
        assertNull(reserveCaptionRoom(PxRect(0, 0, 1080, 200), PxSize(1080, 200), 120, 160))
    }

    @Test
    fun `home path leaves bottom centre start free of every overlay strip`() {
        val start = ScreenPoint(540, 2339)
        val end = ScreenPoint(540, 1919)
        val layout = swipePathLayout(PxRect(414, 1793, 666, 2340), start, ScrollDirection.UP, metrics, screen, end)
        assertFalse(requireNotNull(layout.line).contains(start))
        assertTrue(layout.ringStrips.none { it.contains(start) })
        assertTrue(requireNotNull(layout.line).contains(end))
    }

    @Test
    fun `exact diagonal swipe keeps its endpoint in the line window and its start uncovered`() {
        val start = ScreenPoint(540, 1800)
        val end = ScreenPoint(720, 700)
        val layout = swipePathLayout(PxRect(400, 550, 850, 1950), start, ScrollDirection.UP, metrics, screen, end)
        val line = requireNotNull(layout.line)
        assertTrue(end.x >= line.left && end.x < line.right && end.y >= line.top && end.y < line.bottom)
        assertTrue(start.y >= line.bottom)
        assertTrue(layout.ringStrips.none { start.x >= it.left && start.x < it.right && start.y >= it.top && start.y < it.bottom })
    }

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

        assertEquals(PxRect(480, 1180, 600, 1420), swipeArrowRect(area, ScrollDirection.UP, pill, screen))
    }

    @Test
    fun `without a start point a horizontal swipe pill lies across the swipe axis`() {
        val area = PxRect(100, 1000, 980, 1300)

        assertEquals(PxRect(420, 1090, 660, 1210), swipeArrowRect(area, ScrollDirection.LEFT, pill, screen))
    }

    @Test
    fun `without a start point the swipe pill stays on screen near the edges`() {
        assertEquals(
            PxRect(0, 0, 120, 240),
            swipeArrowRect(PxRect(0, 0, 60, 100), ScrollDirection.DOWN, pill, screen),
        )
        assertEquals(
            PxRect(960, 2100, 1080, 2340),
            swipeArrowRect(PxRect(1000, 2300, 1080, 2340), ScrollDirection.UP, pill, screen),
        )
    }

    @Test
    fun `the gap is centred on the start and surrounded by four ring strips`() {
        val layout = path(540, 1200, ScrollDirection.UP)

        assertEquals(PxRect(510, 1170, 570, 1230), layout.gap)
        assertEquals(PxRect(480, 1140, 600, 1260), layout.ring)
        assertEquals(4, layout.ringStrips.size)
        assertTrue(layout.ringStrips.none { it.overlaps(layout.gap) })
    }

    @Test
    fun `an upward line begins at the gap edge and ends short of the area margin`() {
        val layout = path(540, 1800, ScrollDirection.UP, PxRect(0, 600, 1080, 2340))

        assertEquals(PxRect(480, 700, 600, 1770), layout.line)
    }

    @Test
    fun `a downward line begins at the gap edge below the start`() {
        val layout = path(540, 600, ScrollDirection.DOWN, PxRect(0, 500, 1080, 1700))

        assertEquals(PxRect(480, 630, 600, 1600), layout.line)
    }

    @Test
    fun `horizontal lines run beside the start`() {
        val band = PxRect(0, 1000, 1080, 1400)
        assertEquals(PxRect(130, 1140, 980, 1260), path(100, 1200, ScrollDirection.RIGHT, band).line)
        assertEquals(PxRect(100, 1140, 950, 1260), path(980, 1200, ScrollDirection.LEFT, band).line)
    }

    @Test
    fun `a long swipe gives a longer line than a short one`() {
        val long = path(540, 1800, ScrollDirection.UP, PxRect(0, 400, 1080, 2340)).line!!
        val short = path(540, 1800, ScrollDirection.UP, PxRect(0, 1300, 1080, 2340)).line!!

        assertTrue(long.height > short.height)
    }

    @Test
    fun `at the screen edge the ring is clamped and the gap keeps the start pixel`() {
        val corner = path(0, 0, ScrollDirection.DOWN, PxRect(0, 0, 1080, 1200))
        assertEquals(PxRect(0, 0, 30, 30), corner.gap)
        assertEquals(2, corner.ringStrips.size)
        assertTrue(corner.ringStrips.none { it.contains(ScreenPoint(0, 0)) })

        val far = path(1079, 2339, ScrollDirection.UP, PxRect(0, 1000, 1080, 2340))
        assertTrue(far.gap.contains(ScreenPoint(1079, 2339)))
    }

    @Test
    fun `no line is drawn when there is no room past the start`() {
        assertNull(path(540, 2330, ScrollDirection.DOWN).line)
        assertNull(path(540, 20, ScrollDirection.UP).line)
        assertNull(path(1075, 1200, ScrollDirection.RIGHT).line)
        assertNull(path(5, 1200, ScrollDirection.LEFT).line)
    }

    @Test
    fun `no window ever covers the start pixel and all stay on screen`() {
        val xs = listOf(0, 1, 60, 540, 1019, 1079)
        val ys = listOf(0, 1, 60, 1170, 1755, 2279, 2339)
        val bounds = PxRect(0, 0, screen.width, screen.height)
        var lines = 0

        for (x in xs) {
            for (y in ys) {
                ScrollDirection.entries.forEach { direction ->
                    val layout = path(x, y, direction)
                    val start = ScreenPoint(x, y)
                    val all = layout.ringStrips + listOfNotNull(layout.line)
                    if (layout.line != null) lines++
                    assertTrue(layout.gap.contains(start), "$direction from ($x, $y)")
                    all.forEach { rect ->
                        assertFalse(rect.contains(start), "$direction from ($x, $y): $rect")
                        assertFalse(rect.overlaps(layout.gap), "$direction from ($x, $y): $rect")
                        assertTrue(rect.left >= bounds.left && rect.top >= bounds.top, "$rect")
                        assertTrue(rect.right <= bounds.right && rect.bottom <= bounds.bottom, "$rect")
                    }
                }
            }
        }
        assertTrue(lines > 0)
    }

    @org.junit.jupiter.api.Test
    fun `typing viewport excludes docked floating and fullscreen keyboards`() {
        val screen = PxSize(1080, 2340)
        org.junit.jupiter.api.Assertions
            .assertEquals(screen, typingViewport(screen, null))
        for (top in listOf(0, 700, 1500, 2340)) {
            org.junit.jupiter.api.Assertions
                .assertEquals(PxSize(1080, top), typingViewport(screen, top))
        }
    }
}
