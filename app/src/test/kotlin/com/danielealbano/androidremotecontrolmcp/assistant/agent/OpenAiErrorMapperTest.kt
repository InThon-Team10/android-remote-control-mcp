package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.errorObject
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.headers
import com.openai.errors.BadRequestException
import com.openai.errors.InternalServerException
import com.openai.errors.NotFoundException
import com.openai.errors.OpenAIInvalidDataException
import com.openai.errors.OpenAIIoException
import com.openai.errors.OpenAIRetryableException
import com.openai.errors.PermissionDeniedException
import com.openai.errors.RateLimitException
import com.openai.errors.UnauthorizedException
import com.openai.errors.UnexpectedStatusCodeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException

class OpenAiErrorMapperTest {
    private fun unexpected(
        status: Int,
        code: String? = null,
    ) = UnexpectedStatusCodeException
        .builder()
        .statusCode(status)
        .headers(headers())
        .error(errorObject(code))
        .build()

    private fun badRequest(code: String?) =
        BadRequestException
            .builder()
            .headers(headers())
            .error(errorObject(code))
            .build()

    @Test
    fun `401 and 403 map to auth failed`() {
        assertEquals(
            AssistantError.ModelAuthFailed,
            OpenAiErrorMapper.map(UnauthorizedException.builder().headers(headers()).build()),
        )
        assertEquals(
            AssistantError.ModelAuthFailed,
            OpenAiErrorMapper.map(PermissionDeniedException.builder().headers(headers()).build()),
        )
    }

    @Test
    fun `invalid api key code maps to auth failed whatever the status`() {
        assertEquals(AssistantError.ModelAuthFailed, OpenAiErrorMapper.map(badRequest("invalid_api_key")))
    }

    @Test
    fun `429 and 402 map to quota exceeded`() {
        assertEquals(
            AssistantError.ModelQuotaExceeded,
            OpenAiErrorMapper.map(RateLimitException.builder().headers(headers()).build()),
        )
        assertEquals(AssistantError.ModelQuotaExceeded, OpenAiErrorMapper.map(unexpected(402)))
    }

    @ParameterizedTest
    @ValueSource(ints = [408, 500, 503, 504])
    fun `5xx and 408 map to unavailable`(status: Int) {
        assertEquals(AssistantError.ModelUnavailable, OpenAiErrorMapper.map(unexpected(status)))
    }

    @Test
    fun `internal server exception maps to unavailable`() {
        val error =
            InternalServerException
                .builder()
                .statusCode(502)
                .headers(headers())
                .build()

        assertEquals(AssistantError.ModelUnavailable, OpenAiErrorMapper.map(error))
    }

    @Test
    fun `policy codes map to blocked`() {
        assertEquals(AssistantError.ModelBlocked("invalid_prompt"), OpenAiErrorMapper.map(badRequest("invalid_prompt")))
        assertEquals(
            AssistantError.ModelBlocked("content_policy_violation"),
            OpenAiErrorMapper.map(badRequest("content_policy_violation")),
        )
    }

    @Test
    fun `other 4xx maps to request failed with status and code but never the server message`() {
        assertEquals(
            AssistantError.ModelRequestFailed("HTTP 400 context_length_exceeded"),
            OpenAiErrorMapper.map(badRequest("context_length_exceeded")),
        )
        assertEquals(
            AssistantError.ModelRequestFailed("HTTP 404"),
            OpenAiErrorMapper.map(NotFoundException.builder().headers(headers()).build()),
        )
    }

    @Test
    fun `network and retryable errors map to unavailable`() {
        assertEquals(AssistantError.ModelUnavailable, OpenAiErrorMapper.map(OpenAIIoException("x")))
        assertEquals(AssistantError.ModelUnavailable, OpenAiErrorMapper.map(OpenAIRetryableException("x")))
        assertEquals(AssistantError.ModelUnavailable, OpenAiErrorMapper.map(IOException("x")))
    }

    @Test
    fun `agent failure and assistant exception pass through`() {
        assertEquals(
            AssistantError.ApiKeyMissing,
            OpenAiErrorMapper.map(AgentFailure(AssistantError.ApiKeyMissing)),
        )
        assertEquals(
            AssistantError.BearerDisabled,
            OpenAiErrorMapper.map(AssistantException(AssistantError.BearerDisabled)),
        )
    }

    @Test
    fun `malformed data and unknown exceptions map to request failed with the class name only`() {
        assertEquals(
            AssistantError.ModelRequestFailed("OpenAIInvalidDataException"),
            OpenAiErrorMapper.map(OpenAIInvalidDataException("leak")),
        )
        assertEquals(
            AssistantError.ModelRequestFailed("IllegalStateException"),
            OpenAiErrorMapper.map(IllegalStateException("leak")),
        )
    }

    @Test
    fun `response error codes map to typed errors`() {
        assertEquals(AssistantError.ModelQuotaExceeded, OpenAiErrorMapper.mapResponseError("rate_limit_exceeded"))
        assertEquals(AssistantError.ModelUnavailable, OpenAiErrorMapper.mapResponseError("server_error"))
        assertEquals(AssistantError.ModelBlocked("bio_policy"), OpenAiErrorMapper.mapResponseError("bio_policy"))
        assertEquals(
            AssistantError.ModelRequestFailed("Response failed (invalid_image)."),
            OpenAiErrorMapper.mapResponseError("invalid_image"),
        )
        assertEquals(
            AssistantError.ModelRequestFailed("Response failed (no code)."),
            OpenAiErrorMapper.mapResponseError(null),
        )
    }
}
