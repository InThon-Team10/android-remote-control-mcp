package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceKind
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceState
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget

/** What the guidance renderer shows for the current [GuidanceState]. */
internal sealed interface GuidanceScene {
    data object Empty : GuidanceScene

    /** Dimmed screen with a hole over the target; touches outside the hole are blocked. */
    data class Spotlight(
        val stateId: Long,
        val target: GuidanceTarget,
    ) : GuidanceScene

    /** Dimmed bands around the typing field, excluding the keyboard area. */
    data class TypeHint(
        val stateId: Long,
        val target: GuidanceTarget,
    ) : GuidanceScene

    /** An arrow along the swipe and a caption; nothing dimmed, boxed or blocked, so the swipe reaches the app. */
    data class SwipeHint(
        val stateId: Long,
        val target: GuidanceTarget,
    ) : GuidanceScene
}

/**
 * The [GuidanceScene] for [state]: every step highlights its target at once (an explanation is the caption's
 * second line, never a step of its own). The same state gives an equal scene, so the renderer keeps what it shows.
 */
internal fun guidanceScene(state: GuidanceState?): GuidanceScene {
    val shown = state ?: return GuidanceScene.Empty
    val target = shown.target
    return when (target.kind) {
        GuidanceKind.TYPE -> GuidanceScene.TypeHint(shown.id, target)
        GuidanceKind.SCROLL -> GuidanceScene.SwipeHint(shown.id, target)
        GuidanceKind.TAP, GuidanceKind.LONG_PRESS, GuidanceKind.DOUBLE_TAP -> GuidanceScene.Spotlight(shown.id, target)
    }
}

/** What the caption bubble says: the [instruction] and, as a smaller second line, the [explanation] if any. */
internal data class GuidanceCaption(
    val instruction: String,
    val explanation: String? = null,
) {
    /** What the helper reads aloud: the instruction, then the explanation as a sentence of its own. */
    val spokenText: String
        get() = listOfNotNull(instruction, explanation).joinToString("\n")
}

/** The caption of [target]: its instruction ([defaultInstruction] when blank) and its non-blank explanation. */
internal fun guidanceCaption(
    target: GuidanceTarget,
    defaultInstruction: () -> String,
): GuidanceCaption =
    GuidanceCaption(
        instruction = target.instruction.ifBlank(defaultInstruction),
        explanation = target.explanation?.trim()?.takeIf { it.isNotEmpty() },
    )

@StringRes
internal fun defaultCaptionRes(
    kind: GuidanceKind,
    direction: ScrollDirection?,
): Int =
    when (kind) {
        GuidanceKind.TAP -> {
            R.string.helper_overlay_default_tap
        }

        GuidanceKind.LONG_PRESS -> {
            R.string.helper_overlay_default_long_press
        }

        GuidanceKind.DOUBLE_TAP -> {
            R.string.helper_overlay_default_double_tap
        }

        GuidanceKind.TYPE -> {
            R.string.helper_overlay_default_type
        }

        GuidanceKind.SCROLL -> {
            when (direction) {
                ScrollDirection.UP -> R.string.helper_overlay_default_scroll_up
                ScrollDirection.DOWN -> R.string.helper_overlay_default_scroll_down
                ScrollDirection.LEFT -> R.string.helper_overlay_default_scroll_left
                ScrollDirection.RIGHT -> R.string.helper_overlay_default_scroll_right
                null -> R.string.helper_overlay_default_scroll
            }
        }
    }

/** Ignore missing, invalid or off-screen detection results rather than dimming everything. */
internal fun clipHighlight(
    target: Rect?,
    viewport: Rect,
): Rect? {
    if (target == null || !listOf(target.left, target.top, target.right, target.bottom).all { it.isFinite() }) {
        return null
    }
    return target.intersect(viewport).takeIf { it.width > 0f && it.height > 0f }
}

/** Size of one caption text in pixels: [glyphPx] per (Korean) glyph and [linePx] per line. */
internal data class CaptionTextSize(
    val glyphPx: Float,
    val linePx: Float,
)

/** Caption text sizes, the gap between the instruction and the explanation, and the bubble padding, in pixels. */
internal data class CaptionTextMetrics(
    val instruction: CaptionTextSize,
    val explanation: CaptionTextSize,
    val gapPx: Int,
    val padding: PxSize,
)

/**
 * Estimated height of [caption] in a bubble [width] pixels wide: the instruction's lines, then the gap and the
 * explanation's lines when there is one. Korean glyphs are about one em wide, so latin text is over-estimated (safe).
 */
internal fun estimateCaptionHeight(
    caption: GuidanceCaption,
    width: Int,
    metrics: CaptionTextMetrics,
): Int {
    val textWidth = width - 2 * metrics.padding.width
    val instruction = estimateTextHeight(caption.instruction, textWidth, metrics.instruction)
    val explanation =
        caption.explanation?.let { estimateTextHeight(it, textWidth, metrics.explanation) + metrics.gapPx } ?: 0
    return instruction + explanation + 2 * metrics.padding.height
}

