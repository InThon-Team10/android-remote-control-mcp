package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelperCaptionPlacementTest {
    @Test
    fun positionsCaptionUsingMeasuredHeightAndScreenEdges() {
        val below = placeHelperCaption(400, 800, 160, 80, 220, 140, 280, 100, 16, 16)
        assertTrue(below.below)
        assertEquals(156, below.top)
        val above = placeHelperCaption(400, 800, 160, 660, 220, 720, 280, 100, 16, 16)
        assertFalse(above.below)
        assertEquals(544, above.top)
        val largeText = placeHelperCaption(400, 800, 160, 400, 220, 460, 280, 350, 16, 16)
        assertFalse(largeText.below)
        assertEquals(34, largeText.top)
        assertEquals(16, placeHelperCaption(400, 800, 0, 80, 40, 140, 280, 100, 16, 16).left)
        assertEquals(104, placeHelperCaption(400, 800, 360, 80, 400, 140, 280, 100, 16, 16).left)
    }
}
