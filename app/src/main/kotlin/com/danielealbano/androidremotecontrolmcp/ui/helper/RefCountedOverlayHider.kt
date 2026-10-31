package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AssistantOverlayHider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [AssistantOverlayHider] honouring the contract: reference-counted, restores in `finally` also on cancellation,
 * switches to [main] for [apply], waits a bounded [settleMs] for the hide to reach the screen. [apply] must not
 * throw.
 * [apply] is called with true when the first caller hides and with false when the last one returns.
 */
internal class RefCountedOverlayHider(
    private val main: CoroutineDispatcher,
    private val settleMs: Long = SETTLE_MS,
    private val apply: (hidden: Boolean) -> Unit,
) : AssistantOverlayHider {
    private var count = 0

    override suspend fun <T> hiddenDuring(block: suspend () -> T): T {
        withContext(NonCancellable + main) { if (count++ == 0) apply(true) }
        try {
            delay(settleMs)
            return block()
        } finally {
            withContext(NonCancellable + main) { if (--count == 0) apply(false) }
        }
    }

    private companion object {
        const val SETTLE_MS = 50L
    }
}
