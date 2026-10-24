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
    /** Bind to localhost only. Requires ADB port forwarding for access. */
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
