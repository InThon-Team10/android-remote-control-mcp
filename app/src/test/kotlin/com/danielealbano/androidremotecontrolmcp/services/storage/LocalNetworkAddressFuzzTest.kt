package com.danielealbano.androidremotecontrolmcp.services.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.net.InetAddress
import kotlin.random.Random

@DisplayName("isLocalNetworkAddress property tests")
class LocalNetworkAddressFuzzTest {
    private class Cidr(
        text: String,
    ) {
        private val bits: Int
        private val prefix: Int
        private val network: BigInteger

        init {
            val (addr, len) = text.split('/')
            val bytes = InetAddress.getByName(addr).address
            bits = bytes.size * BYTE_BITS
            prefix = len.toInt()
            network = BigInteger(1, bytes).shiftRight(bits - prefix)
        }

        fun contains(bytes: ByteArray): Boolean =
            bytes.size * BYTE_BITS == bits && BigInteger(1, bytes).shiftRight(bits - prefix) == network
    }

    private val v4Local =
        listOf(
            "0.0.0.0/8",
            "10.0.0.0/8",
            "100.64.0.0/10",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "172.16.0.0/12",
            "192.0.0.0/24",
            "192.168.0.0/16",
        ).map(::Cidr)

    private val v6Local = listOf("fc00::/7", "fe80::/10", "fec0::/10").map(::Cidr)

    private val mappedHigh96 = BigInteger.valueOf(0xFFFFL)
    private val nat64High96 = BigInteger("0064ff9b" + "0".repeat(16), 16)

    private fun referenceV4(bytes: ByteArray) = v4Local.any { it.contains(bytes) }

    private fun reference(bytes: ByteArray): Boolean {
        if (bytes.size == V4_LEN) return referenceV4(bytes)
        val value = BigInteger(1, bytes)
        val low32 = value.and(BigInteger.valueOf(MASK_32))
        val embedded = ByteArray(V4_LEN) { low32.shiftRight(24 - BYTE_BITS * it).toInt().toByte() }
        val high96 = value.shiftRight(32)
        return when (high96) {
            BigInteger.ZERO, mappedHigh96, nat64High96 -> referenceV4(embedded)
            else -> v6Local.any { it.contains(bytes) }
        }
    }

    private val boundaryOctets =
        intArrayOf(0, 1, 15, 16, 31, 32, 63, 64, 100, 127, 128, 167, 168, 169, 172, 192, 253, 254, 255)

    private fun octet(rnd: Random): Int =
        if (rnd.nextBoolean()) boundaryOctets[rnd.nextInt(boundaryOctets.size)] else rnd.nextInt(OCTET_RANGE)

    private fun randomBytes(rnd: Random): ByteArray {
        val v4 = ByteArray(V4_LEN) { octet(rnd).toByte() }
        if (rnd.nextInt(3) != 0) return v4
        val v6 = ByteArray(V6_LEN)
        when (rnd.nextInt(7)) {
            0 -> {
                Unit
            }

            1 -> {
                v6[MAPPED_FIRST] = -1
                v6[MAPPED_FIRST + 1] = -1
            }

            2 -> {
                v6[1] = NAT64_SECOND
                v6[2] = NAT64_THIRD
                v6[3] = NAT64_FOURTH
            }

            3 -> {
                v6[0] = (0xf0 + rnd.nextInt(16)).toByte()
                v6[1] = octet(rnd).toByte()
            }

            4 -> {
                v6[0] = octet(rnd).toByte()
                v6[1] = octet(rnd).toByte()
                for (i in 2 until EMBEDDED_START) v6[i] = if (rnd.nextInt(4) == 0) octet(rnd).toByte() else 0
            }

            else -> {
                rnd.nextBytes(v6)
            }
        }
        System.arraycopy(v4, 0, v6, EMBEDDED_START, V4_LEN)
        return v6
    }

    @Test
    fun `classification agrees with a CIDR based BigInteger reference`() {
        val seed = 0x1AD001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val bytes = randomBytes(rnd)
            val address = InetAddress.getByAddress(bytes)
            assertEquals(
                reference(bytes),
                isLocalNetworkAddress(address),
                "seed=$seed iteration=$i address=${address.hostAddress}",
            )
        }
    }

    private companion object {
        const val ITERATIONS = 20000
        const val V4_LEN = 4
        const val V6_LEN = 16
        const val BYTE_BITS = 8
        const val OCTET_RANGE = 256
        const val MASK_32 = 0xFFFFFFFFL
        const val MAPPED_FIRST = 10
        const val EMBEDDED_START = 12
        const val NAT64_SECOND: Byte = 0x64
        const val NAT64_THIRD: Byte = -1
        const val NAT64_FOURTH: Byte = -101
    }
}
