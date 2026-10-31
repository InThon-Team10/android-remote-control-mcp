package com.danielealbano.androidremotecontrolmcp.services.guidance

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Replace this renderer to add a dimmed mask later; the session only supplies screen bounds. */
class GuidanceOverlay(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show(bounds: HighlightBounds) {
        clear()
        val highlight =
            object : View(service) {
                private val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(255, 80, 40)
                        style = Paint.Style.STROKE
                        strokeWidth = 3f * resources.displayMetrics.density
                    }
                private val location = IntArray(2)

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    getLocationOnScreen(location)
                    val inset = paint.strokeWidth / 2
                    canvas.drawRect(
                        bounds.left - location[0] + inset,
                        bounds.top - location[1] + inset,
                        bounds.right - location[0] - inset,
                        bounds.bottom - location[1] - inset,
                        paint,
                    )
                }
            }
        highlight.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    setFitInsetsTypes(0)
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
        windowManager.addView(highlight, params)
        view = highlight
    }

    fun clear() {
        view?.let { windowManager.removeViewImmediate(it) }
        view = null
    }
}
