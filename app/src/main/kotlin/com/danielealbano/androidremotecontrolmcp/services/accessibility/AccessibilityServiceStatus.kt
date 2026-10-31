package com.danielealbano.androidremotecontrolmcp.services.accessibility

/** A diagnostic snapshot; an enabled service can still be waiting for Android to bind it. */
data class AccessibilityServiceStatus(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val capabilities: AccessibilityCapabilities = AccessibilityCapabilities(),
    val activeWindowAvailable: Boolean = false,
    val interactiveWindowCount: Int = 0,
)

/** Capabilities actually granted by Android, rather than only requested in the manifest. */
data class AccessibilityCapabilities(
    val canRetrieveWindowContent: Boolean = false,
    val canPerformGestures: Boolean = false,
    val canTakeScreenshot: Boolean = false,
    val canRetrieveInteractiveWindows: Boolean = false,
    val canReportViewIds: Boolean = false,
)
