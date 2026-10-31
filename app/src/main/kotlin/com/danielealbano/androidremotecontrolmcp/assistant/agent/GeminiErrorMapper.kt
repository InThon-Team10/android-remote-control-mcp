package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import java.io.IOException

/** Failure classification for SDK calls (pure; never inspects prompts or content). */
internal object GeminiErrorMapper {
    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_PAYMENT_REQUIRED = 402
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val INVALID_KEY_MARKER = "API key not valid"
    private const val INVALID_KEY_REASON = "API_KEY_INVALID"

    fun map(error: Throwable): AssistantError =
        when (error) {
            is AgentFailure -> error.error
            is AssistantException -> error.error
            is ApiException -> mapApi(error)
            is GenAiIOException, is IOException -> AssistantError.ModelUnavailable
            else -> AssistantError.ModelRequestFailed(error.javaClass.simpleName)
        }

    private fun mapApi(error: ApiException): AssistantError {
        val code = error.code()
        return when {
            code == HTTP_BAD_REQUEST && isInvalidKey(error) -> AssistantError.ModelAuthFailed
            code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> AssistantError.ModelAuthFailed
            code == HTTP_TOO_MANY_REQUESTS || code == HTTP_PAYMENT_REQUIRED -> AssistantError.ModelQuotaExceeded
            code == HTTP_REQUEST_TIMEOUT || code >= HTTP_SERVER_ERROR -> AssistantError.ModelUnavailable
            else -> AssistantError.ModelRequestFailed("HTTP $code ${error.status()}")
        }
    }

    private fun isInvalidKey(error: ApiException): Boolean {
        val message = error.message().orEmpty()
        return message.contains(INVALID_KEY_MARKER, ignoreCase = true) || message.contains(INVALID_KEY_REASON)
    }
}
