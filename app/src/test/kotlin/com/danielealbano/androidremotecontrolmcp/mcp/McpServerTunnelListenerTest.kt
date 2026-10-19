package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.mcp.oauth.OAuthServerDeps
import com.danielealbano.androidremotecontrolmcp.services.tunnel.TunnelTlsMaterial
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.Server
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@DisplayName("McpServer tunnel listener")
class McpServerTunnelListenerTest {
    private val held =
        HeldCertificate
            .Builder()
            .ecdsa256()
            .commonName("127.0.0.1")
            .build()
    private val material = TunnelTlsMaterial(held.keyPair.private, listOf(held.certificate))
    private lateinit var server: McpServer

    @BeforeEach
    fun setUp() {
        server =
            McpServer(
                ServerConfig(port = freePort(), bindingAddress = BindingAddress.LOCALHOST),
                null,
                mockk<Server>(relaxed = true),
                mockk(relaxed = true),
                OAuthServerDeps(
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                ),
                RecordingServerLogRepository(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.stop(0, STOP_TIMEOUT_MS)
    }

    @Test
    fun `startTlsListener binds a random loopback port and serves health`() {
        server.start()

        val port = server.startTlsListener(material, null) {}

        assertTrue(port in RANDOM_PORT_RANGE, "port $port")
        assertEquals(200, healthStatus(port))
    }

    @Test
    fun `onRequest runs once per request`() {
        server.start()
        val counter = AtomicInteger()
        val port = server.startTlsListener(material, null) { counter.incrementAndGet() }

        repeat(3) { healthStatus(port) }

        assertEquals(3, counter.get())
    }

    @Test
    fun `requested port is honoured`() {
        server.start()
        val requested = freePort()

        assertEquals(requested, server.startTlsListener(material, requested) {})
        assertEquals(200, healthStatus(requested))
    }

    @Test
    fun `random ports differ across restarts`() {
        server.start()

        val ports =
            List(3) {
                val port = server.startTlsListener(material, null) {}
                server.stopTlsListener()
                port
            }

        assertTrue(ports.toSet().size > 1, "ports $ports")
    }

    @Test
    fun `random port bind failure is retried with another port`() {
        server.start()
        ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK)).use { blocker ->
            val free = freePort()
            val candidates = ArrayDeque(listOf(blocker.localPort, free))
            server.tunnelPortChooser = { candidates.removeFirst() }

            assertEquals(free, server.startTlsListener(material, null) {})
        }
    }

    @Test
    fun `a held fixed port fails after the attempts`() {
        server.start()
        ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK)).use { blocker ->
            val startedAt = System.nanoTime()

            val error = assertThrows<IllegalStateException> { server.startTlsListener(material, blocker.localPort) {} }

            assertTrue(error.cause is IOException, "cause ${error.cause}")
            assertTrue((System.nanoTime() - startedAt) / NANOS_PER_MS < FIXED_PORT_BUDGET_MS)
        }
    }

    @Test
    fun `startTlsListener while running throws`() {
        server.start()
        server.startTlsListener(material, null) {}

        assertThrows<IllegalStateException> { server.startTlsListener(material, null) {} }
    }

    @Test
    fun `startTlsListener without a running server throws`() {
        assertThrows<IllegalStateException> { server.startTlsListener(material, null) {} }
    }

    @Test
    fun `stopTlsListener is idempotent`() {
        server.start()
        server.startTlsListener(material, null) {}

        assertDoesNotThrow {
            server.stopTlsListener()
            server.stopTlsListener()
        }
    }

    @Test
    fun `stop also stops the tunnel listener`() {
        server.start()
        val port = server.startTlsListener(material, null) {}

        server.stop(0, STOP_TIMEOUT_MS)

        assertFalse(accepts(port))
    }

    private fun healthStatus(port: Int): Int {
        val connection = URI("https://127.0.0.1:$port/health").toURL().openConnection() as HttpsURLConnection
        connection.sslSocketFactory = trustAllContext().socketFactory
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun accepts(port: Int): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress(LOOPBACK, port), TIMEOUT_MS) }
            true
        } catch (_: IOException) {
            false
        }

    private fun trustAllContext(): SSLContext {
        val trustAll =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), null) }
    }

    private fun freePort(): Int = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val BACKLOG = 50
        const val TIMEOUT_MS = 5_000
        const val STOP_TIMEOUT_MS = 1_000L
        const val FIXED_PORT_BUDGET_MS = 2_000L
        const val NANOS_PER_MS = 1_000_000L
        val RANDOM_PORT_RANGE = 49152..65535
    }
}
