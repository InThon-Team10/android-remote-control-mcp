package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Accessibility title that marks a `TYPE_ACCESSIBILITY_OVERLAY` window as drawn by this app (set through
 * `WindowManager.LayoutParams.accessibilityTitle`). Overlays of other accessibility services are never hidden.
 */
const val OWN_OVERLAY_ACCESSIBILITY_TITLE = "mcp-own-overlay"

/**
 * True for an accessibility-overlay window drawn by this app: window type
 * [AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY] and either the [OWN_OVERLAY_ACCESSIBILITY_TITLE] title or a
 * root owned by [ownPackage]. The type is checked first and the root is fetched only when the title does not match.
 */
internal fun isOwnOverlayWindow(
    window: AccessibilityWindowInfo,
    ownPackage: String,
): Boolean =
    window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
        (
            window.title?.toString() == OWN_OVERLAY_ACCESSIBILITY_TITLE ||
                window.root?.packageName?.toString() == ownPackage
        )

/** Returns [windows] without the overlays drawn by [ownPackage]; order is preserved. */
internal fun filterOwnOverlayWindows(
    windows: List<AccessibilityWindowInfo>,
    ownPackage: String,
): List<AccessibilityWindowInfo> = windows.filterNot { isOwnOverlayWindow(it, ownPackage) }

/**
 * True when [root] is the root of an own accessibility-overlay window (see [isOwnOverlayWindow]). A root without a
 * window is kept (returns false): an app window must never be hidden by mistake.
 */
internal fun isOwnOverlayRoot(
    root: AccessibilityNodeInfo,
    ownPackage: String,
): Boolean {
    val window = root.window ?: return false
    return window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
        (
            window.title?.toString() == OWN_OVERLAY_ACCESSIBILITY_TITLE ||
                root.packageName?.toString() == ownPackage
        )
}

/**
 * True when a `TYPE_WINDOW_STATE_CHANGED` event was fired by an own overlay window and must not update the current
 * package/activity. Returns false at once unless the event comes from [ownPackage]; [windows] is evaluated only
 * then, and the window is looked up by [eventWindowId] (a window that is already gone is not recognised).
 */
internal fun isOwnOverlayStateEvent(
    eventType: Int,
    eventPackage: CharSequence?,
    eventWindowId: Int,
    ownPackage: String,
    windows: () -> List<AccessibilityWindowInfo>,
): Boolean {
    val fromOwnWindowState =
        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage?.toString() == ownPackage
    val window = if (fromOwnWindowState) windows().firstOrNull { it.id == eventWindowId } else null
    return window != null && isOwnOverlayWindow(window, ownPackage)
}
