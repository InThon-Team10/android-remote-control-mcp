package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.accessibilityservice.AccessibilityService

/** Android-free view of the helper's overlay windows so the host and the hider can be tested with a fake. */
internal interface BubbleWindowController {
    /** Adds the bubble window; throws WindowManager.BadTokenException, IllegalStateException or SecurityException. */
    fun showBubble()

    /** Opens or closes the panel window; it is focusable only while open. */
    fun setPanelOpen(open: Boolean)

    /** Makes the bubble (and an open panel) visible or invisible. */
    fun setBubbleVisible(visible: Boolean)

    /** Makes every window invisible and touch-through, or restores it. Applies to windows added later too. */
    fun setTouchThrough(enabled: Boolean)

    /** Moves keyboard focus to the request input of the open panel. */
    fun focusInput()

    /** Places the bubble at a saved position (thousandths of the screen height); null keeps the default. */
    fun setBubblePosition(permille: Int?)

    /** Removes every window and releases the Compose hosts. Idempotent. */
    fun removeAll()
}

/** What the Compose windows can ask of the host. */
internal interface BubbleActions {
    /** The user dragged the bubble to [permille] (thousandths of the screen height above the bottom). */
    fun onBubbleMoved(permille: Int)

    fun onToggleBubble()

    fun onSend(text: String)

    fun onStop()

    fun onNewChat()

    fun onClosePanel()

    fun onToggleMic()

    fun onConfirm(
        callId: String,
        allow: Boolean,
    )
}

internal fun interface BubbleWindowsFactory {
    fun create(
        service: AccessibilityService,
        state: BubbleUiState,
        actions: BubbleActions,
    ): BubbleWindowController
}
