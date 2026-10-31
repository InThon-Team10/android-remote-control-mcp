package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.random.Random

@DisplayName("McpListenerRulesImpl property tests")
class McpListenerRulesFuzzTest {
    private val ipv4Shape = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val ipv6Shape = Regex("^[0-9a-fA-F:.]+$")

    // Numeric literals only, never a name lookup: strict dotted-quad IPv4 or a colon-containing IPv6 literal
    private fun parseLiteral(text: String): InetAddress? {
        val v4 = ipv4Shape.matchEntire(text)
        return when {
            v4 != null -> {
                val parts = v4.groupValues.drop(1).map { it.toInt() }
                if (parts.all { it in 0..255 }) InetAddress.getByAddress(ByteArray(4) { parts[it].toByte() }) else null
            }

            ':' in text && ipv6Shape.matches(text) -> {
                runCatching { InetAddress.getByName(text) }.getOrNull()
            }

            else -> {
                null
            }
        }
    }

    private val deviceAddresses: List<InetAddress> =
        listOf("192.168.1.20", "10.0.0.5", "127.0.0.1", "2001:db8::1", "fe80::1").map { InetAddress.getByName(it) }

    private val rules = McpListenerRulesImpl(::parseLiteral) { deviceAddresses }

    private fun randomAddressText(rnd: Random): String =
        when (rnd.nextInt(9)) {
            0 -> ""
            1 -> listOf("192.168.1.20", "10.0.0.5", "127.0.0.1", "2001:db8::1", "::1")[rnd.nextInt(5)]
            2 -> listOf("fe80::1", "fe80::1%wlan0", "FE80::1", "febf::1")[rnd.nextInt(4)]
            3 -> listOf("192.168.1.20%eth0", "2001:db8::1%1", "10.0.0.5%")[rnd.nextInt(3)]
            4 -> "${rnd.nextInt(0, 300)}.${rnd.nextInt(0, 300)}.${rnd.nextInt(0, 300)}.${rnd.nextInt(0, 300)}"
            5 -> " ".repeat(rnd.nextInt(0, 2)) + listOf("127.0.0.1", "10.0.0.5")[rnd.nextInt(2)] + " "
            6 -> listOf("::ffff:127.0.0.1", "::ffff:10.0.0.5", "0:0:0:0:0:0:0:1", "::")[rnd.nextInt(4)]
            else -> FuzzStrings.random(rnd, 25)
        }

    private fun randomConfig(rnd: Random): ServerConfig {
        val port = if (rnd.nextBoolean()) 8080 else rnd.nextInt(1, 65536)
        val binding = BindingAddress.entries[rnd.nextInt(BindingAddress.entries.size)]
        return ServerConfig(
            port = port,
            bindingAddress = binding,
            specificBindAddress = randomAddressText(rnd),
            tunnelLocalPort = if (rnd.nextBoolean()) port else rnd.nextInt(1, 65536),
            httpsEnabled = rnd.nextBoolean(),
            tunnelEnabled = rnd.nextBoolean(),
            tunnelProvider = TunnelProviderType.entries[rnd.nextInt(TunnelProviderType.entries.size)],
        )
    }

    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    private fun holdsLoopback(config: ServerConfig): Boolean =
        when (config.bindingAddress) {
            BindingAddress.LOCALHOST, BindingAddress.NETWORK -> true
            BindingAddress.SPECIFIC -> parseLiteral(config.specificBindAddress.trim()) == loopback
        }

    @Test
    fun `tunnel port rules match a reference over random bind addresses and ports`() {
        val seed = 0x4C0001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val config = randomConfig(rnd)
            val ctx = "seed=$seed iteration=$i config=${FuzzStrings.escape(config.toString())}"
            val forwards = config.tunnelEnabled && config.tunnelProvider != TunnelProviderType.ARC_TUNNEL
            val collides = config.tunnelLocalPort == config.port && holdsLoopback(config)
            val outcome = runCatching { rules.check(config) to rules.tunnelListenerPort(config) }
            assertNull(outcome.exceptionOrNull(), ctx)
            val (check, listenerPort) = outcome.getOrThrow()
            assertEquals(forwards && config.httpsEnabled && collides, check.tunnelPortError != null, ctx)
            val expectedPort =
                when {
                    !forwards -> null
                    !config.httpsEnabled && collides -> null
                    else -> config.tunnelLocalPort
                }
            assertEquals(expectedPort, listenerPort, ctx)
        }
    }

    @Test
    fun `specific address errors accept only a zone-less non link-local address of the device`() {
        val seed = 0x4C0002L
        val rnd = Random(seed)
        var accepted = 0
        var rejected = 0
        repeat(ITERATIONS) { i ->
            val text = randomAddressText(rnd)
            val config = ServerConfig(bindingAddress = BindingAddress.SPECIFIC, specificBindAddress = text)
            val trimmed = text.trim()
            val ctx = "seed=$seed iteration=$i text='${FuzzStrings.escape(text)}'"
            val error =
                runCatching { rules.check(config).specificAddressError }.getOrElse { throw AssertionError(ctx, it) }
            val parsed = if (trimmed.isEmpty()) null else parseLiteral(trimmed.substringBefore('%'))
            val acceptable =
                parsed != null &&
                    '%' !in trimmed &&
                    !(parsed is Inet6Address && parsed.isLinkLocalAddress) &&
                    deviceAddresses.any { it.address.contentEquals(parsed.address) }
            if (acceptable) {
                assertNull(error, ctx)
                accepted++
            } else {
                assertNotNull(error, ctx)
                assertTrue(error!!.isNotBlank(), ctx)
                rejected++
            }
            val other = config.copy(bindingAddress = BindingAddress.entries.first { it != BindingAddress.SPECIFIC })
            assertNull(rules.check(other).specificAddressError, ctx)
        }
        assertTrue(accepted > 0 && rejected > 0, "accepted=$accepted rejected=$rejected")
    }

    private companion object {
        const val ITERATIONS = 5000
    }
}
