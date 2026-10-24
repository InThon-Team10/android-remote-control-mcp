package com.danielealbano.androidremotecontrolmcp.ui

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.NetworkRequest
import android.os.Handler
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("NetworkChangeWatcher")
class NetworkChangeWatcherTest {
    private val connectivity = mockk<ConnectivityManager>(relaxed = true)
    private val handler = mockk<Handler>()
    private val request = mockk<NetworkRequest>()
    private var changes = 0
    private val watcher = NetworkChangeWatcher(connectivity, handler, request) { changes++ }

    @Test
    fun `start registers for the request on the handler, and every network change calls back`() {
        val callback = slot<ConnectivityManager.NetworkCallback>()

        watcher.start()

        verify(exactly = 1) { connectivity.registerNetworkCallback(request, capture(callback), handler) }
        callback.captured.onAvailable(mockk())
        callback.captured.onLost(mockk())
        callback.captured.onLinkPropertiesChanged(mockk(), mockk())
        assertEquals(3, changes)
    }

    @Test
    fun `stop unregisters the registered callback`() {
        val registered = slot<ConnectivityManager.NetworkCallback>()
        val unregistered = slot<ConnectivityManager.NetworkCallback>()
        watcher.start()

        watcher.stop()

        verify { connectivity.registerNetworkCallback(request, capture(registered), handler) }
        verify(exactly = 1) { connectivity.unregisterNetworkCallback(capture(unregistered)) }
        assertEquals(registered.captured, unregistered.captured)
    }

    @Test
    fun `start and stop are idempotent`() {
        watcher.stop()
        watcher.start()
        watcher.start()
        watcher.stop()
        watcher.stop()

        verify(exactly = 1) { connectivity.registerNetworkCallback(request, any<NetworkCallback>(), handler) }
        verify(exactly = 1) { connectivity.unregisterNetworkCallback(any<NetworkCallback>()) }
    }
}
