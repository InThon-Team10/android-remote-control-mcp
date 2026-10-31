package com.danielealbano.androidremotecontrolmcp.services.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("AssistantOverlayHider")
class AssistantOverlayHiderTest {
    @AfterEach
    fun tearDown() {
        McpAccessibilityService.assistantOverlayHider = AssistantOverlayHider.NONE
    }

    @Test
    fun `NONE runs the block and returns its value`() =
        runTest {
            assertEquals(42, AssistantOverlayHider.NONE.hiddenDuring { 42 })
        }

    @Test
    fun `NONE propagates exceptions`() =
        runTest {
            assertThrows<IllegalStateException> {
                AssistantOverlayHider.NONE.hiddenDuring { throw IllegalStateException("x") }
            }
        }

    @Test
    fun `service companion defaults to NONE`() {
        assertSame(AssistantOverlayHider.NONE, McpAccessibilityService.assistantOverlayHider)
    }
}
