package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Bubble position")
class BubblePositionTest {
    @Test
    fun `the offset stays between the bottom margin and the top reserve`() {
        assertEquals(40, clampBubbleOffset(-500, 2340, 270, 40, 140))
        assertEquals(2340 - 270 - 140, clampBubbleOffset(5000, 2340, 270, 40, 140))
        assertEquals(1000, clampBubbleOffset(1000, 2340, 270, 40, 140))
    }

    @Test
    fun `a tiny screen never inverts the range`() {
        assertEquals(40, clampBubbleOffset(500, 300, 270, 40, 140))
    }

    @Test
    fun `positions round-trip through thousandths of the screen height`() {
        assertEquals(500, bubblePermille(1170, 2340))
        assertEquals(1170, bubbleOffset(500, 2340))
        assertEquals(1000, bubblePermille(9999, 2340))
        assertEquals(0, bubblePermille(10, 0))
        assertEquals(2340, bubbleOffset(1500, 2340))
    }
}
