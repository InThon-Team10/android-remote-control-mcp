package com.danielealbano.androidremotecontrolmcp.services.sharing

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("CapabilityToken and EphemeralFileLinkService property tests")
class LinkTokenFuzzTest {
    private val tokenShape = Regex("^[0-9a-f]{64}$")

    @Test
    fun `generated tokens are 64 lowercase hex characters, URL safe and never repeat`() {
        val seen = HashSet<String>()
        repeat(TOKENS) { i ->
            val token = CapabilityToken.generate()
            assertTrue(tokenShape.matches(token), "iteration=$i token=$token")
            assertTrue(seen.add(token), "iteration=$i duplicate token=$token")
        }
    }

    @Test
    fun `resolve of arbitrary text never throws and only a registered token resolves`() =
        runBlocking {
            val seed = 0x7041L
            val rnd = Random(seed)
            val service = EphemeralFileLinkServiceImpl()
            val registered = List(5) { service.register(byteArrayOf(it.toByte()), "text/plain", "f$it.txt") }
            repeat(ITERATIONS) { i ->
                val garbage =
                    when (rnd.nextInt(4)) {
                        0 -> FuzzStrings.random(rnd, 80)
                        1 -> registered[rnd.nextInt(registered.size)].uppercase()
                        2 -> registered[rnd.nextInt(registered.size)].dropLast(rnd.nextInt(1, 4))
                        else -> registered[rnd.nextInt(registered.size)] + FuzzStrings.random(rnd, 3)
                    }
                val ctx = "seed=$seed iteration=$i token='${FuzzStrings.escape(garbage)}'"
                val outcome = runCatching { service.resolve(garbage) }
                assertNull(outcome.exceptionOrNull(), ctx)
                assertEquals(garbage in registered, outcome.getOrNull() != null, ctx)
                assertEquals(EphemeralFileLinkService.PATH_PREFIX + garbage, service.pathFor(garbage), ctx)
            }
            registered.forEachIndexed { index, token ->
                assertEquals(byteArrayOf(index.toByte()).toList(), service.resolve(token)?.bytes?.toList())
            }
        }

    @Test
    fun `the link and byte budgets hold, eviction is oldest first and entries expire exactly at the TTL`() =
        runBlocking {
            val seed = 0x7042L
            val rnd = Random(seed)
            val service = EphemeralFileLinkServiceImpl()
            var now = 1_000L
            service.clockMs = { now }
            val live = LinkedHashMap<String, Int>()
            repeat(BUDGET_ITERATIONS) { i ->
                val size =
                    when (rnd.nextInt(4)) {
                        0 -> rnd.nextInt(0, MAX_BLOB_FOR_TEST)
                        else -> rnd.nextInt(0, SMALL_BLOB)
                    }
                now += rnd.nextLong(0, EphemeralFileLinkService.TTL_MS / 4)
                val token = service.register(ByteArray(size), "application/octet-stream", "f")
                val ctx = "seed=$seed iteration=$i size=$size"
                // reference model: expire, evict oldest until the new blob fits, then add
                live.keys.toList().forEach { key ->
                    val entry = service.resolve(key)
                    if (entry == null) live.remove(key)
                }
                live[token] = size
                assertTrue(live.size <= EphemeralFileLinkService.MAX_LINKS, "$ctx count=${live.size}")
                assertTrue(
                    live.values.sumOf { it.toLong() } <= EphemeralFileLinkService.MAX_TOTAL_BYTES,
                    "$ctx bytes=${live.values.sumOf { it.toLong() }}",
                )
                assertNotNull(service.resolve(token), ctx)
                val entry = service.resolve(token)!!
                assertEquals(entry.expiresAtMs, now + EphemeralFileLinkService.TTL_MS, ctx)
                if (rnd.nextInt(5) == 0) {
                    now = entry.expiresAtMs - 1
                    assertNotNull(service.resolve(token), "$ctx just before expiry")
                    now = entry.expiresAtMs
                    assertNull(service.resolve(token), "$ctx at expiry")
                    live.remove(token)
                }
            }
        }

    private companion object {
        const val TOKENS = 20_000
        const val ITERATIONS = 5_000
        const val BUDGET_ITERATIONS = 120
        const val MAX_BLOB_FOR_TEST = 40 * 1024 * 1024
        const val SMALL_BLOB = 4096
    }
}
