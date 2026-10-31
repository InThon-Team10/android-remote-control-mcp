package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.random.Random

@DisplayName("ArcTunnelFrames and wire payload property tests")
class ArcTunnelFramesFuzzTest {
    private fun encode(
        type: Int,
        payload: ByteArray,
    ): Buffer =
        Buffer().also {
            it.writeByte(type)
            it.writeInt(payload.size)
            it.write(payload)
        }

    @Test
    fun `encoded frames decode back to the same type and payload`() {
        val seed = 0xA2C001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val frames = List(rnd.nextInt(1, 6)) { rnd.nextInt(0, 256) to rnd.nextBytes(rnd.nextInt(0, 300)) }
            val buffer = Buffer()
            frames.forEach { (type, payload) -> buffer.write(encode(type, payload), (5 + payload.size).toLong()) }
            val decoded = ArrayList<Pair<Int, ByteArray>>()
            while (true) decoded.add(ArcTunnelFrames.read(buffer) ?: break)
            assertEquals(frames.size, decoded.size, "seed=$seed iteration=$i")
            frames.zip(decoded).forEach { (expected, actual) ->
                assertEquals(expected.first, actual.first, "seed=$seed iteration=$i type")
                assertTrue(expected.second.contentEquals(actual.second), "seed=$seed iteration=$i payload")
            }
        }
    }

    @Test
    fun `random and truncated byte streams only ever end cleanly or with an IOException`() {
        val seed = 0xA2C002L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val bytes = rnd.nextBytes(rnd.nextInt(0, 64))
            if (rnd.nextBoolean() && bytes.size >= 5) {
                // Keep the declared length plausible so the payload path is exercised too.
                bytes[1] = 0
                bytes[2] = 0
                bytes[3] = rnd.nextInt(0, 4).toByte()
            }
            val source = Buffer().write(bytes)
            var frames = 0
            val failure =
                runCatching {
                    while (ArcTunnelFrames.read(source) != null) {
                        frames++
                        check(frames <= bytes.size) { "frame count exceeds input size" }
                    }
                }.exceptionOrNull()
            assertTrue(failure == null || failure is IOException, "seed=$seed iteration=$i threw $failure")
        }
    }

    @Test
    fun `oversized and negative lengths are rejected before any allocation`() {
        for (length in listOf(Int.MIN_VALUE, -1, (1 shl 20) + 1, Int.MAX_VALUE)) {
            val source = Buffer().writeByte(1).writeInt(length)
            val thrown = runCatching { ArcTunnelFrames.read(source) }.exceptionOrNull()
            assertTrue(thrown is IOException, "length $length threw $thrown")
        }
    }

    @Test
    fun `payload decoding of arbitrary text throws only IllegalArgumentException`() {
        val seed = 0xA2C003L
        val rnd = Random(seed)
        val pieces =
            listOf("{", "}", "\"token\"", ":", "\"x\"", ",", "true", "null", "1", "\"addr\"", "[", "]", "\"use_https\"")
        repeat(ITERATIONS) { i ->
            val text =
                if (rnd.nextBoolean()) {
                    FuzzStrings.random(rnd, 60)
                } else {
                    List(rnd.nextInt(0, 14)) { pieces[rnd.nextInt(pieces.size)] }.joinToString("")
                }
            val open = runCatching { ArcTunnelJson.decodeFromString(ArcTunnelOpenPayload.serializer(), text) }
            val nudge = runCatching { ArcTunnelJson.decodeFromString(ArcTunnelRenewNudgePayload.serializer(), text) }
            listOf(open.exceptionOrNull(), nudge.exceptionOrNull()).forEach {
                assertTrue(
                    it == null || it is IllegalArgumentException,
                    "seed=$seed iteration=$i '${FuzzStrings.escape(text)}' threw $it",
                )
            }
            open.getOrNull()?.let { payload ->
                if (payload.isValid()) {
                    assertTrue(
                        payload.token.none(Char::isISOControl),
                        "seed=$seed iteration=$i valid payload has control token",
                    )
                }
            }
        }
    }

    private companion object {
        const val ITERATIONS = 4000
    }
}
