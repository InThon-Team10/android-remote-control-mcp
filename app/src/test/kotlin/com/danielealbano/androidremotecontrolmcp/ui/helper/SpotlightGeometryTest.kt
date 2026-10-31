package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Spotlight geometry")
class SpotlightGeometryTest {
    private val screen = PxSize(1080, 2340)

    private fun PxRect.overlaps(other: PxRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

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
    fun `a vertical swipe pill is centred on the swipe area`() {
        val area = PxRect(400, 700, 680, 1900)

        assertEquals(PxRect(480, 1180, 600, 1420), swipeArrowRect(area, ScrollDirection.UP, 120, 240, screen))
    }

    @Test
    fun `a horizontal swipe pill lies across the swipe axis`() {
        val area = PxRect(100, 1000, 980, 1300)

        assertEquals(PxRect(420, 1090, 660, 1210), swipeArrowRect(area, ScrollDirection.LEFT, 120, 240, screen))
    }

    @Test
    fun `the swipe pill stays on screen near the edges`() {
        assertEquals(
            PxRect(0, 0, 120, 240),
            swipeArrowRect(PxRect(0, 0, 60, 100), ScrollDirection.DOWN, 120, 240, screen),
        )
        assertEquals(
            PxRect(960, 2100, 1080, 2340),
            swipeArrowRect(PxRect(1000, 2300, 1080, 2340), ScrollDirection.UP, 120, 240, screen),
        )
    }
}
