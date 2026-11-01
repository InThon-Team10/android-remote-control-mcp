package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import androidx.annotation.StringRes
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.NoticeKind
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig

/** The bubble is offered only while the assistant is usable (the built-in key counts as configured). */
internal fun bubblePrerequisitesMet(config: AssistantConfig): Boolean =
    config.enabled && config.privacyNoticeAccepted && config.apiKeyConfigured

/** The request may be sent only with a non-blank draft and while no run is in progress. */
internal fun sendEnabled(
    draft: String,
    running: Boolean,
): Boolean = !running && draft.isNotBlank()

/**
 * A helper window is visible and touchable only when it is shown, not hidden for a screenshot ([touchThrough]) and
 * not [suppressed] by a full-screen practice. The two hiding reasons are independent so one cannot undo the other.
 */
internal fun windowInteractive(
    shown: Boolean,
    touchThrough: Boolean,
    suppressed: Boolean,
): Boolean = shown && !touchThrough && !suppressed

/** What the panel shows below the request input. */
internal sealed interface PanelMessage {
    data object None : PanelMessage

    data class Answer(
        val text: String,
    ) : PanelMessage

    data class Problem(
        @StringRes val textRes: Int,
    ) : PanelMessage

    data class Confirm(
        val callId: String,
        val toolName: String,
    ) : PanelMessage
}

/**
 * Picks the message of the current exchange: a pending confirmation always wins (it must never hang), otherwise
 * the last answer, error or notice that follows the latest user request.
 */
internal fun panelMessage(items: List<ChatItem>): PanelMessage {
    items
        .filterIsInstance<ChatItem.Confirmation>()
        .firstOrNull { it.answered == null }
        ?.let { return PanelMessage.Confirm(it.callId, it.toolName) }
    val current = items.takeLastWhile { it !is ChatItem.User }
    return when (val last = current.lastOrNull { it.isMessage() }) {
        is ChatItem.Assistant -> PanelMessage.Answer(last.text)
        is ChatItem.Error -> PanelMessage.Problem(errorTextRes(last.error))
        is ChatItem.Notice -> PanelMessage.Problem(noticeTextRes(last.kind))
        else -> PanelMessage.None
    }
}

private fun ChatItem.isMessage(): Boolean {
    val message = this is ChatItem.Assistant || this is ChatItem.Error
    return message || this is ChatItem.Notice
}

/** Text of the first [ChatItem.Assistant] newer than [lastSpokenId], with its id; null when nothing is new. */
internal fun newAssistantText(
    items: List<ChatItem>,
    lastSpokenId: Long,
): Pair<Long, String>? =
    items
        .filterIsInstance<ChatItem.Assistant>()
        .lastOrNull { it.id > lastSpokenId }
        ?.let { it.id to it.text }

internal fun latestAssistantId(items: List<ChatItem>): Long {
    val ids = items.filterIsInstance<ChatItem.Assistant>().map { it.id }
    return ids.maxOrNull() ?: -1L
}

@StringRes
internal fun noticeTextRes(kind: NoticeKind): Int =
    when (kind) {
        is NoticeKind.StepLimitReached -> R.string.bubble_notice_step_limit
        NoticeKind.Stopped -> R.string.bubble_notice_stopped
    }

@StringRes
internal fun errorTextRes(error: AssistantError): Int =
    when (error) {
        AssistantError.ApiKeyMissing, AssistantError.ModelAuthFailed -> R.string.bubble_error_key
        AssistantError.ModelQuotaExceeded -> R.string.bubble_error_quota
        AssistantError.ModelUnavailable -> R.string.bubble_error_unavailable
        is AssistantError.ModelBlocked -> R.string.bubble_error_blocked
        is AssistantError.ModelRequestFailed -> R.string.bubble_error_generic
        else -> setupErrorTextRes(error)
    }

@StringRes
private fun setupErrorTextRes(error: AssistantError): Int =
    when (error) {
        AssistantError.VoiceRecordingFailed, AssistantError.VoiceDisabled -> R.string.bubble_error_voice
        AssistantError.MicrophonePermissionMissing -> R.string.bubble_error_mic_permission
        AssistantError.AssistantDisabled -> R.string.bubble_error_disabled
        AssistantError.PrivacyNoticeNotAccepted -> R.string.bubble_error_consent
        else -> R.string.bubble_error_server
    }
