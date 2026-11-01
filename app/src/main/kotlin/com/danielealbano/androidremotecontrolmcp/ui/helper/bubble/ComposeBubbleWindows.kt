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

private fun removeHostedWindow(
    windowManager: WindowManager,
    window: HostedWindow,
) {
    try {
        windowManager.removeViewImmediate(window.view)
    } catch (error: IllegalArgumentException) {
        Logger.w("MCP:HelperBubble", "Helper window was already removed: ${error.javaClass.simpleName}")
    }
    window.owner.destroy()
}

private class HostedWindow(
    val view: ComposeView,
    val owner: OverlayLifecycleOwner,
)

private fun updateWindow(
    windowManager: WindowManager,
    window: HostedWindow,
    params: WindowManager.LayoutParams,
    interactive: Boolean,
) {
    window.view.visibility = if (interactive) View.VISIBLE else View.INVISIBLE
    if (!interactive) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    try {
        windowManager.updateViewLayout(window.view, params)
    } catch (error: IllegalArgumentException) {
        Logger.w("MCP:HelperBubble", "Could not update a helper window: ${error.javaClass.simpleName}")
    }
}

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
    private var thinking: HostedWindow? = null
    private var bubbleVisible = true
    private var touchThrough = false
    private var suppressed = false
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
            panel?.let { removeHostedWindow(windowManager, it) }
            panel = null
        }
        refresh()
    }

    override fun setThinking(visible: Boolean) {
        if (visible == (thinking != null)) return
        if (visible) {
            thinking = addWindow(params.thinking()) { BubbleThinkingWindow(actions) }
        } else {
            thinking?.let { removeHostedWindow(windowManager, it) }
            thinking = null
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

    override fun setSuppressed(suppressed: Boolean) {
        this.suppressed = suppressed
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
        thinking?.let { removeHostedWindow(windowManager, it) }
        thinking = null
        panel?.let { removeHostedWindow(windowManager, it) }
        bubble?.let { removeHostedWindow(windowManager, it) }
        panel = null
        bubble = null
    }

    private fun refresh() {
        val bubbleOn = windowInteractive(bubbleVisible && panel == null, touchThrough, suppressed)
        val panelOn = windowInteractive(bubbleVisible, touchThrough, suppressed)
        bubble?.let { updateWindow(windowManager, it, params.bubble(drag.offset), bubbleOn) }
        panel?.let { updateWindow(windowManager, it, params.panel(), panelOn) }
        val thinkingOn = windowInteractive(true, touchThrough, suppressed)
        thinking?.let { updateWindow(windowManager, it, params.thinking(), thinkingOn) }
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
}
