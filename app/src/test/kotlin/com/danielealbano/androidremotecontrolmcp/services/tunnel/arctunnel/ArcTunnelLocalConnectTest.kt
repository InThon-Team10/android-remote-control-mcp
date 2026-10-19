package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

@DisplayName("ArcTunnelLocalConnect")
class ArcTunnelLocalConnectTest {
    private val loopback = InetAddress.getLoopbackAddress()

    private fun freePort(): Int = ServerSocket(0, BACKLOG, loopback).use { it.localPort }

    @Test
    fun `connects to a listening port`() =
        runBlocking {
            ServerSocket(0, BACKLOG, loopback).use { server ->
                ArcTunnelLocalConnect.open(server.localPort, WINDOW_MS).use { socket ->
                    assertTrue(socket.isConnected)
                    assertTrue(socket.tcpNoDelay)
                }
            }
        }

    @Test
    fun `retries until the listener appears within the window`() =
        runBlocking {
            val port = freePort()
            val server = ServerSocket()
            try {
                val late =
                    launch(Dispatchers.IO) {
                        delay(LATE_LISTENER_MS)
                        server.reuseAddress = true
                        server.bind(InetSocketAddress(loopback, port), BACKLOG)
                    }
                ArcTunnelLocalConnect.open(port, WINDOW_MS).use { socket -> assertTrue(socket.isConnected) }
                late.join()
            } finally {
                withContext(Dispatchers.IO) { server.close() }
            }
        }

    @Test
    fun `gives up after the window`() {
        val port = freePort()

        assertThrows<ConnectException> { runBlocking { ArcTunnelLocalConnect.open(port, SHORT_WINDOW_MS) } }
    }

    private companion object {
        const val BACKLOG = 50
        const val WINDOW_MS = 5_000L
        const val SHORT_WINDOW_MS = 300L
        const val LATE_LISTENER_MS = 500L
    }
}
