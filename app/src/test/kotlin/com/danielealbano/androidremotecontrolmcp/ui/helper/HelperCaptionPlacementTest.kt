package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelperCaptionPlacementTest {
    private fun place(
        target: PxRect,
        captionHeight: Int,
        screen: PxSize = PxSize(400, 800),
        captionWidth: Int = 280,
    ) = placeHelperCaption(screen, target, PxSize(captionWidth, captionHeight), 16, 16)

    @Test
    fun `caption goes below the target when there is room`() {
        val result = place(PxRect(160, 80, 220, 140), 100)

        assertTrue(result.below)
        assertEquals(156, result.top)
    }

    @Test
    fun `caption goes above the target near the bottom edge`() {
        val result = place(PxRect(160, 660, 220, 720), 100)

        assertFalse(result.below)
        assertEquals(544, result.top)
    }

    @Test
    fun `oversized caption uses the roomier side and keeps the top visible`() {
        val result = place(PxRect(160, 400, 220, 460), 350)

        assertFalse(result.below)
        assertEquals(34, result.top)
    }

    @Test
    fun `caption stays inside the horizontal margins`() {
        assertEquals(16, place(PxRect(0, 80, 40, 140), 100).left)
        assertEquals(104, place(PxRect(360, 80, 400, 140), 100).left)
    }

    @Test
    fun `overflowing caption is reported as not fitting`() {
        val result = place(PxRect(100, 160, 180, 220), 350, PxSize(320, 400))

        assertFalse(result.fits)
    }

    @Test
    fun `controls go to the bottom unless the top is preferred and free`() {
        val target = PxRect(0, 40, 100, 100)

        assertEquals(464, helperControlsTop(600, target, 120, 16, preferTop = false))
        assertEquals(16, helperControlsTop(600, PxRect(0, 400, 100, 460), 180, 16, preferTop = false))
        assertEquals(16, helperControlsTop(600, PxRect(0, 400, 100, 460), 120, 16, preferTop = true))
    }

    @Test
    fun `controls have no position when neither side has room`() {
        assertNull(helperControlsTop(300, PxRect(0, 100, 100, 160), 180, 16, preferTop = false))
    }
}
