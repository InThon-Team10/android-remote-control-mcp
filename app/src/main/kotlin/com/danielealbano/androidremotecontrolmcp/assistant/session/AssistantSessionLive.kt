package com.danielealbano.androidremotecontrolmcp.assistant.session

/** Appends a chunk of the user's live speech transcript; chunks coalesce into one item until [endLiveTurn]. */
fun AssistantSession.appendLiveUserText(text: String) = transcript.appendLive(user = true, text = text)

/** Appends a chunk of the model's live speech transcript; chunks coalesce into one item until [endLiveTurn]. */
fun AssistantSession.appendLiveAssistantText(text: String) = transcript.appendLive(user = false, text = text)

/** Closes the open live transcript item (turn complete or interrupted). */
fun AssistantSession.endLiveTurn() = transcript.endLiveTurn()

/** Appends the step limit notice of a live conversation. */
fun AssistantSession.reportStepLimit(limit: Int) = transcript.append { id -> ChatItem.Notice(id, NoticeKind.StepLimitReached(limit)) }
