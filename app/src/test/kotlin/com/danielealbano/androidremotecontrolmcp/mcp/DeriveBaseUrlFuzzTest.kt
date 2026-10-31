package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("deriveBaseUrl and effectiveBaseUrl property tests")
class DeriveBaseUrlFuzzTest {
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

    private data class Listener(
        val host: String,
        val port: Int,
        val scheme: String,
    )

    private fun mockCall(
        forwardedHost: String?,
        forwardedProto: String?,
        hostHeader: String?,
        listener: Listener,
    ): ApplicationCall {
        val pairs = mutableListOf<Pair<String, List<String>>>()
        forwardedHost?.let { pairs += "X-Forwarded-Host" to listOf(it) }
        forwardedProto?.let { pairs += "X-Forwarded-Proto" to listOf(it) }
        hostHeader?.let { pairs += "Host" to listOf(it) }
        val request = mockk<ApplicationRequest>()
        every { request.headers } returns headersOf(*pairs.toTypedArray())
        every { request.host() } returns listener.host
        every { request.port() } returns listener.port
        val point = mockk<RequestConnectionPoint>()
        every { point.scheme } returns listener.scheme
        every { request.origin } returns point
        val call = mockk<ApplicationCall>()
        every { call.request } returns request
        return call
    }

    private fun label(rnd: Random): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return String(CharArray(rnd.nextInt(1, 9)) { alphabet[rnd.nextInt(alphabet.length)] })
    }

    private fun host(rnd: Random): String =
        if (rnd.nextInt(5) == 0) {
            "[" + List(rnd.nextInt(2, 8)) { rnd.nextInt(0x10000).toString(16) }.joinToString(":") + "]"
        } else {
            List(rnd.nextInt(1, 4)) { label(rnd) }.joinToString(".")
        }

    private fun hostPort(rnd: Random): String =
        host(rnd) + if (rnd.nextInt(3) == 0) ":" + listOf(80, 443, rnd.nextInt(1, 65536))[rnd.nextInt(3)] else ""

    private fun multi(
        rnd: Random,
        first: String,
    ): String {
        val padded = " ".repeat(rnd.nextInt(0, 3)) + first + " ".repeat(rnd.nextInt(0, 3))
        return if (rnd.nextInt(3) == 0) "$padded, ${hostPort(rnd)}" else padded
    }

    // Independent reference: split the authority by hand, lowercase, drop the scheme's default port
    private fun referenceOrigin(
        scheme: String,
        hostPort: String,
    ): String {
        val lowerScheme = scheme.lowercase()
        val colon = hostPort.indexOf(':')
        val hostEnd =
            when {
                hostPort.startsWith("[") -> hostPort.indexOf(']') + 1
                colon < 0 -> hostPort.length
                else -> colon
            }
        val host = hostPort.substring(0, hostEnd).lowercase()
        val port = hostPort.substring(hostEnd).removePrefix(":").toIntOrNull()
        val default = if (lowerScheme == "https") 443 else 80
        return if (port == null || port == default) "$lowerScheme://$host" else "$lowerScheme://$host:$port"
    }

    @Test
    fun `well-formed forwarded and host headers derive the reference origin with precedence and first-value rules`() {
        val seed = 0xDB0001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val forwardedHost = if (rnd.nextBoolean()) hostPort(rnd) else null
            val hostHeader = if (rnd.nextBoolean()) hostPort(rnd) else null
            val proto = if (rnd.nextBoolean()) listOf("http", "https", "HTTPS")[rnd.nextInt(3)] else null
            val listener = Listener(label(rnd), rnd.nextInt(1, 65536), listOf("http", "https")[rnd.nextInt(2)])
            val forwardedHeader = forwardedHost?.let { multi(rnd, it) }
            val hostValue = hostHeader?.let { multi(rnd, it) }
            val protoHeader = proto?.let { multi(rnd, it) }
            val call = mockCall(forwardedHeader, protoHeader, hostValue, listener)
            val ctx = "seed=$seed iteration=$i xfh='$forwardedHeader' host='$hostValue' proto='$protoHeader'"
            val scheme = proto ?: listener.scheme
            val authority = forwardedHost ?: hostHeader ?: "${listener.host}:${listener.port}"
            assertEquals(referenceOrigin(scheme, authority), deriveBaseUrl(call), ctx)
            assertEquals(deriveBaseUrl(call), effectiveBaseUrl(call, "  "), "$ctx blank override")
        }
    }

    @Test
    fun `arbitrary header text never throws and a non-blank override wins over every header`() {
        val seed = 0xDB0002L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val forwardedHost = if (rnd.nextBoolean()) FuzzStrings.random(rnd, 30) else null
            val hostHeader = if (rnd.nextBoolean()) FuzzStrings.random(rnd, 30) else null
            val proto = if (rnd.nextBoolean()) FuzzStrings.random(rnd, 8) else null
            val override = "https://Pinned.Example:8443/"
            val call = mockCall(forwardedHost, proto, hostHeader, Listener("l", 80, "http"))
            val ctx =
                "seed=$seed iteration=$i xfh='${FuzzStrings.escape(forwardedHost.orEmpty())}' " +
                    "host='${FuzzStrings.escape(hostHeader.orEmpty())}' proto='${FuzzStrings.escape(proto.orEmpty())}'"
            assertNull(runCatching { deriveBaseUrl(call) }.exceptionOrNull(), ctx)
            assertEquals("https://pinned.example:8443", effectiveBaseUrl(call, override), ctx)
        }
    }

    @Test
    fun `arbitrary header text derives either the listener fallback or a clean origin`() {
        val seed = 0xDB0003L
        val rnd = Random(seed)
        val clean = Regex("^https?://(\\[[0-9a-f:]+]|[a-z0-9._~-]+)(:[0-9]{1,5})?$")
        repeat(ITERATIONS) { i ->
            val forwardedHost = if (rnd.nextBoolean()) FuzzStrings.random(rnd, 30) else null
            val hostHeader = if (rnd.nextBoolean()) FuzzStrings.random(rnd, 30) else null
            val proto = if (rnd.nextBoolean()) FuzzStrings.random(rnd, 8) else null
            val listener = Listener("listener.local", 8080, "http")
            val call = mockCall(forwardedHost, proto, hostHeader, listener)
            val ctx =
                "seed=$seed iteration=$i xfh='${FuzzStrings.escape(forwardedHost.orEmpty())}' " +
                    "host='${FuzzStrings.escape(hostHeader.orEmpty())}' proto='${FuzzStrings.escape(proto.orEmpty())}'"
            val result = deriveBaseUrl(call)
            assertTrue(clean.matches(result), "$ctx result='${FuzzStrings.escape(result)}'")
        }
    }

    private companion object {
        const val ITERATIONS = 5000
    }
}
