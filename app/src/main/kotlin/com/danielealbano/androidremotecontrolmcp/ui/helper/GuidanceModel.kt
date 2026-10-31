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

    /** Version A: what the action will do, before the target is highlighted. */
    data class Explanation(
        val stateId: Long,
        val text: String,
    ) : GuidanceScene

    /** Dimmed screen with a hole over the target; touches outside the hole are blocked. */
    data class Spotlight(
        val stateId: Long,
        val target: GuidanceTarget,
    ) : GuidanceScene

    /** Outline and caption without dim or blockers, so the keyboard stays usable. */
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
 * Chooses the [GuidanceScene] per state: an explanation first for pointing actions that carry one, then the
 * spotlight. The phase is per state id; the same id emitted again keeps it, a new id starts over.
 */
internal class GuidanceScenePlanner {
    private var current: GuidanceState? = null
    private var explained = false

    fun plan(state: GuidanceState?): GuidanceScene {
        if (state?.id != current?.id) explained = false
        current = state
        return scene()
    }

    /** Ends the explanation phase of the current step. */
    fun showTarget(): GuidanceScene {
        explained = true
        return scene()
    }

    private fun scene(): GuidanceScene {
        val state = current ?: return GuidanceScene.Empty
        val target = state.target
        val explanation = target.explanation
        return when {
            target.kind == GuidanceKind.TYPE -> {
                GuidanceScene.TypeHint(state.id, target)
            }

            target.kind == GuidanceKind.SCROLL -> {
                GuidanceScene.SwipeHint(state.id, target)
            }

            !explanation.isNullOrBlank() && !explained -> {
                GuidanceScene.Explanation(state.id, explanation)
            }

            else -> {
                GuidanceScene.Spotlight(state.id, target)
            }
        }
    }
}

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

/** Estimated caption height; Korean glyphs are about one em wide, so latin text is over-estimated (safe). */
internal fun estimateCaptionHeight(
    text: String,
    width: Int,
    textPx: Float,
    paddingPx: PxSize,
): Int {
    val charsPerLine = ((width - 2 * paddingPx.width) / textPx).toInt().coerceAtLeast(1)
    val lines = ((text.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
    return (lines * textPx * CAPTION_LINE_HEIGHT_FACTOR).toInt() + 2 * paddingPx.height
}

private const val CAPTION_LINE_HEIGHT_FACTOR = 1.4f

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
 * The caption sits in the band on its side of the hole; the close button on the opposite side when there is room.
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
    val placement = placeHelperCaption(screen, hole, metrics.caption, metrics.margin, metrics.separation)
    val closeTop =
        helperControlsTop(screen.height, hole, metrics.controlsHeight, metrics.margin, preferTop = !placement.below)
            ?: if (hole.top >= screen.height - hole.bottom) {
                metrics.margin
            } else {
                screen.height - metrics.margin - metrics.controlsHeight
            }
    return SpotlightLayout(
        top = PxRect(0, 0, screen.width, hole.top),
        bottom = PxRect(0, hole.bottom, screen.width, screen.height),
        left = PxRect(0, hole.top, hole.left, hole.bottom),
        right = PxRect(hole.right, hole.top, screen.width, hole.bottom),
        caption =
            PxRect(
                placement.left,
                placement.top,
                placement.left + metrics.caption.width,
                placement.top + metrics.caption.height,
            ),
        captionBelow = placement.below,
        captionFits = placement.fits,
        closeTop = closeTop,
        closeSide = if (closeTop < hole.top) BandSide.TOP else BandSide.BOTTOM,
    )
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
