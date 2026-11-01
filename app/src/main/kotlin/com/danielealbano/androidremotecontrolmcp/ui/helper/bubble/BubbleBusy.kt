package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState

/** The 도움 버튼 shows "thinking" while a tool call runs or a typed request still waits for its answer. */
internal fun bubbleBusy(
    toolsInFlight: Boolean,
    awaiting: Boolean,
): Boolean = toolsInFlight || awaiting

/**
 * True while a typed request has not been answered: the session runs, no live conversation is active (it keeps the
 * session Running for its whole duration), a user request exists and no reply, error, notice or confirmation follows
 * it.
 */
internal fun awaitingTextAnswer(
    items: List<ChatItem>,
    session: SessionState,
    liveActive: Boolean,
): Boolean {
    val sinceUser = items.takeLastWhile { it !is ChatItem.User }
    val asked = sinceUser.size < items.size
    return session == SessionState.Running && !liveActive && asked && sinceUser.none { it.answersRequest() }
}

private fun ChatItem.answersRequest(): Boolean =
    this is ChatItem.Assistant || this is ChatItem.Error || this is ChatItem.Notice || this is ChatItem.Confirmation
