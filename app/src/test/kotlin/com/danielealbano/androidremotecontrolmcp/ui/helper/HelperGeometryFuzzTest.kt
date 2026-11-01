package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Seeded property tests for spotlight clipping. */
class HelperGeometryFuzzTest {
    private fun nonNegative(
        rnd: Random,
        max: Int,
    ): Int = if (rnd.nextInt(8) == 0) 0 else rnd.nextInt(max + 1)

    private fun anyFloat(rnd: Random): Float =
        when (rnd.nextInt(12)) {
            0 -> Float.NaN
            1 -> Float.POSITIVE_INFINITY
            2 -> Float.NEGATIVE_INFINITY
            3 -> -0f
            4 -> Float.MAX_VALUE
            5 -> -Float.MAX_VALUE
            6 -> Float.MIN_VALUE
            else -> rnd.nextInt(-300, 600) / (if (rnd.nextBoolean()) 1f else 7f)
        }

    @Test
    fun `clipHighlight yields null or a finite positive rect inside both target and viewport`() {
        var clipped = 0
        var rejected = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val viewport = Rect(0f, 0f, nonNegative(rnd, 2000).toFloat(), nonNegative(rnd, 3000).toFloat())
                val target =
                    if (rnd.nextInt(10) == 0) null else Rect(anyFloat(rnd), anyFloat(rnd), anyFloat(rnd), anyFloat(rnd))
                val label = "seed=$seed iteration=$iteration viewport=$viewport target=$target"
                val result = clipHighlight(target, viewport)
                if (result == null) {
                    rejected++
                } else {
                    val t = checkNotNull(target) { label }
                    val values = listOf(result.left, result.top, result.right, result.bottom)
                    assertTrue(values.all { it.isFinite() }, label)
                    assertTrue(result.width > 0f && result.height > 0f, label)
                    assertTrue(result.left >= viewport.left && result.top >= viewport.top, label)
                    assertTrue(result.right <= viewport.right && result.bottom <= viewport.bottom, label)
                    assertTrue(result.left >= t.left && result.top >= t.top, label)
                    assertTrue(result.right <= t.right && result.bottom <= t.bottom, label)
                    assertEquals(result, clipHighlight(result, viewport), "idempotent: $label")
                    clipped++
                }
            }
        }
        assertTrue(clipped > 0 && rejected > 0, "clipped=$clipped rejected=$rejected")
    }

    @Test
    fun `clipHighlight with arbitrary viewports never throws and keeps positive size`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val viewport = Rect(anyFloat(rnd), anyFloat(rnd), anyFloat(rnd), anyFloat(rnd))
                val target = Rect(anyFloat(rnd), anyFloat(rnd), anyFloat(rnd), anyFloat(rnd))
                val result = clipHighlight(target, viewport)
                if (result != null) {
                    assertTrue(
                        result.width > 0f && result.height > 0f,
                        "seed=$seed iteration=$iteration viewport=$viewport target=$target result=$result",
                    )
                }
            }
        }
        assertNull(clipHighlight(null, Rect(0f, 0f, 10f, 10f)))
    }

    private companion object {
        val SEEDS = listOf(1L, 2L, 3L, 42L, 2024L, 99991L)
        const val ITERATIONS = 400
    }
}
