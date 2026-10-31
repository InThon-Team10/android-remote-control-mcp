package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelperRuntimeTest {
    @AfterEach
    fun reset() {
        HelperRuntime.disconnect()
        HelperRuntime.suppress(false)
    }

    private fun connect() {
        HelperRuntime.connect()
        HelperRuntime.show = { HelperRuntime.visible = true }
        HelperRuntime.hide = { HelperRuntime.visible = false }
    }

    @Test
    fun `practice restores previously visible help only`() {
        connect()
        HelperRuntime.visible = true
        HelperRuntime.suppress(true)
        assertFalse(HelperRuntime.visible)
        HelperRuntime.suppress(true)
        HelperRuntime.suppress(false)
        assertTrue(HelperRuntime.visible)
        HelperRuntime.hide?.invoke()
        HelperRuntime.suppress(true)
        HelperRuntime.suppress(false)
        assertFalse(HelperRuntime.visible)
    }

    @Test
    fun `disconnect during practice never restores a dead service`() {
        connect()
        HelperRuntime.visible = true
        HelperRuntime.suppress(true)
        HelperRuntime.disconnect()
        HelperRuntime.suppress(false)
        assertFalse(HelperRuntime.visible)
        assertFalse(HelperRuntime.connected)
    }

    @Test
    fun `service connecting during practice is restored on exit`() {
        HelperRuntime.suppress(true)
        connect()
        assertFalse(HelperRuntime.visible)
        HelperRuntime.suppress(false)
        assertTrue(HelperRuntime.visible)
    }
}
