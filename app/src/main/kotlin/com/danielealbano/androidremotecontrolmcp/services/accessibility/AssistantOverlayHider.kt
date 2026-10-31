package com.danielealbano.androidremotecontrolmcp.services.accessibility

/**
 * Hides the assistant overlays from the screen and from touches while the AI observes or acts on the device.
 *
 * Installed by the UI (D18/D19) in `McpAccessibilityService.assistantOverlayHider`; it MUST be (re)installed after
 * every `onServiceConnected`, because the service's `onDestroy` resets it to [NONE].
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
 * - (g) Every overlay window the UI adds as `TYPE_ACCESSIBILITY_OVERLAY` MUST set
 *   `WindowManager.LayoutParams.accessibilityTitle = OWN_OVERLAY_ACCESSIBILITY_TITLE`, otherwise the window is not
 *   recognised as the app's own and is exposed to MCP clients.
 */
interface AssistantOverlayHider {
    /** Runs [block] with the assistant overlays invisible (and touch-through), restoring them afterwards. */
    suspend fun <T> hiddenDuring(block: suspend () -> T): T

    companion object {
        /** Runs the block unchanged; used until the UI (D18/D19) installs its overlay hider. */
        val NONE: AssistantOverlayHider =
            object : AssistantOverlayHider {
                override suspend fun <T> hiddenDuring(block: suspend () -> T): T = block()
            }
    }
}
