package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

class AssistantMcpEndpointTest {
    @Test
    fun `url uses 127_0_0_1 for LOCALHOST`() {
        assertEquals("http://127.0.0.1:8080/mcp", AssistantMcpEndpoint.url(ServerConfig()))
    }

    @Test
    fun `url uses 127_0_0_1 for NETWORK`() {
        val config = ServerConfig(bindingAddress = BindingAddress.NETWORK)
        assertEquals("http://127.0.0.1:8080/mcp", AssistantMcpEndpoint.url(config))
    }

    @Test
    fun `url uses specific address for SPECIFIC`() {
        val config = ServerConfig(bindingAddress = BindingAddress.SPECIFIC, specificBindAddress = "192.168.1.5")
        assertEquals("http://192.168.1.5:8080/mcp", AssistantMcpEndpoint.url(config))
    }

    @Test
    fun `url brackets IPv6 for SPECIFIC`() {
        val config = ServerConfig(bindingAddress = BindingAddress.SPECIFIC, specificBindAddress = "::1")
        assertEquals("http://[::1]:8080/mcp", AssistantMcpEndpoint.url(config))
    }

    @Test
    fun `url falls back to loopback for blank SPECIFIC`() {
        val config = ServerConfig(bindingAddress = BindingAddress.SPECIFIC, specificBindAddress = "  ")
        assertEquals("http://127.0.0.1:8080/mcp", AssistantMcpEndpoint.url(config))
    }

    @Test
    fun `url uses https only when httpsEnabled`() {
        val config = ServerConfig(httpsEnabled = true, port = 9443)
        assertEquals("https://127.0.0.1:9443/mcp", AssistantMcpEndpoint.url(config))
    }

    @Test
    fun `pinned hostname verifier accepts pinned leaf`() {
        val pinned = mockk<X509Certificate>()
        val session = mockk<SSLSession> { every { peerCertificates } returns arrayOf(pinned) }
        assertTrue(AssistantMcpEndpoint.pinnedHostnameVerifier(pinned).verify("anything", session))
    }

    @Test
    fun `pinned hostname verifier rejects other leaf`() {
        val verifier = AssistantMcpEndpoint.pinnedHostnameVerifier(mockk<X509Certificate>())
        val other = mockk<SSLSession> { every { peerCertificates } returns arrayOf(mockk<X509Certificate>()) }
        val empty = mockk<SSLSession> { every { peerCertificates } returns emptyArray() }
        val throwing = mockk<SSLSession> { every { peerCertificates } throws IllegalStateException("no peer") }
        assertFalse(verifier.verify("h", other))
        assertFalse(verifier.verify("h", empty))
        assertFalse(verifier.verify("h", throwing))
    }
}
