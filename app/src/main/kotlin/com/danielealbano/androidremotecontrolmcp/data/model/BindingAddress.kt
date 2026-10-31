package com.danielealbano.androidremotecontrolmcp.data.model

/**
 * Represents the network binding address for the MCP server.
 *
 * @property address The IP address string to bind to; empty for [SPECIFIC], whose address is
 *   [ServerConfig.specificBindAddress].
 */
enum class BindingAddress(
    val address: String,
) {
    /**
     * Bind to localhost only: reachable from apps on this device, and from a computer through ADB port
     * forwarding.
     *
     * Every app holding `INTERNET` can connect, and the server cannot tell which app did: apps cannot read
     * `/proc/net` since Android 10, and `ConnectivityManager.getConnectionOwnerUid()` works only for the app
     * running the active `VpnService`. Authentication on `/mcp` (bearer token or OAuth) is therefore the only
     * gate against other apps; with both disabled, any of them can call every tool.
     *
     * Loopback TCP is kept as the on-device transport by design: a Unix domain socket would reject the
     * intended client apps too. This app targets API 34, so `seapp_contexts` gives it an MLS level of its own
     * (`levelFrom=all`), and SELinux denies `unix_stream_socket` `connectto` between different levels.
     */
    LOCALHOST("127.0.0.1"),

    /** Bind to all network interfaces. Accessible on the local network. */
    NETWORK("0.0.0.0"),

    /** Bind to one address of the device, [ServerConfig.specificBindAddress]. */
    SPECIFIC(""),
    ;

    companion object {
        /**
         * Returns the fixed [BindingAddress] matching the given [address] string, or [LOCALHOST] if not found
         * ([SPECIFIC] has no fixed address and never matches).
         */
        fun fromAddress(address: String): BindingAddress {
            val fixed = entries.filter { it != SPECIFIC }
            return fixed.firstOrNull { it.address == address } ?: LOCALHOST
        }
    }
}
