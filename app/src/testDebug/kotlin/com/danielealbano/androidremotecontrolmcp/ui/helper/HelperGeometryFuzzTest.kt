package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Seeded property tests for caption/controls placement and spotlight clipping. */
class HelperGeometryFuzzTest {
    private fun anyInt(rnd: Random): Int =
        when (rnd.nextInt(10)) {
            0 -> Int.MIN_VALUE
            1 -> Int.MAX_VALUE
            2 -> 0
            3 -> rnd.nextInt(-5, 5)
            else -> rnd.nextInt(-6000, 6000)
        }

    private fun nonNegative(
        rnd: Random,
        max: Int,
    ): Int = if (rnd.nextInt(8) == 0) 0 else rnd.nextInt(max + 1)

    @Test
    fun `caption placement is clamped to the margin box for any target and never throws`() {
        var iterations = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val width = nonNegative(rnd, 3000)
                val height = nonNegative(rnd, 4000)
                val margin = nonNegative(rnd, 100)
                val separation = nonNegative(rnd, 100)
                val captionWidth = nonNegative(rnd, 4000)
                val captionHeight = nonNegative(rnd, 5000)
                val l = anyInt(rnd)
                val t = anyInt(rnd)
                val r = anyInt(rnd)
                val b = anyInt(rnd)
                val label =
                    "seed=$seed iteration=$iteration screen=${width}x$height margin=$margin separation=$separation " +
                        "caption=${captionWidth}x$captionHeight target=[$l,$t,$r,$b]"
                val p = placeHelperCaption(width, height, l, t, r, b, captionWidth, captionHeight, margin, separation)
                assertTrue(p.left >= margin && p.left <= maxOf(margin, width - margin - captionWidth), label)
                assertTrue(p.top >= margin && p.top <= maxOf(margin, height - margin - captionHeight), label)
                iterations++
            }
        }
        assertEquals(SEEDS.size * ITERATIONS, iterations)
    }

    @Test
    fun `caption placement for on screen targets prefers below and never overlaps the target when it fits`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val width = rnd.nextInt(200, 2000)
                val height = rnd.nextInt(300, 3000)
                val margin = rnd.nextInt(0, 40)
                val separation = rnd.nextInt(0, 40)
                val captionWidth = rnd.nextInt(0, 2200)
                val captionHeight = rnd.nextInt(0, 3200)
                val left = rnd.nextInt(0, width)
                val top = rnd.nextInt(0, height)
                val right = rnd.nextInt(left, width + 1)
                val bottom = rnd.nextInt(top, height + 1)
                val label =
                    "seed=$seed iteration=$iteration screen=${width}x$height margin=$margin separation=$separation " +
                        "caption=${captionWidth}x$captionHeight target=[$left,$top,$right,$bottom]"
                val p = placeHelperCaption(width, height, left, top, right, bottom, captionWidth, captionHeight, margin, separation)
                val belowSpace = height - margin - bottom - separation
                if (belowSpace >= captionHeight) assertTrue(p.below, "prefer below: $label")
                if (p.fits) {
                    val captionBottom = p.top + captionHeight
                    assertTrue(captionBottom <= top || p.top >= bottom, "fits but overlaps target: $label")
                    assertTrue(p.left >= margin && p.left + captionWidth <= width - margin, label)
                    assertTrue(p.top >= margin && p.top + captionHeight <= height - margin, "fits but overflows: $label")
                }
            }
        }
    }

    @Test
    fun `controls placement is within the screen disjoint from the target and honours preference`() {
        var nulls = 0
        var placed = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val height = rnd.nextInt(100, 3000)
                val margin = rnd.nextInt(0, 60)
                val controls = rnd.nextInt(0, 1500)
                val top = rnd.nextInt(0, height)
                val bottom = rnd.nextInt(top, height + 1)
                val preferTop = rnd.nextBoolean()
                val label =
                    "seed=$seed iteration=$iteration height=$height margin=$margin controls=$controls " +
                        "target=[$top,$bottom] preferTop=$preferTop"
                val result = helperControlsTop(height, top, bottom, controls, margin, preferTop)
                val topFits = margin + controls + margin <= top
                val bottomFits = height - margin - controls - margin >= bottom
                if (result == null) {
                    assertFalse(topFits || bottomFits, label)
                    nulls++
                } else {
                    assertTrue(result >= margin && result + controls <= height - margin, "outside: $label result=$result")
                    assertTrue(result + controls + margin <= top || result >= bottom + margin, "overlaps: $label result=$result")
                    if (preferTop && topFits) assertEquals(margin, result, label)
                    placed++
                }
            }
        }
        assertTrue(nulls > 0 && placed > 0, "nulls=$nulls placed=$placed")
    }

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
                    assertTrue(
                        result.left >= t.left && result.top >= t.top && result.right <= t.right && result.bottom <= t.bottom,
                        label,
                    )
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
