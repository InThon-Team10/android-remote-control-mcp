package com.danielealbano.androidremotecontrolmcp.assistant

/** Typed failure shown by the assistant chat; produced by the MCP connection, the server starter and the agent. */
sealed interface AssistantError {
    data object ApiKeyMissing : AssistantError

    data object BearerDisabled : AssistantError

    data object ServerStartBlocked : AssistantError

    data class ServerStartFailed(
        val message: String,
    ) : AssistantError

    data class McpUnavailable(
        val message: String,
    ) : AssistantError

    /** 400 API_KEY_INVALID / 401 / 403. */
    data object GeminiAuthFailed : AssistantError

    /** 429 / 402 after retries. */
    data object GeminiQuotaExceeded : AssistantError

    /** 5xx / 503 / 504 after retries, network errors. */
    data object GeminiUnavailable : AssistantError

    /** promptFeedback.blockReason / finishReason SAFETY etc. */
    data class GeminiBlocked(
        val reason: String,
    ) : AssistantError

    /** Other 4xx, malformed responses. */
    data class GeminiRequestFailed(
        val message: String,
    ) : AssistantError

    data object VoiceRecordingFailed : AssistantError

    data object AssistantDisabled : AssistantError

    data object PrivacyNoticeNotAccepted : AssistantError

    data object VoiceDisabled : AssistantError

    data object MicrophonePermissionMissing : AssistantError
}

/** Carrier of an [AssistantError] inside a [Result.failure]. */
class AssistantException(
    val error: AssistantError,
) : Exception(error.toString())
