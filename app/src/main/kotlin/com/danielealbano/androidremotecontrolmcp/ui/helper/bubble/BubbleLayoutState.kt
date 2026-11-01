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
