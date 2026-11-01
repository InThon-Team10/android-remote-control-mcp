package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.openai.errors.OpenAIIoException
import com.openai.errors.OpenAIRetryableException
import com.openai.errors.OpenAIServiceException
import com.openai.errors.PermissionDeniedException
import com.openai.errors.RateLimitException
import com.openai.errors.UnauthorizedException
import java.io.IOException
import kotlin.jvm.optionals.getOrNull

/** Failure classification for OpenAI SDK calls (pure; never inspects prompts, content or server messages). */
internal object OpenAiErrorMapper {
    private const val HTTP_PAYMENT_REQUIRED = 402
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_SERVER_ERROR = 500
    private const val INVALID_KEY_CODE = "invalid_api_key"
    private const val RATE_LIMIT_CODE = "rate_limit_exceeded"
    private const val SERVER_ERROR_CODE = "server_error"

    /** Error codes of a request or a response the service refused on policy grounds. */
    private val BLOCKED_CODES =
        setOf(
            "invalid_prompt",
            "content_policy_violation",
            "bio_policy",
            "misalignment_policy_violation",
            "image_content_policy_violation",
        )

    fun map(error: Throwable): AssistantError =
        when (error) {
            is AgentFailure -> error.error
            is AssistantException -> error.error
            is UnauthorizedException, is PermissionDeniedException -> AssistantError.ModelAuthFailed
            is RateLimitException -> AssistantError.ModelQuotaExceeded
            is OpenAIServiceException -> mapStatus(error)
            is OpenAIIoException, is OpenAIRetryableException, is IOException -> AssistantError.ModelUnavailable
            else -> AssistantError.ModelRequestFailed(error.javaClass.simpleName)
        }

    /** The error of a response that came back with status `failed`, from its error [code]. */
    fun mapResponseError(code: String?): AssistantError =
        when {
            code == RATE_LIMIT_CODE -> AssistantError.ModelQuotaExceeded
            code == SERVER_ERROR_CODE -> AssistantError.ModelUnavailable
            code != null && code in BLOCKED_CODES -> AssistantError.ModelBlocked(code)
            else -> AssistantError.ModelRequestFailed("Response failed (${code ?: "no code"}).")
        }

    private fun mapStatus(error: OpenAIServiceException): AssistantError {
        val status = error.statusCode()
        val code = error.code().getOrNull()
        return when {
            code == INVALID_KEY_CODE -> AssistantError.ModelAuthFailed
            code != null && code in BLOCKED_CODES -> AssistantError.ModelBlocked(code)
            status == HTTP_PAYMENT_REQUIRED -> AssistantError.ModelQuotaExceeded
            status == HTTP_REQUEST_TIMEOUT || status >= HTTP_SERVER_ERROR -> AssistantError.ModelUnavailable
            else -> AssistantError.ModelRequestFailed(listOfNotNull("HTTP $status", code).joinToString(" "))
        }
    }
}
