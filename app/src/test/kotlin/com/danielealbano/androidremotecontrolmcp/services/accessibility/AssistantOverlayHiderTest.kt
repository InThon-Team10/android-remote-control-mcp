package com.danielealbano.androidremotecontrolmcp.services.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `combine of no hiders is NONE and of one hider is that hider`() {
        val single = RecordingHider("a", mutableListOf())

        assertSame(AssistantOverlayHider.NONE, AssistantOverlayHider.combine(emptyList()))
        assertSame(single, AssistantOverlayHider.combine(listOf(single)))
    }

    @Test
    fun `combine nests the hiders first outermost and returns the block value`() =
        runTest {
            val events = mutableListOf<String>()
            val combined =
                AssistantOverlayHider.combine(listOf(RecordingHider("a", events), RecordingHider("b", events)))

            val result =
                combined.hiddenDuring {
                    events += "block"
                    7
                }

            assertEquals(7, result)
            assertEquals(listOf("a:hide", "b:hide", "block", "b:restore", "a:restore"), events)
        }

    @Test
    fun `combine restores every hider when the block throws`() =
        runTest {
            val events = mutableListOf<String>()
            val combined =
                AssistantOverlayHider.combine(listOf(RecordingHider("a", events), RecordingHider("b", events)))

            assertThrows<IllegalStateException> { combined.hiddenDuring { throw IllegalStateException("x") } }

            assertTrue(events.containsAll(listOf("a:restore", "b:restore")))
        }

    private class RecordingHider(
        private val name: String,
        private val events: MutableList<String>,
    ) : AssistantOverlayHider {
        override suspend fun <T> hiddenDuring(block: suspend () -> T): T {
            events += "$name:hide"
            try {
                return block()
            } finally {
                events += "$name:restore"
            }
        }
    }
}
