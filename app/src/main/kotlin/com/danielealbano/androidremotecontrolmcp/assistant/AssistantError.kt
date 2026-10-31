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

    /** The AI service rejected the API key (401 / 403, or an invalid-key error code). */
    data object ModelAuthFailed : AssistantError

    /** Rate limit or quota exhausted (429 / 402) after the client's retries. */
    data object ModelQuotaExceeded : AssistantError

    /** Server errors and timeouts after the client's retries, network errors, a session the service ended. */
    data object ModelUnavailable : AssistantError

    /** The service refused the request or the reply (refusal, content filter, policy-flagged prompt). */
    data class ModelBlocked(
        val reason: String,
    ) : AssistantError

    /** Other 4xx, failed, incomplete or malformed responses. */
    data class ModelRequestFailed(
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
