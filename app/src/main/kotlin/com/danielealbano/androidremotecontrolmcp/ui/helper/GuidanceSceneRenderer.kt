package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
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
    private val onShowTarget: () -> Unit,
) {
    fun show(scene: GuidanceScene) {
        val screen = display.screenSize()
        when (scene) {
            GuidanceScene.Empty -> Unit
            is GuidanceScene.Explanation -> showExplanation(scene.text, screen)
            is GuidanceScene.Spotlight -> showSpotlight(scene.target, screen)
            is GuidanceScene.TypeHint -> showTypeHint(scene.target, screen)
            is GuidanceScene.SwipeHint -> showSwipeHint(scene.target, screen)
        }
    }

    private fun showExplanation(
        text: String,
        screen: PxSize,
    ) {
        val full = PxRect(0, 0, screen.width, screen.height)
        windows.add(BLOCKER_ID, GuidanceWindowSpec(full, touchable = true)) { DimLayer() }
        val maxHeight = (screen.height * EXPLANATION_MAX_HEIGHT_FRACTION / display.dp(1)).dp
        val bottomMargin = display.dp(PANEL_BOTTOM_MARGIN_DP)
        val spec = GuidanceWindowSpec(PxRect(0, bottomMargin, screen.width, 0), touchable = true, bottomAnchored = true)
        windows.add(PANEL_ID, spec) { ExplanationPanel(text, maxHeight, onShowTarget, controller::cancel) }
    }

    private fun showSpotlight(
        target: GuidanceTarget,
        screen: PxSize,
    ) {
        val caption = display.captionText(target)
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
     * A swipe is shown by an arrow along its path and a caption only. Nothing blocks the screen, so a 1-pixel
     * watcher window reports the user's touch (it receives ACTION_OUTSIDE for every touch elsewhere).
     */
    private fun showSwipeHint(
        target: GuidanceTarget,
        screen: PxSize,
    ) {
        val caption = display.captionText(target)
        val area = display.holeFor(target.bounds, screen)
        if (area == null) {
            showFloatingCaption(caption, screen)
            return
        }
        val direction = target.scrollDirection ?: ScrollDirection.UP
        val arrow =
            swipeArrowRect(area, direction, display.dp(ARROW_THICKNESS_DP), display.dp(ARROW_LENGTH_DP), screen)
        windows.add(ARROW_ID, GuidanceWindowSpec(arrow, touchable = false)) { SwipeArrow(direction) }
        windows.add(WATCHER_ID, GuidanceWindowSpec(WATCHER_RECT, touchable = true, watchOutside = true)) {}
        detector.reset(setOf(WATCHER_ID))
        // The caption goes outside the whole swipe path (not just the small pill): a caption window under the
        // finger would take the swipe instead of the app.
        showCaptionNear(area, caption, screen)
    }

    /** Typing keeps the keyboard usable: outline and caption only, nothing dimmed or blocked. */
    private fun showTypeHint(
        target: GuidanceTarget,
        screen: PxSize,
    ) {
        val caption = display.captionText(target)
        val hole = display.holeFor(target.bounds, screen)
        if (hole == null) {
            showFloatingCaption(caption, screen)
            return
        }
        showOutline(hole, screen)
        showCaptionNear(hole, caption, screen)
    }

    /** The caption with its close button next to [anchor], pointing at it, without dimming anything. */
    private fun showCaptionNear(
        anchor: PxRect,
        caption: String,
        screen: PxSize,
    ) {
        val hole = anchor
        val size = display.captionSize(caption, screen, extraHeight = display.dp(CONTROLS_HEIGHT_DP + CONTROLS_GAP_DP))
        val separation = display.dp(CAPTION_SEPARATION_DP)
        val placement = placeHelperCaption(screen, hole, size, display.dp(MARGIN_DP), separation)
        val right = placement.left + size.width
        val spec =
            if (placement.below) {
                GuidanceWindowSpec(PxRect(placement.left, placement.top, right, 0), touchable = true, wrapHeight = true)
            } else {
                val bottomMargin = screen.height - (hole.top - separation)
                val rect = PxRect(placement.left, bottomMargin, right, 0)
                GuidanceWindowSpec(rect, touchable = true, bottomAnchored = true)
            }
        val pointerX = if (placement.fits) hole.centerX - placement.left else null
        windows.add(CAPTION_ID, spec) { TypeHintCaption(caption, pointerX, placement.below, controller::cancel) }
    }

    /** The target is unknown or off screen: a caption at the bottom, nothing dimmed or blocked. */
    private fun showFloatingCaption(
        caption: String,
        screen: PxSize,
    ) {
        val size = display.captionSize(caption, screen, extraHeight = 0)
        val left = (screen.width - size.width) / 2
        val spec =
            GuidanceWindowSpec(
                PxRect(left, display.dp(MARGIN_DP * 2), left + size.width, 0),
                touchable = true,
                bottomAnchored = true,
            )
        windows.add(CAPTION_ID, spec) { TypeHintCaption(caption, null, true, controller::cancel) }
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
        const val BLOCKER_ID = 0
        const val PANEL_ID = 1
        const val CAPTION_ID = 2
        const val OUTLINE_FIRST_ID = 4
        const val ARROW_ID = 8
        const val WATCHER_ID = 9
        const val OUTLINE_THICKNESS_DP = 8
        const val ARROW_THICKNESS_DP = 56
        const val ARROW_LENGTH_DP = 128
        val WATCHER_RECT = PxRect(0, 0, 1, 1)
        const val PANEL_BOTTOM_MARGIN_DP = 24
        const val EXPLANATION_MAX_HEIGHT_FRACTION = 0.6f
    }
}
