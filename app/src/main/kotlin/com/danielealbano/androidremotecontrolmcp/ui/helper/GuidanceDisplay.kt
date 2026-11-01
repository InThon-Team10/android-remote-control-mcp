package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
import kotlin.math.roundToInt

/** Screen metrics and text/geometry helpers for the guidance windows, all in screen pixels. */
internal class GuidanceDisplay(
    private val service: AccessibilityService,
) {
    private val density: Float get() = service.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).roundToInt()

    fun toDp(px: Int): Dp = (px / density).dp

    fun captionMinimumRoom(screen: PxSize): Int {
        val textRoom = dp(96) * service.resources.configuration.fontScale
        val desired = textRoom.roundToInt() + dp(CONTROLS_HEIGHT_DP + CONTROLS_GAP_DP + MARGIN_DP * 2)
        return minOf(desired, screen.height / 2)
    }

    fun screenSize(): PxSize {
        val bounds = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        return PxSize(bounds.width(), bounds.height())
    }

    /** Keyboard windows stay fully exposed. Re-read when the IME opens, closes or changes height. */
    fun typingScreenSize(): PxSize {
        val screen = screenSize()
        val keyboardTop =
            service.windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                .map { window -> android.graphics.Rect().also(window::getBoundsInScreen) }
                .filter { !it.isEmpty }
                .minOfOrNull { it.top }
        return typingViewport(screen, keyboardTop)
    }

    /** The target padded for the hole and clipped to the screen; null when it is invalid or off screen. */
    fun holeFor(
        bounds: BoundsData,
        screen: PxSize,
    ): PxRect? {
        val pad = dp(HOLE_PADDING_DP).toFloat()
        val padded = Rect(bounds.left - pad, bounds.top - pad, bounds.right + pad, bounds.bottom + pad)
        val viewport = Rect(0f, 0f, screen.width.toFloat(), screen.height.toFloat())
        val clipped = clipHighlight(padded, viewport) ?: return null
        return PxRect(
            clipped.left.roundToInt(),
            clipped.top.roundToInt(),
            clipped.right.roundToInt(),
            clipped.bottom.roundToInt(),
        )
    }

    /** Caption box for [caption] (both of its lines at the user's font scale), plus [extraHeight] for controls. */
    fun captionSize(
        caption: GuidanceCaption,
        screen: PxSize,
        extraHeight: Int,
    ): PxSize {
        val width = minOf(dp(CAPTION_MAX_WIDTH_DP), screen.width - 2 * dp(MARGIN_DP))
        val spPx = density * service.resources.configuration.fontScale
        val metrics =
            CaptionTextMetrics(
                instruction = CaptionTextSize(CAPTION_TEXT_SP * spPx, CAPTION_TEXT_SP * CAPTION_LINE_FACTOR * spPx),
                explanation = CaptionTextSize(EXPLANATION_TEXT_SP * spPx, EXPLANATION_LINE_SP * spPx),
                gapPx = dp(CAPTION_LINE_GAP_DP),
                padding = PxSize(dp(CAPTION_PADDING_H_DP), dp(CAPTION_PADDING_V_DP)),
            )
        return PxSize(width, estimateCaptionHeight(caption, width, metrics) + extraHeight)
    }

    /** The caption of [target]; a blank instruction becomes the default for its kind and scroll direction. */
    fun caption(target: GuidanceTarget): GuidanceCaption {
        val caption = guidanceCaption(target) { service.getString(defaultCaptionRes(target.kind, target.scrollDirection)) }
        if (target.swipeStart == null) return caption
        val label =
            service.getString(
                if (target.systemGesture) R.string.helper_gesture_edge else R.string.helper_gesture_inside,
            )
        return caption.copy(instruction = "$label\n${caption.instruction}")
    }

    companion object {
        const val MARGIN_DP = 16
        const val CAPTION_SEPARATION_DP = 16
        const val CONTROLS_HEIGHT_DP = 56
        const val CONTROLS_GAP_DP = 8
        private const val HOLE_PADDING_DP = 4
        private const val CAPTION_MAX_WIDTH_DP = 320
        private const val CAPTION_TEXT_SP = 22f
        private const val CAPTION_LINE_FACTOR = 1.4f

        /** The explanation line: the project's 18sp body text, [CAPTION_LINE_GAP_DP] below the instruction. */
        const val EXPLANATION_TEXT_SP = 18f
        const val EXPLANATION_LINE_SP = 26f
        const val CAPTION_LINE_GAP_DP = 4
        private const val CAPTION_PADDING_H_DP = 18
        private const val CAPTION_PADDING_V_DP = 14
    }
}
