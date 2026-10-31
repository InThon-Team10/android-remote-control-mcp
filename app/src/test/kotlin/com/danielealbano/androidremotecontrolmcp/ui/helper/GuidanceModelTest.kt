package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.geometry.Rect
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceState
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidanceModelTest {
    private fun state(
        id: Long,
        kind: GuidanceKind = GuidanceKind.TAP,
        explanation: String? = "누르면 전송돼요",
    ) = GuidanceState(id, GuidanceTarget(BoundsData(10, 20, 50, 60), kind, "여기", explanation))

    @Test
    fun `screen edge clipping preserves visible target and rejects unusable detections`() {
        val viewport = Rect(0f, 0f, 100f, 200f)

        assertEquals(Rect(0f, 20f, 40f, 200f), clipHighlight(Rect(-10f, 20f, 40f, 250f), viewport))
        assertNull(clipHighlight(null, viewport))
        assertNull(clipHighlight(Rect(110f, 20f, 150f, 40f), viewport))
        assertNull(clipHighlight(Rect(40f, 20f, 30f, 40f), viewport))
        assertNull(clipHighlight(Rect(Float.NaN, 20f, 40f, 50f), viewport))
        assertNull(clipHighlight(Rect(0f, 20f, Float.POSITIVE_INFINITY, 50f), viewport))
    }

    @Test
    fun `pointing step with an explanation starts with the explanation then the spotlight`() {
        val planner = GuidanceScenePlanner()

        assertTrue(planner.plan(state(1)) is GuidanceScene.Explanation)
        assertTrue(planner.showTarget() is GuidanceScene.Spotlight)
    }

    @Test
    fun `same state id keeps the phase and a new id starts over`() {
        val planner = GuidanceScenePlanner()
        planner.plan(state(1))
        planner.showTarget()

        assertTrue(planner.plan(state(1)) is GuidanceScene.Spotlight)
        assertTrue(planner.plan(state(2)) is GuidanceScene.Explanation)
    }

    @Test
    fun `step without explanation goes straight to the spotlight`() {
        val planner = GuidanceScenePlanner()

        assertTrue(planner.plan(state(1, explanation = null)) is GuidanceScene.Spotlight)
        assertTrue(planner.plan(state(2, explanation = "  ")) is GuidanceScene.Spotlight)
    }

    @Test
    fun `scroll and type steps skip the explanation`() {
        val planner = GuidanceScenePlanner()

        assertTrue(planner.plan(state(1, GuidanceKind.SCROLL)) is GuidanceScene.SwipeHint)
        assertTrue(planner.plan(state(2, GuidanceKind.TYPE)) is GuidanceScene.TypeHint)
    }

    @Test
    fun `swipes are shown as an arrow hint, never as a spotlight box`() {
        val planner = GuidanceScenePlanner()

        val scene = planner.plan(state(1, GuidanceKind.SCROLL))

        assertTrue(scene is GuidanceScene.SwipeHint)
        assertTrue(planner.showTarget() is GuidanceScene.SwipeHint)
    }

    @Test
    fun `null state clears the scene and resets the phase`() {
        val planner = GuidanceScenePlanner()
        planner.plan(state(1))
        planner.showTarget()

        assertEquals(GuidanceScene.Empty, planner.plan(null))
        assertTrue(planner.plan(state(1)) is GuidanceScene.Explanation)
    }

    @Test
    fun `default caption differs per kind and scroll direction`() {
        val captions =
            listOf(
                defaultCaptionRes(GuidanceKind.TAP, null),
                defaultCaptionRes(GuidanceKind.LONG_PRESS, null),
                defaultCaptionRes(GuidanceKind.DOUBLE_TAP, null),
                defaultCaptionRes(GuidanceKind.TYPE, null),
                defaultCaptionRes(GuidanceKind.SCROLL, null),
            ) + ScrollDirection.entries.map { defaultCaptionRes(GuidanceKind.SCROLL, it) }

        assertEquals(captions.size, captions.toSet().size)
        assertEquals(
            R.string.helper_overlay_default_scroll_up,
            defaultCaptionRes(GuidanceKind.SCROLL, ScrollDirection.UP),
        )
    }

    @Test
    fun `caption height grows with the text length`() {
        val padding = PxSize(18, 14)

        val short = estimateCaptionHeight("가나", 300, 22f, padding)
        val long = estimateCaptionHeight("가".repeat(60), 300, 22f, padding)

        assertTrue(long > short)
        assertTrue(short >= 2 * padding.height)
    }

    private fun metrics(
        screen: PxSize = PxSize(600, 800),
        caption: PxSize = PxSize(280, 100),
        controlsHeight: Int = 56,
    ) = SpotlightMetrics(screen, caption, controlsHeight, 16, 16)

    @Test
    fun `spotlight pieces surround the hole without overlapping it`() {
        val hole = PxRect(200, 300, 300, 360)

        val layout = computeSpotlightLayout(hole, metrics())

        assertEquals(PxRect(0, 0, 600, 300), layout.top)
        assertEquals(PxRect(0, 360, 600, 800), layout.bottom)
        assertEquals(PxRect(0, 300, 200, 360), layout.left)
        assertEquals(PxRect(300, 300, 600, 360), layout.right)
    }

    @Test
    fun `caption below the hole puts the close button above and vice versa`() {
        val above = computeSpotlightLayout(PxRect(200, 80, 300, 140), metrics())
        val below = computeSpotlightLayout(PxRect(200, 640, 300, 700), metrics())

        assertTrue(above.captionBelow)
        assertEquals(BandSide.BOTTOM, above.captionSide)
        assertEquals(BandSide.BOTTOM, above.closeSide)
        assertFalse(below.captionBelow)
        assertEquals(BandSide.TOP, below.captionSide)
        assertEquals(BandSide.TOP, below.closeSide)
        assertEquals(16, below.closeTop)
    }

    @Test
    fun `oversized caption does not claim to fit and never overlaps the hole as a fit`() {
        val layout =
            computeSpotlightLayout(
                PxRect(100, 160, 180, 220),
                metrics(screen = PxSize(320, 400), caption = PxSize(280, 350)),
            )

        assertFalse(layout.captionFits)
    }

    @Test
    fun `close button falls back to the roomier side when neither side has room`() {
        val layout =
            computeSpotlightLayout(
                PxRect(0, 100, 600, 650),
                metrics(caption = PxSize(280, 50), controlsHeight = 120),
            )

        assertEquals(BandSide.BOTTOM, layout.closeSide)
        assertNotEquals(100, layout.closeTop)
    }
}
