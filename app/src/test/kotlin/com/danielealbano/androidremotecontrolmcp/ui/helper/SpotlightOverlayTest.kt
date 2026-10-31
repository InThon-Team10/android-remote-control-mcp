package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpotlightOverlayTest {
    @Test
    fun `screen edge clipping preserves visible target and rejects unusable detections`() {
        val viewport = Rect(0f, 0f, 100f, 200f)
        assertEquals(Rect(0f, 20f, 40f, 200f), clipHighlight(Rect(-10f, 20f, 40f, 250f), viewport))
        assertNull(clipHighlight(null, viewport))
        assertNull(clipHighlight(Rect(110f, 20f, 150f, 40f), viewport))
        assertNull(clipHighlight(Rect(40f, 20f, 30f, 40f), viewport))
        assertNull(clipHighlight(Rect(Float.NaN, 20f, 40f, 50f), viewport))
        assertNull(clipHighlight(Rect(0f, 20f, Float.POSITIVE_INFINITY, 50f), viewport))
    }
}
