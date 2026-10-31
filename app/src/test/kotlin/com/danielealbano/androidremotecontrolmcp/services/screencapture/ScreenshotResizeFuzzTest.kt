package com.danielealbano.androidremotecontrolmcp.services.screencapture

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.math.abs
import kotlin.random.Random

@DisplayName("ScreenshotEncoder.resizeBitmapProportional property tests")
class ScreenshotResizeFuzzTest {
    private val encoder = ScreenshotEncoder()
    private var currentWidth = 0
    private var currentHeight = 0
    private var scaledWidth = -1
    private var scaledHeight = -1
    private val bitmap = mockk<Bitmap>()
    private val scaled = mockk<Bitmap>()

    @BeforeEach
    fun setUp() {
        mockkStatic(Bitmap::class)
        every { bitmap.width } answers { currentWidth }
        every { bitmap.height } answers { currentHeight }
        every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } answers {
            scaledWidth = secondArg()
            scaledHeight = thirdArg()
            scaled
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Bitmap::class)
    }

    private fun dimension(rnd: Random): Int =
        when (rnd.nextInt(4)) {
            0 -> rnd.nextInt(1, 20)
            1 -> rnd.nextInt(1, 4000)
            2 -> rnd.nextInt(1, 100_000)
            else -> rnd.nextInt(1, Int.MAX_VALUE)
        }

    private fun bound(rnd: Random): Int? =
        when (rnd.nextInt(6)) {
            0 -> null
            1 -> rnd.nextInt(1, 8)
            2 -> Int.MAX_VALUE - rnd.nextInt(3)
            else -> dimension(rnd)
        }

    /** Exact rational reference: the smaller of the two bound ratios (or 1) applied to both sides, floored. */
    private fun reference(
        w: Int,
        h: Int,
        maxW: Int?,
        maxH: Int?,
    ): Pair<Long, Long> {
        var num = BigInteger.ONE
        var den = BigInteger.ONE

        fun consider(
            bound: Int?,
            side: Int,
        ) {
            if (bound == null) return
            val n = BigInteger.valueOf(bound.toLong())
            val d = BigInteger.valueOf(side.toLong())
            if (n.multiply(den) < num.multiply(d)) {
                num = n
                den = d
            }
        }
        consider(maxW, w)
        consider(maxH, h)

        fun scale(side: Int) =
            BigInteger
                .valueOf(side.toLong())
                .multiply(num)
                .divide(den)
                .toLong()
                .coerceAtLeast(1)
        return scale(w) to scale(h)
    }

    @Test
    fun `resize matches the exact rational reference and its invariants`() {
        val seed = 0x5C2E01L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val w = dimension(rnd)
            val h = if (rnd.nextInt(3) == 0) w else dimension(rnd)
            val maxW = bound(rnd)
            val maxH = bound(rnd)
            currentWidth = w
            currentHeight = h
            scaledWidth = -1
            scaledHeight = -1
            val ctx = "seed=$seed iteration=$i size=${w}x$h maxW=$maxW maxH=$maxH"
            val result = encoder.resizeBitmapProportional(bitmap, maxW, maxH)
            val (expectedW, expectedH) = reference(w, h, maxW, maxH)
            if (expectedW == w.toLong() && expectedH == h.toLong()) {
                assertSame(bitmap, result, "$ctx must return the original when nothing shrinks")
                return@repeat
            }
            assertSame(scaled, result, ctx)
            assertEquals(expectedW, scaledWidth.toLong(), "$ctx width")
            assertEquals(expectedH, scaledHeight.toLong(), "$ctx height")
            assertInvariants(ctx, w, h, maxW, maxH)
        }
    }

    private fun assertInvariants(
        ctx: String,
        w: Int,
        h: Int,
        maxW: Int?,
        maxH: Int?,
    ) {
        assertTrue(scaledWidth in 1..w && scaledHeight in 1..h, "$ctx never upscales")
        assertTrue(scaledWidth <= (maxW ?: Int.MAX_VALUE) && scaledHeight <= (maxH ?: Int.MAX_VALUE), "$ctx bounds")
        val atWidthBound = maxW != null && scaledWidth == maxW
        val atHeightBound = maxH != null && scaledHeight == maxH
        assertTrue(atWidthBound || atHeightBound, "$ctx constraining side at bound")
        // A side clamped up to 1 pixel has no meaningful ratio to compare.
        if (scaledWidth == 1 || scaledHeight == 1) return
        val driftNumerator =
            if (atWidthBound) {
                scaledHeight.toLong() * w - h.toLong() * scaledWidth
            } else {
                scaledWidth.toLong() * h - w.toLong() * scaledHeight
            }
        val perPixel = if (atWidthBound) w else h
        assertTrue(abs(driftNumerator) < perPixel, "$ctx aspect ratio drifts more than 1px")
    }

    private companion object {
        const val ITERATIONS = 6000
    }
}
