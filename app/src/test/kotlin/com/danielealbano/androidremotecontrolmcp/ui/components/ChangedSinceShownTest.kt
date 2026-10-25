package com.danielealbano.androidremotecontrolmcp.ui.components

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ChangedSinceShown")
class ChangedSinceShownTest {
    @Test
    fun `the first value shown is not a change, nor showing it again`() {
        val tracker = ChangedSinceShown()

        assertFalse(tracker.update("MCP Server, Running", ready = true))
        assertFalse(tracker.update("MCP Server, Running", ready = true))
    }

    @Test
    fun `a different value is a change, and it stays one even back to the first value`() {
        val tracker = ChangedSinceShown()
        tracker.update("MCP Server, Running", ready = true)

        assertTrue(tracker.update("MCP Server, Stopping…", ready = true))
        assertTrue(tracker.update("MCP Server, Running", ready = true))
    }

    @Test
    fun `values shown before ready are not recorded, the first ready one is`() {
        val tracker = ChangedSinceShown()

        // A placeholder before the setting is read, then the real value
        assertFalse(tracker.update("Event Channel, Stopped", ready = false))
        assertFalse(tracker.update("Event Channel, Active", ready = true))
        assertFalse(tracker.update("Event Channel, Active", ready = true))
        assertTrue(tracker.update("Event Channel, Stopped", ready = true))
    }

    @Test
    fun `a null first value is recorded like any other`() {
        val tracker = ChangedSinceShown()

        assertFalse(tracker.update(null, ready = true))
        assertTrue(tracker.update("You're on the latest version", ready = true))
    }
}
