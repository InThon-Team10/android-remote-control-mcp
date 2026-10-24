package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.mcp.oauth.OAuthServerDeps
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import com.danielealbano.androidremotecontrolmcp.utils.NetworkUtils
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.Server
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyStore

/** The main listener and the plain HTTP loopback listener a Cloudflare or ngrok tunnel forwards to. */
@DisplayName("McpServer listeners")
class McpServerListenersTest {
    private val held =
        HeldCertificate
            .Builder()
            .ecdsa256()
            .addSubjectAlternativeName(LOOPBACK)
            .build()

    // Both taken at once, so they differ
    private val ports = freePorts(2)
    private val mainPort = ports[0]
    private val tunnelPort = ports[1]
    private var server: McpServer? = null
    private val client = OkHttpClient()

    @AfterEach
    fun tearDown() {
        server?.stop(0, STOP_TIMEOUT_MS)
    }

    private fun start(
        localhostTunnelPort: Int?,
        https: Boolean = false,
    ): McpServer = newServer(localhostTunnelPort, https).also { it.start() }

    private fun newServer(
        localhostTunnelPort: Int?,
        https: Boolean = false,
        specificAddress: String? = null,
    ): McpServer {
        val config =
            ServerConfig(
                port = mainPort,
                bindingAddress = if (specificAddress == null) BindingAddress.LOCALHOST else BindingAddress.SPECIFIC,
                specificBindAddress = specificAddress.orEmpty(),
                bearerToken = TOKEN,
                bearerTokenEnabled = true,
                oauthEnabled = false,
                httpsEnabled = https,
            )
        return McpServer(
            config,
            if (https) httpsMaterial() else null,
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
            InFlightToolCalls(),
            localhostTunnelPort,
        ).also { server = it }
    }

    @Test
    fun `the tunnel listener serves the same application with the same authentication`() {
        start(localhostTunnelPort = tunnelPort)

        for (port in listOf(mainPort, tunnelPort)) {
            assertEquals(HTTP_OK, status(client, "http://$LOOPBACK:$port/health"), "port $port")
            assertEquals(HTTP_UNAUTHORIZED, status(client, "http://$LOOPBACK:$port/mcp"), "port $port")
            // Authenticated, the request reaches the MCP route, which serves POST only
            assertEquals(
                HTTP_METHOD_NOT_ALLOWED,
                status(client, "http://$LOOPBACK:$port/mcp", "Bearer $TOKEN"),
                "port $port",
            )
        }
    }

    @Test
    fun `an HTTPS main listener and a plain HTTP tunnel listener run together`() {
        start(localhostTunnelPort = tunnelPort, https = true)
        val trusting = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        val builder = client.newBuilder().sslSocketFactory(trusting.sslSocketFactory(), trusting.trustManager)
        val httpsClient = builder.build()

        assertEquals(HTTP_OK, status(httpsClient, "https://$LOOPBACK:$mainPort/health"))
        assertEquals(HTTP_OK, status(client, "http://$LOOPBACK:$tunnelPort/health"))
        assertEquals(HTTP_UNAUTHORIZED, status(client, "http://$LOOPBACK:$tunnelPort/mcp"))
    }

    @Test
    fun `a main listener on another address and the tunnel listener share the port`() {
        // The IPv6 loopback: another address of the loopback interface, on every platform
        newServer(localhostTunnelPort = mainPort, specificAddress = IPV6_LOOPBACK).start()

        assertEquals(HTTP_OK, status(client, "http://[$IPV6_LOOPBACK]:$mainPort/health"))
        assertEquals(HTTP_OK, status(client, "http://$LOOPBACK:$mainPort/health"))
    }

    @Test
    fun `without a tunnel listener only the main port listens`() {
        start(localhostTunnelPort = null)

        assertEquals(HTTP_OK, status(client, "http://$LOOPBACK:$mainPort/health"))
        assertTrue(NetworkUtils.isPortAvailable(tunnelPort, LOOPBACK))
    }

