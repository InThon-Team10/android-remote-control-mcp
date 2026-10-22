package com.danielealbano.androidremotecontrolmcp.services.mcp

import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelRenewals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How a server stop ends [McpServerService]: at once, or, when a tunnel certificate issuance attempt is in
 * flight, by stopping the server now and keeping the service in the foreground until that attempt is saved
 * (abandoning it can lose a certificate the tunnel service already issued). Called on the main thread.
 */
internal class ServerStopSequencer(
    private val renewals: ArcTunnelRenewals,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
) {
    @Volatile
    private var pending: Job? = null

    /**
     * [teardown] stops the server and the tunnel, [showFinishing] updates the notification, [stopService] ends
     * the service; the last one runs on [mainDispatcher].
     */
    fun stop(
        teardown: () -> Unit,
        showFinishing: () -> Unit,
        stopService: () -> Unit,
    ) {
        pending?.cancel()
        if (!renewals.stop()) {
            stopService()
            return
        }
        teardown()
        showFinishing()
        pending =
            scope.launch {
                renewals.awaitIdle()
                withContext(mainDispatcher) { stopService() }
            }
    }

    /** A start while a stop waits for an issuance: the service must keep running afterwards. */
    fun cancelPendingStop() {
        pending?.cancel()
        pending = null
    }
}
