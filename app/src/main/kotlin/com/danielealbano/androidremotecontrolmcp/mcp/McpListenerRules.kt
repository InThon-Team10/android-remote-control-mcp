package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig

/**
 * What keeps the configured listeners from starting, setting by setting; both null when the server can start.
 *
 * @property specificAddressError The problem with the Specific IP address, or null.
 * @property tunnelPortError The problem with the Cloudflare/ngrok tunnel local port, or null.
 */
data class ListenerSettingsCheck(
    val specificAddressError: String? = null,
    val tunnelPortError: String? = null,
) {
    /** The error that keeps the server from starting, or null when it can start. */
    val startError: String?
        get() = specificAddressError ?: tunnelPortError
}

/**
 * The rules of the server's listeners. The main listener always runs, on the binding address and port of the
 * settings, serving HTTP or HTTPS. A Cloudflare or ngrok tunnel forwards to `http://localhost:<tunnel local port>`:
 * the main listener serves it when it already holds 127.0.0.1 on that port with plain HTTP, otherwise a plain HTTP
 * listener of its own on 127.0.0.1 does, serving the same application. The free E2E tunnel has its own listener.
 */
interface McpListenerRules {
    /** Checks the listener settings of [config] against the addresses the device has right now. */
    fun check(config: ServerConfig): ListenerSettingsCheck

    /**
     * The port of the plain HTTP loopback listener the Cloudflare or ngrok tunnel of [config] needs, or null when no
     * such tunnel runs or the main listener serves it. Meant for a [config] that [check] accepts.
     */
    fun tunnelListenerPort(config: ServerConfig): Int?
}
