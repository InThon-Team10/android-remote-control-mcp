package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceTarget
import kotlin.math.roundToInt

/** Screen metrics and text/geometry helpers for the guidance windows, all in screen pixels. */
internal class GuidanceDisplay(
    private val service: AccessibilityService,
) {
    private val density: Float get() = service.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).roundToInt()

    fun screenSize(): PxSize {
        val bounds = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        return PxSize(bounds.width(), bounds.height())
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

    /** Caption box for [caption], plus [extraHeight] for controls stacked with it. */
    fun captionSize(
        caption: String,
        screen: PxSize,
        extraHeight: Int,
    ): PxSize {
        val width = minOf(dp(CAPTION_MAX_WIDTH_DP), screen.width - 2 * dp(MARGIN_DP))
        val textPx = CAPTION_TEXT_SP * density * service.resources.configuration.fontScale
        val padding = PxSize(dp(CAPTION_PADDING_H_DP), dp(CAPTION_PADDING_V_DP))
        return PxSize(width, estimateCaptionHeight(caption, width, textPx, padding) + extraHeight)
    }

    /** The target's instruction, or the default for its kind and scroll direction. */
    fun captionText(target: GuidanceTarget): String =
        target.instruction.ifBlank {
            service.getString(defaultCaptionRes(target.kind, target.scrollDirection))
        }

    companion object {
        const val MARGIN_DP = 16
        const val CAPTION_SEPARATION_DP = 16
        const val CONTROLS_HEIGHT_DP = 56
        const val CONTROLS_GAP_DP = 8
        private const val HOLE_PADDING_DP = 4
        private const val CAPTION_MAX_WIDTH_DP = 320
        private const val CAPTION_TEXT_SP = 22f
        private const val CAPTION_PADDING_H_DP = 18
        private const val CAPTION_PADDING_V_DP = 14
    }
}
