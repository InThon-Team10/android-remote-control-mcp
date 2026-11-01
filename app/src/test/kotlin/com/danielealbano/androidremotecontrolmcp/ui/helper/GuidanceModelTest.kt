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
    fun `pointing step with an explanation is highlighted at once`() {
        val scene = guidanceScene(state(1))

        assertTrue(scene is GuidanceScene.Spotlight)
        assertEquals("누르면 전송돼요", (scene as GuidanceScene.Spotlight).target.explanation)
    }

    @Test
    fun `same state id keeps the scene and a new id gives a new one`() {
        val first = guidanceScene(state(1))

        assertEquals(first, guidanceScene(state(1)))
        assertNotEquals(first, guidanceScene(state(2)))
    }

    @Test
    fun `step without explanation goes straight to the spotlight`() {
        assertTrue(guidanceScene(state(1, explanation = null)) is GuidanceScene.Spotlight)
        assertTrue(guidanceScene(state(2, explanation = "  ")) is GuidanceScene.Spotlight)
    }

    @Test
    fun `every pointing kind is a spotlight`() {
        listOf(GuidanceKind.TAP, GuidanceKind.LONG_PRESS, GuidanceKind.DOUBLE_TAP).forEach { kind ->
            assertTrue(guidanceScene(state(1, kind)) is GuidanceScene.Spotlight, kind.name)
        }
    }

    @Test
    fun `scroll and type steps with an explanation get their own hints at once`() {
        assertTrue(guidanceScene(state(1, GuidanceKind.SCROLL)) is GuidanceScene.SwipeHint)
        assertTrue(guidanceScene(state(2, GuidanceKind.TYPE)) is GuidanceScene.TypeHint)
    }

    @Test
    fun `swipes are shown as an arrow hint, never as a spotlight box`() {
        val scene = guidanceScene(state(1, GuidanceKind.SCROLL, explanation = null))

        assertTrue(scene is GuidanceScene.SwipeHint)
    }

    @Test
    fun `null state clears the scene`() {
        assertEquals(GuidanceScene.Empty, guidanceScene(null))
    }

    @Test
    fun `caption shows the instruction with the trimmed explanation as its second line`() {
        val caption = guidanceCaption(state(1, explanation = "  누르면 전송돼요 ").target) { "기본" }

        assertEquals(GuidanceCaption("여기", "누르면 전송돼요"), caption)
    }

    @Test
    fun `caption without an explanation has only the instruction`() {
        assertNull(guidanceCaption(state(1, explanation = null).target) { "기본" }.explanation)
        assertNull(guidanceCaption(state(1, explanation = " ").target) { "기본" }.explanation)
    }

    @Test
    fun `a blank instruction takes the default caption`() {
        val target = GuidanceTarget(BoundsData(10, 20, 50, 60), GuidanceKind.TAP, " ", "누르면 전송돼요")

        assertEquals(GuidanceCaption("기본", "누르면 전송돼요"), guidanceCaption(target) { "기본" })
    }

    @Test
    fun `spoken text is the instruction followed by the explanation as its own sentence`() {
        assertEquals(
            "'보내기' 버튼을 눌러 주세요\n누르면 메시지가 전송돼요",
            GuidanceCaption("'보내기' 버튼을 눌러 주세요", "누르면 메시지가 전송돼요").spokenText,
        )
        assertEquals("눌러 주세요!\n전송돼요", GuidanceCaption("눌러 주세요!", "전송돼요").spokenText)
        assertEquals("눌러 주세요.\n전송돼요", GuidanceCaption("눌러 주세요.", "전송돼요").spokenText)
    }

    @Test
    fun `spoken text without an explanation is the instruction alone`() {
        assertEquals("여기를 눌러 주세요", GuidanceCaption("여기를 눌러 주세요").spokenText)
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

    private val textMetrics =
        CaptionTextMetrics(
            instruction = CaptionTextSize(22f, 31f),
            explanation = CaptionTextSize(18f, 26f),
            gapPx = 4,
            padding = PxSize(18, 14),
        )

    @Test
    fun `caption height grows with the text length`() {
        val short = estimateCaptionHeight(GuidanceCaption("가나"), 300, textMetrics)
        val long = estimateCaptionHeight(GuidanceCaption("가".repeat(60)), 300, textMetrics)

        assertEquals(31 + 2 * 14, short)
        assertTrue(long > short)
    }

    @Test
    fun `an explanation adds the gap and its own smaller lines to the caption height`() {
        // 264 px of text width: 12 instruction glyphs or 14 explanation glyphs per line.
        val instructionOnly = estimateCaptionHeight(GuidanceCaption("가".repeat(12)), 300, textMetrics)
        val withExplanation = estimateCaptionHeight(GuidanceCaption("가".repeat(12), "나".repeat(15)), 300, textMetrics)

        assertEquals(31 + 2 * 14, instructionOnly)
        assertEquals(instructionOnly + 4 + 2 * 26, withExplanation)
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
    fun `the caption room hugs the hole and stops short of the close button in its band`() {
        val below = computeSpotlightLayout(PxRect(200, 80, 300, 140), metrics())
        val above = computeSpotlightLayout(PxRect(200, 640, 300, 700), metrics())

        assertEquals(PxRect(110, 156, 390, 712), below.caption)
        assertEquals(728, below.closeTop)
        assertEquals(PxRect(110, 88, 390, 624), above.caption)
        assertEquals(16, above.closeTop)
    }

    @Test
    fun `the caption side must also hold the close button`() {
        // 150 px below the hole: room for the 100 px caption alone, not with the 56 px close button.
        val layout = computeSpotlightLayout(PxRect(200, 560, 300, 618), metrics())

        assertFalse(layout.captionBelow)
        assertEquals(PxRect(110, 88, 390, 544), layout.caption)
    }

    @Test
    fun `a caption too tall for either band scrolls in the roomier one instead of covering the hole`() {
        val hole = PxRect(100, 160, 180, 220)

        val layout = computeSpotlightLayout(hole, metrics(screen = PxSize(320, 400), caption = PxSize(280, 350)))

        assertTrue(layout.captionBelow)
        assertFalse(layout.captionFits)
        assertEquals(PxRect(16, 236, 296, 312), layout.caption)
        assertTrue(layout.caption.top > hole.bottom && layout.caption.bottom <= 400 - 16)
    }

    @Test
    fun `without room beside the close button the caption keeps its whole band`() {
        val layout =
            computeSpotlightLayout(
                PxRect(0, 100, 600, 650),
                metrics(caption = PxSize(280, 50), controlsHeight = 120),
            )

        assertEquals(PxRect(160, 666, 440, 784), layout.caption)
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
