package com.danielealbano.androidremotecontrolmcp.e2e

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * E2E test: the server's listeners on the device. A Specific IP is the only address the server listens on; one
 * the device does not have, or one with a zone index, fails the start with a clear error; a Cloudflare or ngrok
 * tunnel gets a plain HTTP listener of its own on 127.0.0.1:<tunnel local port>, serving the same application with
 * the same authentication.
 *
 * Redroid's Android shares the container's network namespace, so the container port mapping reaches its eth0.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class E2EListenersTest {
    private val baseUrl = SharedAndroidContainer.mcpServerUrl

    @AfterAll
    fun restoreDefaults() {
        // Back to every address, no tunnel and the server port as tunnel port, with a fresh shared client for the
        // classes that follow (the specific address stays saved, unused while bound to every address)
        AndroidContainerSetup.stopMcpServer(baseUrl)
        configure(
            "--es",
            "binding_address",
            "0.0.0.0",
            "--ez",
            "tunnel_enabled",
            "false",
            "--es",
            "tunnel_provider",
            "android-remote-control-mcp-tunneld",
            "--ei",
            "tunnel_local_port",
            MCP_PORT.toString(),
        )
        StorageE2E.restartServerAndRefreshClient()
    }

    @Test
    @Order(1)
    fun `a specific IP of the device is the only address the server listens on`() {
        val ip = deviceIpv4()

        restartWith("--es", "binding_address", ip)

        // Reachable at the container's address...
        AndroidContainerSetup.waitForServerReady(baseUrl)
        assertEquals("Running", AndroidContainerSetup.queryArcTunnel().server)
        // ...and not on the device's loopback, where a server on every address would answer too
        withForward(MCP_PORT) { hostPort -> assertNull(httpStatus("http://127.0.0.1:$hostPort/health")) }
    }

    @Test
    @Order(2)
    fun `a specific IP the device does not have fails the start with a clear error`() {
        restartWith("--es", "binding_address", ABSENT_IP)

        val status =
            AndroidContainerSetup.awaitArcTunnel(timeoutMs = STATUS_TIMEOUT_MS) { it.server.startsWith("Error") }

        assertEquals("Error: $ABSENT_IP is not an address of this device", status.server)
    }

    @Test
    @Order(3)
    fun `a zone index fails the start, whatever the address`() {
        // Android parses a global IPv6 literal with a numeric zone index; the URLs would carry it
        restartWith("--es", "binding_address", ZONED_IP)

        val status =
            AndroidContainerSetup.awaitArcTunnel(timeoutMs = STATUS_TIMEOUT_MS) { it.server.startsWith("Error") }

        assertEquals("Error: Remove the zone index (%3): this address does not need one", status.server)
    }

    @Test
    @Order(4)
    fun `a failed start can be retried at once, without a stop`() {
        // Still failed from the previous test; the address fixed, Start again
        configure("--es", "binding_address", "0.0.0.0")
        AndroidContainerSetup.startMcpServer()

        AndroidContainerSetup.waitForServerReady(baseUrl)
        assertEquals("Running", AndroidContainerSetup.queryArcTunnel().server)
    }

    @Test
    @Order(5)
    fun `a Cloudflare or ngrok tunnel gets a localhost listener of its own, with the same authentication`() {
        // ngrok without an authtoken fails on its own: the server, and the listener, keep running
        restartWith(
            "--es",
            "binding_address",
            "0.0.0.0",
            "--ez",
            "tunnel_enabled",
            "true",
            "--es",
            "tunnel_provider",
            "NGROK",
            "--ei",
            "tunnel_local_port",
            TUNNEL_PORT.toString(),
        )
        AndroidContainerSetup.waitForServerReady(baseUrl)

        withForward(TUNNEL_PORT) { hostPort ->
            val url = "http://127.0.0.1:$hostPort"
            assertEquals(HTTP_OK, httpStatus("$url/health"))
            assertEquals(HTTP_UNAUTHORIZED, httpStatus("$url/mcp", ping = true))
            assertEquals(HTTP_OK, httpStatus("$url/mcp", ping = true, token = AndroidContainerSetup.E2E_BEARER_TOKEN))
        }
    }

    private fun restartWith(vararg extras: String) {
        AndroidContainerSetup.stopMcpServer(baseUrl)
        configure(*extras)
        AndroidContainerSetup.startMcpServer()
    }

    private fun configure(vararg extras: String) {
        AndroidContainerSetup.execAdb(
            "shell",
            "am",
            "broadcast",
            "-a",
            AndroidContainerSetup.E2E_CONFIGURE_ACTION,
            "-n",
            AndroidContainerSetup.E2E_RECEIVER_COMPONENT,
            *extras,
        )
        Thread.sleep(CONFIGURE_WAIT_MS)
    }

    /** The device's IPv4 address on eth0, the container's interface. */
    private fun deviceIpv4(): String {
        val out = AndroidContainerSetup.execAdb("shell", "ip", "-4", "-o", "addr", "show", "eth0")
        return requireNotNull(Regex("""inet (\d+\.\d+\.\d+\.\d+)/""").find(out)) { "no IPv4 on eth0: $out" }.groupValues[1]
    }

    /** Runs [block] with a host port forwarded to [devicePort] on the device's loopback. */
    private fun withForward(
        devicePort: Int,
        block: (hostPort: Int) -> Unit,
    ) {
        val hostPort = AndroidContainerSetup.execAdb("forward", "tcp:0", "tcp:$devicePort").trim().toInt()
        try {
            block(hostPort)
        } finally {
            AndroidContainerSetup.execAdb("forward", "--remove", "tcp:$hostPort")
        }
    }

    /** The HTTP status of a GET of [url] (a JSON-RPC ping POST with [ping]), or null when nothing answers there. */
    private fun httpStatus(
        url: String,
        ping: Boolean = false,
        token: String? = null,
    ): Int? {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (ping) {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json, text/event-stream")
                conn.doOutput = true
                conn.outputStream.use { it.write("""{"jsonrpc":"2.0","method":"ping","id":1}""".toByteArray()) }
            }
            conn.responseCode
        } catch (_: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        /** The port [AndroidContainerSetup] configures the server on. */
        const val MCP_PORT = 8080

        // Above the ephemeral ports, which a connection could be holding
        const val TUNNEL_PORT = 61200

        /** TEST-NET-2: never an address of the device. */
        const val ABSENT_IP = "198.51.100.7"

        /** A documentation-range IPv6 address with a numeric zone index. */
        const val ZONED_IP = "2001:db8::7%3"
        const val STATUS_TIMEOUT_MS = 30_000L
        const val CONFIGURE_WAIT_MS = 3_000L
        const val HTTP_TIMEOUT_MS = 5_000
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
    }
}
