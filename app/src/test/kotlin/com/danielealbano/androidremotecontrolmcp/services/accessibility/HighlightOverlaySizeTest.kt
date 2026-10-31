package com.danielealbano.androidremotecontrolmcp.services.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HighlightOverlaySizeTest {
    @Test
    fun `positive extent is returned unchanged`() {
        assertEquals(300, highlightOverlayExtent(100, 400))
    }

    @Test
    fun `inverted bounds never produce a reserved layout size`() {
        // -1 is MATCH_PARENT and -2 is WRAP_CONTENT in WindowManager.LayoutParams
        assertEquals(1, highlightOverlayExtent(11, 10))
        assertEquals(1, highlightOverlayExtent(12, 10))
    }

    @Test
    fun `zero extent is raised to one pixel`() {
        assertEquals(1, highlightOverlayExtent(50, 50))
    }
}
