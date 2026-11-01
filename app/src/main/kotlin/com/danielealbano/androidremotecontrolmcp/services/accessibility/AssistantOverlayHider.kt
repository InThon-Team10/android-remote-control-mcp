package com.danielealbano.androidremotecontrolmcp.services.accessibility

/**
 * Hides the assistant overlays from the screen and from touches while the AI observes or acts on the device.
 *
 * Each [AccessibilityOverlayHost] provides one for its own windows; `McpAccessibilityService` installs their
 * [combine]d hider in `assistantOverlayHider` on every `onServiceConnected`, and its `onDestroy` resets it to [NONE].
 *
 * Contract every implementation MUST honour:
 * - (a) Overlapping calls are reference-counted; the overlays are restored only when the count returns to 0.
 * - (b) The overlays are restored in a `finally`, also when [block] throws or the caller is cancelled (use
 *   `withContext(NonCancellable)` for suspending restore steps).
 * - (c) Callable from any dispatcher; it switches to the main thread itself for view changes and runs [block] in
 *   the caller's context.
 * - (d) Waits for the hide/restore to take effect are bounded (e.g. at most 200 ms) so a display-off device
 *   cannot hang a tool call.
 * - (e) It never throws.
 * - (f) For gestures, touch-through MUST be applied before [block] runs.
 * - (g) Every overlay window the UI adds as `TYPE_ACCESSIBILITY_OVERLAY` MUST set its `WindowManager.LayoutParams`
 *   title (`title = OWN_OVERLAY_ACCESSIBILITY_TITLE`, as the tool-call indicator does), otherwise the window is not
 *   recognised as the app's own and is exposed to MCP clients.
 */
interface AssistantOverlayHider {
    /** Runs [block] with the assistant overlays invisible (and touch-through), restoring them afterwards. */
    suspend fun <T> hiddenDuring(block: suspend () -> T): T

    companion object {
        /** Runs the block unchanged; used while no overlay host is attached. */
        val NONE: AssistantOverlayHider =
            object : AssistantOverlayHider {
                override suspend fun <T> hiddenDuring(block: suspend () -> T): T = block()
            }

        /** One hider applying every hider in [hiders] around the block, first one outermost. */
        fun combine(hiders: List<AssistantOverlayHider>): AssistantOverlayHider =
            when (hiders.size) {
                0 -> NONE
                1 -> hiders.single()
                else -> CombinedOverlayHider(hiders.toList())
            }
    }
}

private class CombinedOverlayHider(
    private val hiders: List<AssistantOverlayHider>,
) : AssistantOverlayHider {
    override suspend fun <T> hiddenDuring(block: suspend () -> T): T = runNested(0, block)

    private suspend fun <T> runNested(
        index: Int,
        block: suspend () -> T,
    ): T = if (index == hiders.size) block() else hiders[index].hiddenDuring { runNested(index + 1, block) }
}
