package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
import com.danielealbano.androidremotecontrolmcp.services.guidance.ScreenPoint
import com.danielealbano.androidremotecontrolmcp.ui.helper.GuidanceDisplay.Companion.CAPTION_SEPARATION_DP
import com.danielealbano.androidremotecontrolmcp.ui.helper.GuidanceDisplay.Companion.CONTROLS_GAP_DP
import com.danielealbano.androidremotecontrolmcp.ui.helper.GuidanceDisplay.Companion.CONTROLS_HEIGHT_DP
import com.danielealbano.androidremotecontrolmcp.ui.helper.GuidanceDisplay.Companion.MARGIN_DP

/** Adds the windows of one [GuidanceScene] to a [GuidanceWindowSet]. */
internal class GuidanceSceneRenderer(
    private val display: GuidanceDisplay,
    private val windows: GuidanceWindowSet,
    private val detector: HoleTouchDetector,
    private val controller: GuidanceController,
) {
    fun show(scene: GuidanceScene) {
        val screen = display.screenSize()
        when (scene) {
            GuidanceScene.Empty -> Unit
            is GuidanceScene.Spotlight -> showSpotlight(scene.target, screen)
            is GuidanceScene.TypeHint -> showTypeHint(scene.target, screen)
            is GuidanceScene.SwipeHint -> showSwipeHint(scene.target, screen)
        }
    }

    private fun showSpotlight(
        target: GuidanceTarget,
        screen: PxSize,
    ) {
        val caption = display.caption(target)
        val hole = display.holeFor(target.bounds, screen)
        if (hole == null) {
            showFloatingCaption(caption, screen)
            return
        }
        val metrics =
            SpotlightMetrics(
                screen,
                display.captionSize(caption, screen, extraHeight = 0),
                display.dp(CONTROLS_HEIGHT_DP),
                display.dp(MARGIN_DP),
                display.dp(CAPTION_SEPARATION_DP),
            )
        val layout = computeSpotlightLayout(hole, metrics)
        val pieces =
            listOf(
                Triple(0, layout.top, BandSide.TOP),
                Triple(1, layout.bottom, BandSide.BOTTOM),
                Triple(2, layout.left, null),
                Triple(3, layout.right, null),
            ).filter { it.second.width > 0 && it.second.height > 0 }
        detector.reset(pieces.map { it.first }.toSet())
        pieces.forEach { (id, rect, side) ->
            windows.add(id, GuidanceWindowSpec(rect, touchable = true, watchOutside = true)) {
                SpotlightBand(rect, layout, side, caption, hole.centerX, controller::cancel)
            }
        }
        showOutline(hole, screen)
    }

    /**
     * A swipe is shown by an arrow and a caption only: for a swipe gesture the arrow begins just past where the finger
     * goes down (never over that point), for scrolling a node it is centred on the node. Nothing blocks the screen, so
     * a 1-pixel watcher window reports the user's touch (it receives ACTION_OUTSIDE for every touch elsewhere).
     */
    private fun showSwipeHint(
        target: GuidanceTarget,
        screen: PxSize,
    ) {
        val caption = display.caption(target)
        val area = display.holeFor(target.bounds, screen)
        if (area == null) {
            showFloatingCaption(caption, screen)
            return
        }
        val direction = target.scrollDirection ?: ScrollDirection.UP
        val pill = SwipePillSize(display.dp(ARROW_THICKNESS_DP), display.dp(ARROW_LENGTH_DP), display.dp(ARROW_GAP_DP))
        val arrow = swipeArrowRect(area, target.swipeStart, direction, pill, screen)
        arrow?.let { windows.add(ARROW_ID, GuidanceWindowSpec(it, touchable = false)) { SwipeArrow(direction) } }
        val watcher = watcherRect(target.swipeStart, screen)
        windows.add(WATCHER_ID, GuidanceWindowSpec(watcher, touchable = true, watchOutside = true)) {}
        detector.reset(setOf(WATCHER_ID))
        // The caption goes outside the whole swipe path (not just the small pill): a caption window under the
        // finger would take the swipe instead of the app.
        val path = arrow?.let { area.union(it) } ?: area
        showCaptionNear(path, caption, screen)
    }

    private fun PxRect.union(other: PxRect) =
        PxRect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

    /** The 1 px touch watcher sits on a corner pixel that is never the swipe start. */
    private fun watcherRect(
        start: ScreenPoint?,
        screen: PxSize,
    ): PxRect =
        if (start != null && start.x == 0 && start.y == 0) {
            PxRect(screen.width - 1, screen.height - 1, screen.width, screen.height)
        } else {
            WATCHER_RECT
        }

    /** Typing keeps the keyboard usable: outline and caption only, nothing dimmed or blocked. */
    private fun showTypeHint(
        target: GuidanceTarget,
        screen: PxSize,
    ) {
        val caption = display.caption(target)
        val hole = display.holeFor(target.bounds, screen)
        if (hole == null) {
            showFloatingCaption(caption, screen)
            return
        }
        showOutline(hole, screen)
        showCaptionNear(hole, caption, screen)
    }

    /**
     * The caption with its close button next to [anchor], pointing at it, without dimming anything. The window stays
     * in the room on its side of [anchor] and never covers it; a caption taller than that room scrolls.
     */
    private fun showCaptionNear(
        anchor: PxRect,
        caption: GuidanceCaption,
        screen: PxSize,
    ) {
        val size = display.captionSize(caption, screen, extraHeight = display.dp(CONTROLS_HEIGHT_DP + CONTROLS_GAP_DP))
        val separation = display.dp(CAPTION_SEPARATION_DP)
        val placement = placeHelperCaption(screen, anchor, size, display.dp(MARGIN_DP), separation)
        val right = placement.left + size.width
        val spec =
            if (placement.below) {
                GuidanceWindowSpec(PxRect(placement.left, placement.top, right, 0), touchable = true, wrapHeight = true)
            } else {
                val rect = PxRect(placement.left, screen.height - placement.bottom, right, 0)
                GuidanceWindowSpec(rect, touchable = true, bottomAnchored = true)
            }
        val pointerX = if (placement.fits) anchor.centerX - placement.left else null
        val maxHeight = display.toDp(placement.height)
        windows.add(CAPTION_ID, spec) {
            TypeHintCaption(caption, pointerX, placement.below, maxHeight, controller::cancel)
        }
    }

    /** The target is unknown or off screen: a caption at the bottom, nothing dimmed or blocked. */
    private fun showFloatingCaption(
        caption: GuidanceCaption,
        screen: PxSize,
    ) {
        val size = display.captionSize(caption, screen, extraHeight = 0)
        val left = (screen.width - size.width) / 2
        val bottomMargin = display.dp(MARGIN_DP * 2)
        val spec =
            GuidanceWindowSpec(
                PxRect(left, bottomMargin, left + size.width, 0),
                touchable = true,
                bottomAnchored = true,
            )
        val maxHeight = display.toDp(screen.height - bottomMargin - display.dp(MARGIN_DP))
        windows.add(CAPTION_ID, spec) { TypeHintCaption(caption, null, true, maxHeight, controller::cancel) }
    }

    /** The outline as strips around the hole (a window over the hole would make apps ignore touches there). */
    private fun showOutline(
        hole: PxRect,
        screen: PxSize,
    ) {
        val thickness = display.dp(OUTLINE_THICKNESS_DP)
        val ring = outlineRing(hole, thickness, screen)
        outlineStrips(hole, ring).forEachIndexed { index, strip ->
            windows.add(OUTLINE_FIRST_ID + index, GuidanceWindowSpec(strip, touchable = false)) {
                SpotlightRingPart(ring, strip, thickness)
            }
        }
    }

    private companion object {
        const val CAPTION_ID = 2
        const val OUTLINE_FIRST_ID = 4
        const val ARROW_ID = 8
        const val WATCHER_ID = 9
        const val OUTLINE_THICKNESS_DP = 8
        const val ARROW_THICKNESS_DP = 56
        const val ARROW_LENGTH_DP = 128
        const val ARROW_GAP_DP = 16
        val WATCHER_RECT = PxRect(0, 0, 1, 1)
    }
}
