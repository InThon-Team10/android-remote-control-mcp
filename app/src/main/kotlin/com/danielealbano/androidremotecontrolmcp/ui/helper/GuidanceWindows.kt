package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.danielealbano.androidremotecontrolmcp.services.accessibility.OWN_OVERLAY_ACCESSIBILITY_TITLE

/** Lifecycle, saved-state and view-model owners a [ComposeView] needs outside an Activity. */
internal class OverlayComposeOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performAttach()
        savedState.performRestore(Bundle())
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** Root of a window around its [ComposeView]; reports ACTION_DOWN and ACTION_OUTSIDE for [HoleTouchDetector]. */
internal class TouchReportingLayout(
    context: Context,
    private val windowId: Int,
    private val onTouch: (windowId: Int, touch: WindowTouch, eventTime: Long) -> Unit,
) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouch(windowId, WindowTouch.DOWN, ev.eventTime)
            MotionEvent.ACTION_OUTSIDE -> onTouch(windowId, WindowTouch.OUTSIDE, ev.eventTime)
        }
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * One guidance window at absolute screen [rect]. With [wrapHeight] the height follows the content; with
 * [bottomAnchored] (implies wrap) the window sits [PxRect.top] pixels above the bottom screen edge instead.
 */
internal data class GuidanceWindowSpec(
    val rect: PxRect,
    val touchable: Boolean,
    val watchOutside: Boolean = false,
    val wrapHeight: Boolean = false,
    val bottomAnchored: Boolean = false,
)

/**
 * The accessibility-overlay windows currently shown. Every window carries the own-overlay title, is positioned in
 * absolute screen pixels (no inset shift) and has its own Compose owners, destroyed on removal. Main thread only.
 */
internal class GuidanceWindowSet(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTouch: (windowId: Int, touch: WindowTouch, eventTime: Long) -> Unit,
) {
    private class Entry(
        val view: View,
        val owner: OverlayComposeOwner,
        val params: WindowManager.LayoutParams,
        val touchableFlags: Int,
    )

    private val entries = mutableListOf<Entry>()
    private var hidden = false

    fun add(
        id: Int,
        spec: GuidanceWindowSpec,
        content: @Composable () -> Unit,
    ) {
        val owner = OverlayComposeOwner().also { it.start() }
        val view =
            TouchReportingLayout(context, id, onTouch).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                addView(ComposeView(context).apply { setContent { GuidanceTheme(content) } })
            }
        val params = layoutParams(spec)
        val entry = Entry(view, owner, params, params.flags)
        if (hidden) applyHidden(entry, true)
        try {
            windowManager.addView(view, params)
            entries.add(entry)
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Could not add a guidance window", e)
            owner.destroy()
        }
    }

    fun clear() {
        entries.forEach { entry ->
            try {
                windowManager.removeView(entry.view)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Guidance window already removed", e)
            }
            entry.owner.destroy()
        }
        entries.clear()
    }

    /** Makes every window invisible and touch-through (or restores it); windows added meanwhile follow. */
    fun setHidden(value: Boolean) {
        hidden = value
        entries.forEach { applyHidden(it, value) }
    }

    private fun applyHidden(
        entry: Entry,
        value: Boolean,
    ) {
        entry.view.alpha = if (value) 0f else 1f
        entry.params.flags =
            if (value) entry.touchableFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else entry.touchableFlags
        if (entry.view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(entry.view, entry.params)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Could not update a guidance window", e)
            }
        }
    }

    private fun layoutParams(spec: GuidanceWindowSpec): WindowManager.LayoutParams {
        var flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!spec.touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (spec.watchOutside) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val height =
            if (spec.wrapHeight || spec.bottomAnchored) WindowManager.LayoutParams.WRAP_CONTENT else spec.rect.height
        return WindowManager
            .LayoutParams(
                spec.rect.width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = (if (spec.bottomAnchored) Gravity.BOTTOM else Gravity.TOP) or Gravity.LEFT
                x = spec.rect.left
                y = spec.rect.top
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = OWN_OVERLAY_ACCESSIBILITY_TITLE
            }
    }

    private companion object {
        const val TAG = "MCP:GuidanceWindows"
    }
}
