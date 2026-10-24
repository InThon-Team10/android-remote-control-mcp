package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.mcp.ListenerSettingsCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TunnelSettingsScreen")
class TunnelSettingsScreenTest {
    private val conflict = ListenerSettingsCheck(tunnelPortError = "choose another port")

    @Test
    fun `the tunnel forwards to the tunnel local port, not the server port, in ASCII digits whatever the locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            // A locale with native digits, which a %d format would use
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))

            assertEquals("http://localhost:61200", tunnelForwardUrl(ServerConfig(port = 8080, tunnelLocalPort = 61200)))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `the tunnel port field shows the input's own error first`() {
        assertEquals("Port must be a number", tunnelLocalPortFieldError("Port must be a number", conflict))
    }

    @Test
    fun `without an input error the field shows the listener check's`() {
        assertEquals("choose another port", tunnelLocalPortFieldError(null, conflict))
        assertNull(tunnelLocalPortFieldError(null, ListenerSettingsCheck()))
    }
}
