package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException

class GeminiErrorMapperTest {
    private fun api(
        code: Int,
        status: String = "STATUS",
        message: String = "msg",
    ) = ApiException(code, status, message)

    @Test
    fun `400 with invalid key message maps to auth failed`() {
        val error = api(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.")

        assertEquals(AssistantError.ModelAuthFailed, GeminiErrorMapper.map(error))
    }

    @Test
    fun `400 other maps to request failed without server message`() {
        assertEquals(
            AssistantError.ModelRequestFailed("HTTP 400 INVALID_ARGUMENT"),
            GeminiErrorMapper.map(api(400, "INVALID_ARGUMENT", "secret prompt echo")),
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403])
    fun `401 and 403 map to auth failed`(code: Int) {
        assertEquals(AssistantError.ModelAuthFailed, GeminiErrorMapper.map(api(code)))
    }

    @ParameterizedTest
    @ValueSource(ints = [402, 429])
    fun `429 and 402 map to quota exceeded`(code: Int) {
        assertEquals(AssistantError.ModelQuotaExceeded, GeminiErrorMapper.map(api(code)))
    }

    @ParameterizedTest
    @ValueSource(ints = [408, 500, 503, 504])
    fun `5xx and 408 map to unavailable`(code: Int) {
        assertEquals(AssistantError.ModelUnavailable, GeminiErrorMapper.map(api(code)))
    }

    @Test
    fun `other 4xx maps to request failed`() {
        assertEquals(
            AssistantError.ModelRequestFailed("HTTP 404 NOT_FOUND"),
            GeminiErrorMapper.map(api(404, "NOT_FOUND")),
        )
    }

    @Test
    fun `network errors map to unavailable`() {
        assertEquals(AssistantError.ModelUnavailable, GeminiErrorMapper.map(GenAiIOException("x")))
        assertEquals(AssistantError.ModelUnavailable, GeminiErrorMapper.map(IOException("x")))
    }

    @Test
    fun `agent failure and assistant exception pass through`() {
        assertEquals(
            AssistantError.ApiKeyMissing,
            GeminiErrorMapper.map(AgentFailure(AssistantError.ApiKeyMissing)),
        )
        assertEquals(
            AssistantError.BearerDisabled,
            GeminiErrorMapper.map(AssistantException(AssistantError.BearerDisabled)),
        )
    }

    @Test
    fun `unknown exception maps to request failed with class name only`() {
        assertEquals(
            AssistantError.ModelRequestFailed("IllegalStateException"),
            GeminiErrorMapper.map(IllegalStateException("leak")),
        )
    }
}
