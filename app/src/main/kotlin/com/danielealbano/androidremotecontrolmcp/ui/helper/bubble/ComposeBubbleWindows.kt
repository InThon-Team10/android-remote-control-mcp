package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.accessibilityservice.AccessibilityService
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

internal class ComposeBubbleWindowsFactory
    @Inject
    constructor() : BubbleWindowsFactory {
        override fun create(
            service: AccessibilityService,
            state: BubbleUiState,
            actions: BubbleActions,
        ): BubbleWindowController = ComposeBubbleWindows(service, state, actions)
    }

private class HostedWindow(
    val view: ComposeView,
    val owner: OverlayLifecycleOwner,
)

/** [BubbleWindowController] backed by two accessibility-overlay windows, each hosting a ComposeView. */
internal class ComposeBubbleWindows(
    private val service: AccessibilityService,
    private val state: BubbleUiState,
    private val actions: BubbleActions,
) : BubbleWindowController {
    private val windowManager: WindowManager = service.getSystemService(WindowManager::class.java)
    private val params = BubbleWindowParams(service)
    private val focusTick = MutableStateFlow(0)
    private var bubble: HostedWindow? = null
    private var panel: HostedWindow? = null
    private var bubbleVisible = true
    private var touchThrough = false
    private val drag =
        BubbleDragTracker(
            params,
            screenHeight = { windowManager.currentWindowMetrics.bounds.height() },
            bubbleHeight = { bubble?.view?.height?.takeIf { it > 0 } ?: params.bubbleHeight() },
        )

    override fun showBubble() {
        if (bubble != null) return
        bubble =
            addWindow(params.bubble(drag.offset)) {
                BubbleCharacterWindow(
                    state,
                    actions,
                    onDrag = { deltaY ->
                        drag.drag(deltaY)
                        refresh()
                    },
                    onDragEnd = { drag.permille()?.let(actions::onBubbleMoved) },
                )
            }
        refresh()
    }

    override fun setPanelOpen(open: Boolean) {
        if (open == (panel != null)) return
        if (open) {
            panel = addWindow(params.panel()) { BubblePanelWindow(state, actions, focusTick) }
        } else {
            panel?.let(::removeWindow)
            panel = null
        }
        refresh()
    }

    override fun setBubbleVisible(visible: Boolean) {
        bubbleVisible = visible
        refresh()
    }

    override fun setTouchThrough(enabled: Boolean) {
        touchThrough = enabled
        refresh()
    }

    override fun focusInput() {
        focusTick.value += 1
    }

    override fun setBubblePosition(permille: Int?) {
        drag.restore(permille)
        refresh()
    }

    override fun removeAll() {
        panel?.let(::removeWindow)
        bubble?.let(::removeWindow)
        panel = null
        bubble = null
    }

    private fun refresh() {
        bubble?.let { update(it, params.bubble(drag.offset), shown = bubbleVisible && panel == null) }
        panel?.let { update(it, params.panel(), shown = bubbleVisible) }
    }

    private fun update(
        window: HostedWindow,
        params: WindowManager.LayoutParams,
        shown: Boolean,
    ) {
        val interactive = shown && !touchThrough
        window.view.visibility = if (interactive) View.VISIBLE else View.INVISIBLE
        if (!interactive) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(window.view, params)
        } catch (error: IllegalArgumentException) {
            Logger.w(TAG, "Could not update a helper window: ${error.javaClass.simpleName}")
        }
    }

    private fun addWindow(
        params: WindowManager.LayoutParams,
        content: @Composable () -> Unit,
    ): HostedWindow {
        val owner = OverlayLifecycleOwner()
        val view =
            ComposeView(service).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setContent { BubbleTheme(content) }
            }
        owner.resume()
        var added = false
        try {
            windowManager.addView(view, params)
            added = true
        } finally {
            if (!added) owner.destroy()
        }
        return HostedWindow(view, owner)
    }

    private fun removeWindow(window: HostedWindow) {
        try {
            windowManager.removeViewImmediate(window.view)
        } catch (error: IllegalArgumentException) {
            Logger.w(TAG, "Helper window was already removed: ${error.javaClass.simpleName}")
        }
        window.owner.destroy()
    }

    private companion object {
        const val TAG = "MCP:HelperBubble"
    }
}
