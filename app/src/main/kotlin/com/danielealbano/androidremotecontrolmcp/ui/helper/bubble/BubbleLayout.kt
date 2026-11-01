package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

/** The window layout once guidance and the thinking screen are taken into account. */
internal data class BubbleLayoutState(
    val panelOpen: Boolean,
    val bubbleVisible: Boolean,
    val thinking: Boolean,
)

/** Guidance always wins: while a step is shown nothing may cover the target, so thinking yields to it too. */
internal fun resolveLayout(
    panelRequested: Boolean,
    guidanceActive: Boolean,
    thinking: Boolean,
): BubbleLayoutState {
    val thinkingShown = thinking && !guidanceActive
    return BubbleLayoutState(
        panelOpen = panelRequested && !guidanceActive && !thinkingShown,
        bubbleVisible = !guidanceActive && !thinkingShown,
        thinking = thinkingShown,
    )
}

/**
 * Per-request latch for the thinking screen: it shows from the request until the first guidance step, never again in
 * that request (between steps only the bubble's busy label shows), and a new request starts it again.
 */
internal data class ThinkingTracker(
    private val latched: Boolean = false,
    val visible: Boolean = false,
) {
    fun next(
        awaiting: Boolean,
        guidanceActive: Boolean,
    ): ThinkingTracker =
        when {
            !awaiting -> ThinkingTracker()
            guidanceActive -> ThinkingTracker(latched = true, visible = false)
            else -> ThinkingTracker(latched = latched, visible = !latched)
        }
}
