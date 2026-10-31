package com.danielealbano.androidremotecontrolmcp.services.guidance

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Refresh only the highlighted node, so unrelated clocks/widgets cannot erase the user's pending step. */
class GuidanceAnchor(
    private val node: AccessibilityNodeInfo,
    private val expectedBounds: HighlightBounds,
) {
    private val text = node.text?.toString()
    private val description = node.contentDescription?.toString()
    private val resourceId = node.viewIdResourceName
    private val className = node.className?.toString()
    val windowId = node.windowId

    fun isValid(): Boolean =
        runCatching {
            if (!node.refresh() || !node.isVisibleToUser || !node.isEnabled) return@runCatching false
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val sameOrigin = bounds.left == expectedBounds.left && bounds.top == expectedBounds.top
            val sameExtent = bounds.right == expectedBounds.right && bounds.bottom == expectedBounds.bottom
            val sameContent = node.text?.toString() == text && node.contentDescription?.toString() == description
            val sameIdentity =
                node.viewIdResourceName == resourceId && node.className?.toString() == className &&
                    node.windowId == windowId
            sameOrigin && sameExtent && sameContent && sameIdentity
        }.getOrDefault(false)
}
