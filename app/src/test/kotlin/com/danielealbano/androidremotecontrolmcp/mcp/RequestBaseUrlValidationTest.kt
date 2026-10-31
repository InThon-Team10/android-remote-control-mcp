package com.danielealbano.androidremotecontrolmcp.mcp

import io.ktor.http.RequestConnectionPoint
import io.ktor.http.headersOf
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RequestBaseUrl header validation")
class RequestBaseUrlValidationTest {
    @BeforeEach
    fun setUp() {
        mockkStatic(
            "io.ktor.server.request.ApplicationRequestPropertiesKt",
            "io.ktor.server.plugins.OriginConnectionPointKt",
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(
            "io.ktor.server.request.ApplicationRequestPropertiesKt",
            "io.ktor.server.plugins.OriginConnectionPointKt",
        )
    }

    private fun mockCall(
        forwardedHost: String? = null,
        forwardedProto: String? = null,
        hostHeader: String? = null,
    ): ApplicationCall {
        val pairs = mutableListOf<Pair<String, List<String>>>()
        forwardedHost?.let { pairs += "X-Forwarded-Host" to listOf(it) }
        forwardedProto?.let { pairs += "X-Forwarded-Proto" to listOf(it) }
        hostHeader?.let { pairs += "Host" to listOf(it) }
        val request = mockk<ApplicationRequest>()
        every { request.headers } returns headersOf(*pairs.toTypedArray())
        every { request.host() } returns "listener.local"
        every { request.port() } returns 8080
        val point = mockk<RequestConnectionPoint>()
        every { point.scheme } returns "http"
        every { request.origin } returns point
        val call = mockk<ApplicationCall>()
        every { call.request } returns request
        return call
    }

    private val invalidAuthorities =
        listOf(
            "user:pw@evil.com",
            "user@evil.com",
            "evil.com/x",
            "evil.com/x?y#z",
            "evil.com?y",
            "evil.com#z",
            "evil.com:80/x",
            "evil .com",
            "evil.com\u0009x",
            "evil.com\u0000",
            "evil.com\u007F",
            "evil\u00A0.com",
            "evil.com:abc",
            "evil.com:",
            "evil.com:0",
            "evil.com:65536",
            "evil.com:-1",
            "evil.com:+80",
            ":80",
            "[]",
            "[::1",
            "[::1]x",
            "[::g]",
            "[::1]:",
            "[::1]:99999",
            "a:b:c",
        )

    @Test
    fun `invalid forwarded host falls through to the Host header`() {
        for (bad in invalidAuthorities) {
            val call = mockCall(forwardedHost = bad, hostHeader = "good.example:8080")
            assertEquals("http://good.example:8080", deriveBaseUrl(call), "xfh='$bad'")
        }
    }

    @Test
    fun `invalid Host header falls through to the listener when nothing else is usable`() {
        for (bad in invalidAuthorities) {
            val call = mockCall(hostHeader = bad)
            assertEquals("http://listener.local:8080", deriveBaseUrl(call), "host='$bad'")
        }
    }

    @Test
    fun `invalid forwarded host and invalid Host fall through to the listener`() {
        val call = mockCall(forwardedHost = "a@b", hostHeader = "c/d")
        assertEquals("http://listener.local:8080", deriveBaseUrl(call))
    }

    @Test
    fun `non http or https forwarded proto is ignored and the listener scheme is used`() {
        for (bad in listOf("javascript", "ftp", "http://", "htt ps", "ws", "http:evil", "\u0000")) {
            val call = mockCall(forwardedHost = "h.example", forwardedProto = bad)
            assertEquals("http://h.example", deriveBaseUrl(call), "proto='$bad'")
        }
    }

    @Test
    fun `forwarded proto http and https are accepted case-insensitively`() {
        val https = mockCall(forwardedHost = "h.example", forwardedProto = "HtTpS")
        assertEquals("https://h.example", deriveBaseUrl(https))
        val http = mockCall(forwardedHost = "h.example:8443", forwardedProto = "HTTP")
        assertEquals("http://h.example:8443", deriveBaseUrl(http))
    }

    @Test
    fun `well-formed ipv6 ipv4 and port authorities are preserved`() {
        assertEquals("https://[2001:db8::1]:8443", deriveBaseUrl(mockCall("[2001:DB8::1]:8443", "https")))
        assertEquals("http://[::1]", deriveBaseUrl(mockCall(hostHeader = "[::1]:80")))
        assertEquals("http://192.168.1.5:65535", deriveBaseUrl(mockCall(hostHeader = "192.168.1.5:65535")))
        val tunnel = mockCall("my-tunnel_1.trycloudflare.com", "https")
        assertEquals("https://my-tunnel_1.trycloudflare.com", deriveBaseUrl(tunnel))
    }
}
