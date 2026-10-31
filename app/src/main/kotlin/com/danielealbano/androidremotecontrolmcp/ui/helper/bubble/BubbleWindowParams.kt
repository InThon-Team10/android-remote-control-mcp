package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.danielealbano.androidremotecontrolmcp.services.accessibility.OWN_OVERLAY_ACCESSIBILITY_TITLE

/** Layout parameters of the helper windows; every window carries the own-overlay title (hidden from MCP clients). */
internal class BubbleWindowParams(
    private val context: Context,
) {
    /** The bubble on the right edge, [offset] pixels above the bottom (null = the default position). */
    fun bubble(offset: Int? = null): WindowManager.LayoutParams =
        base(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            y = offset ?: defaultBubbleOffset()
        }

    fun defaultBubbleOffset(): Int = px(BUBBLE_BOTTOM_MARGIN_DP)

    fun minBubbleOffset(): Int = px(BUBBLE_MIN_OFFSET_DP)

    /** Room kept free at the top (status bar) when dragging. */
    fun topReserve(): Int = px(BUBBLE_TOP_RESERVE_DP)

    /** Height of the bubble window before it is measured. */
    fun bubbleHeight(): Int = px(BUBBLE_HEIGHT_DP)

    private fun px(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    /** Focusable (no FLAG_NOT_FOCUSABLE) so the IME can type into the request input. */
    fun panel(): WindowManager.LayoutParams =
        base(WindowManager.LayoutParams.MATCH_PARENT, 0).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

    private fun base(
        width: Int,
        extraFlags: Int,
    ): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or extraFlags,
                PixelFormat.TRANSLUCENT,
            ).apply { title = OWN_OVERLAY_ACCESSIBILITY_TITLE }

    private companion object {
        const val BUBBLE_BOTTOM_MARGIN_DP = 96
        const val BUBBLE_MIN_OFFSET_DP = 16
        const val BUBBLE_TOP_RESERVE_DP = 48
        const val BUBBLE_HEIGHT_DP = 92
    }
}
