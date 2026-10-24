package com.danielealbano.androidremotecontrolmcp.mcp

import android.net.InetAddresses
import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import com.danielealbano.androidremotecontrolmcp.utils.NetworkUtils
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject

/**
 * [McpListenerRules] on the device's addresses. [parseNumericAddress] parses an IP literal and NEVER resolves a
 * host name (null for anything else); [deviceAddresses] lists the addresses assigned to the device right now.
 */
class McpListenerRulesImpl internal constructor(
    private val parseNumericAddress: (String) -> InetAddress?,
    private val deviceAddresses: () -> Collection<InetAddress>,
) : McpListenerRules {
    @Inject
    constructor() : this(::parseNumericAddressOrNull, NetworkUtils::deviceAddresses)

    override fun check(config: ServerConfig): ListenerSettingsCheck =
        ListenerSettingsCheck(
            specificAddressError =
                if (config.bindingAddress == BindingAddress.SPECIFIC) specificAddressError(config.bindHost) else null,
            tunnelPortError = tunnelPortError(config),
        )

    override fun tunnelListenerPort(config: ServerConfig): Int? =
        when {
            !forwardsToLocalhost(config) -> null
            !config.httpsEnabled && tunnelPortHeldByMain(config) -> null
            else -> config.tunnelLocalPort
        }

    private fun specificAddressError(text: String): String? {
        // Parsed without its zone index, so that a zone gets its own error whatever the parser makes of it
        val address = if (text.isEmpty()) null else parseNumericAddress(text.substringBefore('%'))
        return when {
            text.isEmpty() -> {
                "Enter the IP address to bind to"
            }

            address == null -> {
                "\"$text\" is not an IPv4 or IPv6 address"
            }

            address is Inet6Address && address.isLinkLocalAddress -> {
                "IPv6 link-local addresses (fe80::/10) cannot be used: binding to one needs an interface name"
            }

            // A zone index means nothing off the device: the URLs shown and shared would carry it
            '%' in text -> {
                "Remove the zone index (%${text.substringAfter('%')}): this address does not need one"
            }

            deviceAddresses().none { it.address.contentEquals(address.address) } -> {
                "$text is not an address of this device"
            }

            else -> {
                null
            }
        }
    }

    // With HTTPS on, the tunnel needs a plain HTTP listener of its own on 127.0.0.1:<tunnel port>, which the main
    // listener already holds when it holds 127.0.0.1 (Localhost, Network or the Specific IP 127.0.0.1) on that port
    private fun tunnelPortError(config: ServerConfig): String? =
        if (forwardsToLocalhost(config) && config.httpsEnabled && tunnelPortHeldByMain(config)) {
            "With HTTPS on, the tunnel needs a plain HTTP listener of its own: " +
                "choose a tunnel port other than the server port (${config.port})"
        } else {
            null
        }

    // Cloudflare and ngrok forward to http://localhost:<tunnel local port>; the free E2E tunnel has its own listener
    private fun forwardsToLocalhost(config: ServerConfig): Boolean =
        config.tunnelEnabled && config.tunnelProvider != TunnelProviderType.ARC_TUNNEL

    // The main listener already holds 127.0.0.1:<tunnel local port>
    private fun tunnelPortHeldByMain(config: ServerConfig): Boolean {
        val samePort = config.tunnelLocalPort == config.port
        return samePort && mainHoldsLoopback(config)
    }

    // Bound to 127.0.0.1, to every address, or to the Specific IP 127.0.0.1 (which behaves as Localhost)
    private fun mainHoldsLoopback(config: ServerConfig): Boolean =
        when (config.bindingAddress) {
            BindingAddress.LOCALHOST, BindingAddress.NETWORK -> true
            BindingAddress.SPECIFIC -> parseNumericAddress(config.bindHost) == IPV4_LOOPBACK
        }

    private companion object {
        val IPV4_LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

        fun parseNumericAddressOrNull(text: String): InetAddress? =
            if (InetAddresses.isNumericAddress(text)) InetAddresses.parseNumericAddress(text) else null
    }
}
