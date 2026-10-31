package com.danielealbano.androidremotecontrolmcp.mcp.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("TreeFingerprint.compare property tests")
class TreeFingerprintFuzzTest {
    private val fingerprint = TreeFingerprint()

    private fun randomCounts(rnd: Random): IntArray {
        val density = rnd.nextInt(1, 5)
        return IntArray(TreeFingerprint.FINGERPRINT_SIZE) { if (rnd.nextInt(density) == 0) rnd.nextInt(0, 5000) else 0 }
    }

    @Test
    fun `similarity is bounded, symmetric and 100 for identical fingerprints`() {
        val seed = 0xF1D001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val a = randomCounts(rnd)
            val b =
                if (rnd.nextInt(3) == 0) {
                    a.copyOf().also { it[rnd.nextInt(it.size)] += rnd.nextInt(0, 3) }
                } else {
                    randomCounts(rnd)
                }
            val ctx = "seed=$seed iteration=$i"
            val ab = fingerprint.compare(a, b)
            assertTrue(ab in 0..100, "$ctx out of range: $ab")
            assertEquals(ab, fingerprint.compare(b, a), "$ctx symmetry")
            assertEquals(100, fingerprint.compare(a, a.copyOf()), "$ctx identity")
        }
    }

    private companion object {
        const val ITERATIONS = 5000
    }
}
