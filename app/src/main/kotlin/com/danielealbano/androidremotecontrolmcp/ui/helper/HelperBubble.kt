package com.danielealbano.androidremotecontrolmcp.ui.helper

import kotlinx.coroutines.flow.StateFlow

/** Real state of the floating helper bubble window. */
enum class HelperBubbleStatus { OFF, STARTING, ON, FAILED }

/** The floating helper bubble as seen by the app's screens. */
interface HelperBubble {
    val status: StateFlow<HelperBubbleStatus>

    /** Expands the bubble's request input (no-op while the bubble is not ON). */
    fun openInput()
}
