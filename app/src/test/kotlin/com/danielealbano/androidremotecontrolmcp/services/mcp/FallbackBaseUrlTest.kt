package com.danielealbano.androidremotecontrolmcp.services.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("fallbackBaseUrl")
class FallbackBaseUrlTest {
    private fun specific(ip: String) = ServerConfig(bindingAddress = BindingAddress.SPECIFIC, specificBindAddress = ip)

    @Test
    fun `a specific IP is the host, an IPv6 one in brackets`() {
        assertEquals("http://192.168.1.20:8080", fallbackBaseUrl(specific(" 192.168.1.20 "), "10.0.0.9"))
        assertEquals(
            "https://[2001:db8::20]:8443",
            fallbackBaseUrl(specific("2001:db8::20").copy(httpsEnabled = true, port = 8443), "10.0.0.9"),
        )
    }

    @Test
    fun `otherwise the device's address, or the binding address without one`() {
        val network = ServerConfig(bindingAddress = BindingAddress.NETWORK)

        assertEquals("http://10.0.0.9:8080", fallbackBaseUrl(network, "10.0.0.9"))
        assertEquals("http://0.0.0.0:8080", fallbackBaseUrl(network, null))
        assertEquals("http://127.0.0.1:8080", fallbackBaseUrl(null, null))
    }
}
