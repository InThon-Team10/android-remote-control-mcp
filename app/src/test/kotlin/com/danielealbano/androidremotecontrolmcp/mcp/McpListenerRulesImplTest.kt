package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.InetAddress

@DisplayName("McpListenerRulesImpl")
class McpListenerRulesImplTest {
    // Literals parsed from fixed bytes: the parser under test never resolves a name
    private val literals =
        mapOf(
            "127.0.0.1" to bytes(127, 0, 0, 1),
            "192.168.1.20" to bytes(192, 168, 1, 20),
            "10.0.0.9" to bytes(10, 0, 0, 9),
            "2001:db8::20" to bytes(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x20),
            "fe80::1" to bytes(0xfe, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        ).mapValues { InetAddress.getByAddress(it.value) }
    private val deviceAddresses =
        listOf(literals.getValue("127.0.0.1"), literals.getValue("192.168.1.20"), literals.getValue("2001:db8::20"))
    private val rules = McpListenerRulesImpl({ literals[it] }, { deviceAddresses })

    private fun specific(ip: String) = ServerConfig(bindingAddress = BindingAddress.SPECIFIC, specificBindAddress = ip)

    private fun tunnel(
        binding: BindingAddress = BindingAddress.LOCALHOST,
        specificAddress: String = "",
        https: Boolean = false,
        tunnelPort: Int = ServerConfig.DEFAULT_PORT,
        provider: TunnelProviderType = TunnelProviderType.CLOUDFLARE,
    ) = ServerConfig(
        bindingAddress = binding,
        specificBindAddress = specificAddress,
        httpsEnabled = https,
        tunnelEnabled = true,
        tunnelProvider = provider,
        tunnelLocalPort = tunnelPort,
    )

    @Nested
    @DisplayName("Specific IP")
    inner class SpecificIp {
        @Test
        fun `Localhost and All networks need no address`() {
            assertNull(rules.check(ServerConfig(bindingAddress = BindingAddress.LOCALHOST)).startError)
            assertNull(rules.check(ServerConfig(bindingAddress = BindingAddress.NETWORK)).startError)
        }

        @Test
        fun `an address of the device, IPv4 or IPv6, is accepted, surrounding spaces ignored`() {
            assertNull(rules.check(specific("192.168.1.20")).specificAddressError)
            assertNull(rules.check(specific("2001:db8::20")).specificAddressError)
            assertNull(rules.check(specific("  192.168.1.20 ")).specificAddressError)
        }

        @Test
        fun `an empty address is refused`() {
            assertEquals("Enter the IP address to bind to", rules.check(specific("  ")).startError)
        }

        @Test
        fun `anything but an IP literal is refused`() {
            val error = rules.check(specific("phone.local")).startError

            assertEquals("\"phone.local\" is not an IPv4 or IPv6 address", error)
        }

        @Test
        fun `an IPv6 link-local address is refused with the reason`() {
            assertEquals(
                "IPv6 link-local addresses (fe80::/10) cannot be used: binding to one needs an interface name",
                rules.check(specific("fe80::1")).startError,
            )
        }

        @Test
        fun `a zone index is refused, numeric or named, on IPv6 or IPv4, even on an address of the device`() {
            // The parser only ever sees the address: it knows no zoned literal
            mapOf("2001:db8::20%3" to "%3", "2001:db8::20%wlan0" to "%wlan0", "192.168.1.20%3" to "%3").forEach {
                assertEquals(
                    "Remove the zone index (${it.value}): this address does not need one",
                    rules.check(specific(it.key)).specificAddressError,
                    it.key,
                )
            }
        }

        @Test
        fun `a link-local address with a zone index is refused as link-local`() {
            listOf("fe80::1%3", "fe80::1%wlan0").forEach {
                assertEquals(
                    "IPv6 link-local addresses (fe80::/10) cannot be used: binding to one needs an interface name",
                    rules.check(specific(it)).specificAddressError,
                    it,
                )
            }
        }

        @Test
        fun `a zone index with no address is not an IP literal`() {
            assertEquals("\"%3\" is not an IPv4 or IPv6 address", rules.check(specific("%3")).specificAddressError)
            assertEquals("\"%\" is not an IPv4 or IPv6 address", rules.check(specific("%")).specificAddressError)
        }

        @Test
        fun `an address the device does not have is refused`() {
            assertEquals("10.0.0.9 is not an address of this device", rules.check(specific("10.0.0.9")).startError)
        }
    }

    @Nested
    @DisplayName("Cloudflare and ngrok listener")
    inner class TunnelListener {
        @Test
        fun `no tunnel, or the free E2E tunnel, needs no listener and checks no port`() {
            assertNull(rules.tunnelListenerPort(ServerConfig(httpsEnabled = true)))
            val arc = tunnel(https = true, provider = TunnelProviderType.ARC_TUNNEL)
            assertNull(rules.tunnelListenerPort(arc))
            assertNull(rules.check(arc).startError)
        }

        @Test
        fun `plain HTTP main on the tunnel port of 127_0_0_1 serves the tunnel itself`() {
            for (provider in listOf(TunnelProviderType.CLOUDFLARE, TunnelProviderType.NGROK)) {
                assertNull(rules.tunnelListenerPort(tunnel(BindingAddress.LOCALHOST, provider = provider)))
                assertNull(rules.tunnelListenerPort(tunnel(BindingAddress.NETWORK, provider = provider)))
            }
        }

        @Test
        fun `the Specific IP 127_0_0_1 behaves as Localhost`() {
            assertNull(rules.tunnelListenerPort(tunnel(BindingAddress.SPECIFIC, "127.0.0.1")))
            assertNotNull(rules.check(tunnel(BindingAddress.SPECIFIC, "127.0.0.1", https = true)).tunnelPortError)
        }

        @Test
        fun `another tunnel port gets a listener of its own`() {
            assertEquals(49200, rules.tunnelListenerPort(tunnel(tunnelPort = 49200)))
            assertEquals(49200, rules.tunnelListenerPort(tunnel(https = true, tunnelPort = 49200)))
            assertNull(rules.check(tunnel(https = true, tunnelPort = 49200)).startError)
        }

        @Test
        fun `a main listener on another address leaves 127_0_0_1 free for the tunnel, even on the same port`() {
            val config = tunnel(BindingAddress.SPECIFIC, "192.168.1.20", https = true)

            assertEquals(ServerConfig.DEFAULT_PORT, rules.tunnelListenerPort(config))
            assertNull(rules.check(config).startError)
        }

        @Test
        fun `HTTPS main holding 127_0_0_1 on the tunnel port is refused on the tunnel port`() {
            for (binding in listOf(BindingAddress.LOCALHOST, BindingAddress.NETWORK)) {
                val check = rules.check(tunnel(binding, https = true))

                assertNull(check.specificAddressError)
                assertEquals(
                    "With HTTPS on, the tunnel needs a plain HTTP listener of its own: " +
                        "choose a tunnel port other than the server port (8080)",
                    check.tunnelPortError,
                )
                assertEquals(check.tunnelPortError, check.startError)
            }
        }

        @Test
        fun `a disabled tunnel never blocks the start`() {
            assertNull(rules.check(tunnel(https = true).copy(tunnelEnabled = false)).startError)
        }
    }

    @Test
    fun `the Specific IP problem comes first`() {
        // A device without 127.0.0.1 refuses it, while HTTPS on its port also leaves the tunnel no listener
        val noLoopback = McpListenerRulesImpl({ literals[it] }, { emptyList() })

        val check = noLoopback.check(tunnel(BindingAddress.SPECIFIC, "127.0.0.1", https = true))

        assertNotNull(check.tunnelPortError)
        assertEquals("127.0.0.1 is not an address of this device", check.startError)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