    @Test
    fun `a taken tunnel port fails the start naming it, before anything listens`() {
        ServerSocket(tunnelPort, BACKLOG, InetAddress.getByName(LOOPBACK)).use {
            val error = assertThrows<IllegalStateException> { start(localhostTunnelPort = tunnelPort) }

            assertTrue(error.message!!.startsWith("Cannot listen on $LOOPBACK:$tunnelPort: "), error.message)
            assertFalse(server!!.isRunning())
            assertTrue(NetworkUtils.isPortAvailable(mainPort, LOOPBACK))
        }
    }

    @Test
    fun `a tunnel port taken once the main listener started fails the start and stops the main listener`() {
        val taken = mutableListOf<ServerSocket>()
        val mcpServer = newServer(localhostTunnelPort = tunnelPort)
        // Taken between the bind probes and the start of the listeners
        mcpServer.afterBindProbes = { taken += ServerSocket(tunnelPort, BACKLOG, InetAddress.getByName(LOOPBACK)) }

        try {
            val error = assertThrows<IllegalStateException> { mcpServer.start() }

            assertTrue(error.message!!.startsWith("Cannot listen on $LOOPBACK:$tunnelPort: "), error.message)
            assertFalse(mcpServer.isRunning())
            assertTrue(NetworkUtils.isPortAvailable(mainPort, LOOPBACK))
        } finally {
            taken.forEach { it.close() }
        }
    }

    @Test
    fun `a main port taken between the probe and the bind fails the start naming it`() {
        val taken = mutableListOf<ServerSocket>()
        val mcpServer = newServer(localhostTunnelPort = tunnelPort)
        mcpServer.afterBindProbes = { taken += ServerSocket(mainPort, BACKLOG, InetAddress.getByName(LOOPBACK)) }

        try {
            val error = assertThrows<IllegalStateException> { mcpServer.start() }

            assertTrue(error.message!!.startsWith("Cannot listen on $LOOPBACK:$mainPort: "), error.message)
            assertFalse(mcpServer.isRunning())
            assertTrue(NetworkUtils.isPortAvailable(tunnelPort, LOOPBACK))
        } finally {
            taken.forEach { it.close() }
        }
    }

    @Test
    fun `a taken main port fails the start naming it`() {
        ServerSocket(mainPort, BACKLOG, InetAddress.getByName(LOOPBACK)).use {
            val error = assertThrows<IllegalStateException> { start(localhostTunnelPort = tunnelPort) }

            assertTrue(error.message!!.startsWith("Cannot listen on $LOOPBACK:$mainPort: "), error.message)
            assertTrue(NetworkUtils.isPortAvailable(tunnelPort, LOOPBACK))
        }
    }

    @Test
    fun `stop closes the tunnel listener too`() {
        start(localhostTunnelPort = tunnelPort).stop(0, STOP_TIMEOUT_MS)

        assertTrue(NetworkUtils.isPortAvailable(mainPort, LOOPBACK))
        assertTrue(NetworkUtils.isPortAvailable(tunnelPort, LOOPBACK))
    }

    private fun status(
        httpClient: OkHttpClient,
        url: String,
        authorization: String? = null,
    ): Int {
        val request =
            Request
                .Builder()
                .url(url)
                .apply { authorization?.let { header("Authorization", it) } }
                .build()
        return httpClient.newCall(request).execute().use { it.code }
    }

    private fun httpsMaterial(): HttpsMaterial {
        val password = PASSWORD.toCharArray()
        val keyStore =
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(CertificateManager.KEY_ALIAS, held.keyPair.private, password, arrayOf(held.certificate))
            }
        return HttpsMaterial(keyStore, password)
    }

    private fun freePorts(count: Int): List<Int> {
        val sockets = List(count) { ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK)) }
        return try {
            sockets.map { it.localPort }
        } finally {
            sockets.forEach { it.close() }
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val IPV6_LOOPBACK = "::1"
        const val BACKLOG = 50
        const val STOP_TIMEOUT_MS = 1_000L
        const val TOKEN = "listener-test-token"
        const val PASSWORD = "listener-test"
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_METHOD_NOT_ALLOWED = 405
    }
}