private fun estimateTextHeight(
    text: String,
    width: Int,
    size: CaptionTextSize,
): Int {
    val charsPerLine = (width / size.glyphPx).toInt().coerceAtLeast(1)
    val lines = ((text.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
    return (lines * size.linePx).toInt()
}

internal enum class BandSide { TOP, BOTTOM }

internal data class SpotlightMetrics(
    val screen: PxSize,
    val caption: PxSize,
    val controlsHeight: Int,
    val margin: Int,
    val separation: Int,
)

/**
 * Geometry of the four blocker pieces around the hole plus the caption and the close button, in screen pixels.
 * The caption sits in the band on its side of the hole, the close button at that band's screen edge when there is
 * room. [caption] is the room the caption may fill, hugging the hole (a taller caption scrolls inside it): never
 * over the hole, and clear of the close button in the same band.
 */
internal data class SpotlightLayout(
    val top: PxRect,
    val bottom: PxRect,
    val left: PxRect,
    val right: PxRect,
    val caption: PxRect,
    val captionBelow: Boolean,
    val captionFits: Boolean,
    val closeTop: Int,
    val closeSide: BandSide,
) {
    val captionSide: BandSide get() = if (captionBelow) BandSide.BOTTOM else BandSide.TOP
}

internal fun computeSpotlightLayout(
    hole: PxRect,
    metrics: SpotlightMetrics,
): SpotlightLayout {
    val screen = metrics.screen
    // The close button goes to the caption's band when it fits there, so that side must hold both.
    val withClose = PxSize(metrics.caption.width, metrics.caption.height + metrics.controlsHeight + metrics.margin)
    val placement = placeHelperCaption(screen, hole, withClose, metrics.margin, metrics.separation)
    val closeTop =
        helperControlsTop(screen.height, hole, metrics.controlsHeight, metrics.margin, preferTop = !placement.below)
            ?: if (hole.top >= screen.height - hole.bottom) {
                metrics.margin
            } else {
                screen.height - metrics.margin - metrics.controlsHeight
            }
    val closeSide = if (closeTop < hole.top) BandSide.TOP else BandSide.BOTTOM
    return SpotlightLayout(
        top = PxRect(0, 0, screen.width, hole.top),
        bottom = PxRect(0, hole.bottom, screen.width, screen.height),
        left = PxRect(0, hole.top, hole.left, hole.bottom),
        right = PxRect(hole.right, hole.top, screen.width, hole.bottom),
        caption = captionRoom(placement, closeTop, closeSide, metrics),
        captionBelow = placement.below,
        captionFits = placement.fits,
        closeTop = closeTop,
        closeSide = closeSide,
    )
}

/**
 * The caption's room from [placement], minus the close button's row (and a margin) when the button is in the same
 * band. When that would leave nothing (no room even for the button), the caption keeps the whole band.
 */
private fun captionRoom(
    placement: HelperCaptionPlacement,
    closeTop: Int,
    closeSide: BandSide,
    metrics: SpotlightMetrics,
): PxRect {
    val shared = closeSide == (if (placement.below) BandSide.BOTTOM else BandSide.TOP)
    val top =
        if (shared && !placement.below) {
            maxOf(placement.top, closeTop + metrics.controlsHeight + metrics.margin)
        } else {
            placement.top
        }
    val bottom = if (shared && placement.below) minOf(placement.bottom, closeTop - metrics.margin) else placement.bottom
    val right = placement.left + metrics.caption.width
    return if (bottom > top) {
        PxRect(placement.left, top, right, bottom)
    } else {
        PxRect(placement.left, placement.top, right, placement.bottom)
    }
}

internal enum class WindowTouch { DOWN, OUTSIDE }

/**
 * Tells a touch inside the hole from one on a blocker. Compose apps send no click event for touch taps, so the
 * four blockers each watch outside touches: a touch is in the hole when every blocker saw ACTION_OUTSIDE for the
 * same event time and none of the renderer's windows received the ACTION_DOWN. Main thread only.
 */
internal class HoleTouchDetector {
    private class Touch {
        val outside = mutableSetOf<Int>()
        var down = false
    }

    private var blockers: Set<Int> = emptySet()
    private val touches = LinkedHashMap<Long, Touch>()

    fun reset(blockerIds: Set<Int>) {
        blockers = blockerIds
        touches.clear()
    }

    /** Records [touch]; true when it is the first outside touch for [eventTime] (the caller schedules [decide]). */
    fun record(
        windowId: Int,
        touch: WindowTouch,
        eventTime: Long,
    ): Boolean {
        if (blockers.isEmpty()) return false
        val entry = touches.getOrPut(eventTime) { Touch() }
        while (touches.size > MAX_TRACKED_TOUCHES) touches.remove(touches.keys.first())
        return when (touch) {
            WindowTouch.DOWN -> {
                entry.down = true
                false
            }

            WindowTouch.OUTSIDE -> {
                val first = entry.outside.isEmpty()
                if (windowId in blockers) entry.outside.add(windowId)
                first
            }
        }
    }

    /** True when the touch at [eventTime] landed in the hole; forgets it. */
    fun decide(eventTime: Long): Boolean {
        val entry = touches.remove(eventTime) ?: return false
        return blockers.isNotEmpty() && !entry.down && entry.outside == blockers
    }

    companion object {
        const val DECISION_DELAY_MS = 100L
        private const val MAX_TRACKED_TOUCHES = 8
    }
}
