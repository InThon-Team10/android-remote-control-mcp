package com.danielealbano.androidremotecontrolmcp.services.storage

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.Inet6Address
import java.net.InetAddress

@DisplayName("isLocalNetworkAddress")
class LocalNetworkAddressTest {
    @ParameterizedTest(name = "{0} is local")
    @ValueSource(
        strings = [
            // 0.0.0.0/8 (this host)
            "0.0.0.0", "0.255.255.255",
            // 127.0.0.0/8 (loopback)
            "127.0.0.1", "127.255.255.255",
            // 10.0.0.0/8
            "10.0.0.0", "10.255.255.255",
            // 172.16.0.0/12
            "172.16.0.0", "172.31.255.255",
            // 192.168.0.0/16
            "192.168.0.0", "192.168.255.255",
            // 169.254.0.0/16 (link-local, cloud metadata)
            "169.254.0.0", "169.254.169.254", "169.254.255.255",
            // 100.64.0.0/10 (CGNAT, Tailscale)
            "100.64.0.0", "100.100.100.100", "100.127.255.255",
            // IPv6 unspecified and loopback
            "::", "::1",
            // fe80::/10 (link-local)
            "fe80::1", "febf:ffff::1",
            // fc00::/7 (unique local)
            "fc00::1", "fdff:ffff::1",
            // fec0::/10 (deprecated site-local)
            "fec0::1", "feff:ffff::1",
            // 192.0.0.0/24 (IETF protocol assignments, 464XLAT CLAT 192.0.0.4)
            "192.0.0.0", "192.0.0.4", "192.0.0.255",
        ],
    )
    fun `local addresses are blocked`(literal: String) {
        assertTrue(isLocalNetworkAddress(InetAddress.getByName(literal)))
    }

    @ParameterizedTest(name = "{0} is public")
    @ValueSource(
        strings = [
            "1.1.1.1", "8.8.8.8",
            // Just outside the blocked IPv4 ranges
            "1.0.0.0", "9.255.255.255", "11.0.0.0", "126.255.255.255", "128.0.0.0",
            "172.15.255.255", "172.32.0.0", "192.0.1.0", "192.1.0.0", "192.167.255.255", "192.169.0.0",
            "169.253.255.255", "169.255.0.0", "100.63.255.255", "100.128.0.0",
            // Public IPv6 and just outside fe80::/10 and fc00::/7
            "2001:4860:4860::8888", "fe7f::1", "fbff::1", "fe00::1",
        ],
    )
    fun `public addresses are allowed`(literal: String) {
        assertFalse(isLocalNetworkAddress(InetAddress.getByName(literal)))
    }

    @ParameterizedTest(name = "IPv4-mapped {0} is local")
    @ValueSource(
        strings = ["0.1.2.3", "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1"],
    )
    fun `IPv4-mapped IPv6 forms of local addresses are blocked`(ipv4: String) {
        assertTrue(isLocalNetworkAddress(ipv6WithEmbedded(ipv4, mapped = true)))
    }

    @ParameterizedTest(name = "IPv4-compatible {0} is local")
    @ValueSource(
        strings = ["0.1.2.3", "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1"],
    )
    fun `IPv4-compatible IPv6 forms of local addresses are blocked`(ipv4: String) {
        assertTrue(isLocalNetworkAddress(ipv6WithEmbedded(ipv4, mapped = false)))
    }

    @ParameterizedTest(name = "NAT64 64:ff9b::{0} is local")
    @ValueSource(strings = ["127.0.0.1", "10.1.2.3", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0"])
    fun `NAT64 forms of local addresses are blocked`(ipv4: String) {
        assertTrue(isLocalNetworkAddress(nat64WithEmbedded(ipv4)))
    }

    @Test
    fun `NAT64 form of a public address is allowed`() {
        assertFalse(isLocalNetworkAddress(nat64WithEmbedded("8.8.8.8")))
    }

    @Test
    fun `IPv4-mapped and IPv4-compatible forms of public addresses are allowed`() {
        assertFalse(isLocalNetworkAddress(ipv6WithEmbedded("8.8.8.8", mapped = true)))
        assertFalse(isLocalNetworkAddress(ipv6WithEmbedded("8.8.8.8", mapped = false)))
    }

    /**
     * Builds the raw 16-byte IPv6 form (::ffff:a.b.c.d when [mapped], ::a.b.c.d otherwise). Built from bytes because
     * parsing the text form of a mapped address yields an Inet4Address, while a socket can still carry the IPv6 form.
     */
    private fun ipv6WithEmbedded(
        ipv4: String,
        mapped: Boolean,
    ): Inet6Address {
        val bytes = ByteArray(IPV6_LENGTH)
        if (mapped) {
            bytes[MAPPED_MARKER_INDEX] = MARKER
            bytes[MAPPED_MARKER_INDEX + 1] = MARKER
        }
        InetAddress.getByName(ipv4).address.copyInto(bytes, EMBEDDED_START)
        return Inet6Address.getByAddress(null, bytes, null)
    }

    /** Builds the 64:ff9b::/96 (NAT64) address that carries [ipv4] in its last 4 bytes. */
    private fun nat64WithEmbedded(ipv4: String): Inet6Address {
        val bytes = ByteArray(IPV6_LENGTH)
        bytes[1] = 0x64
        bytes[2] = 0xff.toByte()
        bytes[3] = 0x9b.toByte()
        InetAddress.getByName(ipv4).address.copyInto(bytes, EMBEDDED_START)
        return Inet6Address.getByAddress(null, bytes, null)
    }

    private companion object {
        const val IPV6_LENGTH = 16
        const val MAPPED_MARKER_INDEX = 10
        const val EMBEDDED_START = 12
        const val MARKER = 0xff.toByte()
    }
}
