package com.danielealbano.androidremotecontrolmcp.ui

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkRequest
import android.os.Handler

/**
 * Calls [onChange] whenever a network of the device appears, goes or changes its addresses, while started. [request]
 * defaults to every network, VPNs included; the callbacks run on [handler]'s thread. [start] and [stop] are
 * idempotent.
 */
class NetworkChangeWatcher(
    private val connectivity: ConnectivityManager,
    private val handler: Handler,
    private val request: NetworkRequest = NetworkRequest.Builder().clearCapabilities().build(),
    private val onChange: () -> Unit,
) {
    private var started = false

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChange()

            override fun onLost(network: Network) = onChange()

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) = onChange()
        }

    fun start() {
        if (started) return
        connectivity.registerNetworkCallback(request, callback, handler)
        started = true
    }

    fun stop() {
        if (!started) return
        connectivity.unregisterNetworkCallback(callback)
        started = false
    }
}
