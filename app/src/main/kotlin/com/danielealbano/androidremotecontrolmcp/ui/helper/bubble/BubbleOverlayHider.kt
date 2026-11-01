package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AssistantOverlayHider
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hides the helper windows (invisible, touch-through) around [hiddenDuring] blocks. Reference-counted: the windows
 * are restored when the last overlapping block ends, in a finally under [NonCancellable]. Never throws.
 */
internal class BubbleOverlayHider(
    private val controller: () -> BubbleWindowController?,
    private val mainDispatcher: CoroutineDispatcher,
    private val settleMs: Long = SETTLE_MS,
) : AssistantOverlayHider {
    private val depth = AtomicInteger(0)

    override suspend fun <T> hiddenDuring(block: suspend () -> T): T {
        enter()
        try {
            return block()
        } finally {
            withContext(NonCancellable) { leave() }
        }
    }

    private suspend fun enter() {
        if (depth.getAndIncrement() != 0) return
        withContext(NonCancellable) {
            apply(true)
            delay(settleMs)
        }
    }

    private suspend fun leave() {
        if (depth.decrementAndGet() == 0) apply(false)
    }

    private suspend fun apply(hidden: Boolean) {
        try {
            withContext(mainDispatcher) { controller()?.setTouchThrough(hidden) }
        } catch (error: IllegalStateException) {
            Logger.w(TAG, "Could not update helper windows: ${error.javaClass.simpleName}")
        } catch (error: IllegalArgumentException) {
            Logger.w(TAG, "Could not update helper windows: ${error.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "MCP:BubbleHider"
        const val SETTLE_MS = 50L
    }
}
