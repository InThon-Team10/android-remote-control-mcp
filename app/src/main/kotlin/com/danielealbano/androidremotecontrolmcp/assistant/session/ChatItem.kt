package com.danielealbano.androidremotecontrolmcp.assistant.session

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError

/** One entry of the in-memory assistant transcript. [id] is unique and monotonic within the process. */
sealed interface ChatItem {
    val id: Long

    data class User(
        override val id: Long,
        val text: String,
    ) : ChatItem

    data class Assistant(
        override val id: Long,
        val text: String,
    ) : ChatItem

    data class ToolCall(
        override val id: Long,
        val callId: String,
        val toolName: String,
        val status: ToolCallStatus,
    ) : ChatItem

    /** [answered] is null until the user answers; true = allowed, false = denied (or cancelled by stop). */
    data class Confirmation(
        override val id: Long,
        val callId: String,
        val toolName: String,
        val answered: Boolean?,
    ) : ChatItem

    data class Error(
        override val id: Long,
        val error: AssistantError,
    ) : ChatItem

    data class Notice(
        override val id: Long,
        val kind: NoticeKind,
    ) : ChatItem
}

enum class ToolCallStatus { RUNNING, DONE, FAILED, DENIED, STOPPED }

sealed interface NoticeKind {
    data class StepLimitReached(
        val limit: Int,
    ) : NoticeKind

    data object Stopped : NoticeKind
}
