package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.accessibilityservice.AccessibilityService

/**
 * Owner of some of the helper's overlay windows (bubble, input, spotlight, speech), hosted by
 * [McpAccessibilityService] so the user enables a single service. Hosts are contributed to a Hilt `Set` with
 * `@Binds @IntoSet`. [attach] runs on the main thread after the service connects and [detach] before it is
 * destroyed; both are idempotent. [overlayHider] hides this host's windows while the AI captures the screen or
 * dispatches a gesture; the service combines the hiders of all hosts.
 */
interface AccessibilityOverlayHost {
    val overlayHider: AssistantOverlayHider get() = AssistantOverlayHider.NONE

    fun attach(service: AccessibilityService)

    fun detach()
}
