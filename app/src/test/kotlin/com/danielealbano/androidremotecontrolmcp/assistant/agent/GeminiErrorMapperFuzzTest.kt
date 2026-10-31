package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlin.random.Random

@DisplayName("GeminiErrorMapper fuzz")
class GeminiErrorMapperFuzzTest {
    private val codes =
        listOf(-1, 0, 200, 302, 400, 401, 402, 403, 404, 408, 418, 429, 499, 500, 502, 503, 504, 599) +
            listOf(Int.MAX_VALUE, Int.MIN_VALUE)

    private fun errors(
        random: Random,
        secret: String,
    ): List<Throwable> {
        val noise = AssistantFuzzGen.string(random, 10)
        val message = "$noise $secret $noise"
        val keyMarker = listOf("API key not valid", "API_KEY_INVALID", "other")[random.nextInt(3)]
        val code = codes[random.nextInt(codes.size)]
        return listOf(
            ApiException(code, "STATUS", "$keyMarker: $message"),
            ApiException(code, "INVALID_ARGUMENT", message),
            GenAiIOException(message),
            GenAiIOException(message, RuntimeException(secret)),
            IOException(message, IOException(secret)),
            IllegalStateException(message, IllegalArgumentException(secret)),
            TimeoutException(message),
            RuntimeException(message),
            AgentFailure(AssistantError.GeminiRequestFailed("HTTP 400 STATUS")),
            AssistantException(AssistantError.GeminiUnavailable),
        )
    }

    @Test
    fun `no mapped failure of any random exception contains a secret from its message or cause`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(100) { index ->
                val secret = AssistantFuzzGen.secret(random)
                for (error in errors(random, secret)) {
                    val mapped = GeminiErrorMapper.map(error)
                    assertFalse(mapped.toString().contains(secret), "seed=$seed index=$index $error -> $mapped")
                }
            }
        }
    }

    @Test
    fun `typed carriers pass their error through unchanged`() {
        val failure = AssistantError.GeminiBlocked("SAFETY")

        assertEquals(failure, GeminiErrorMapper.map(AgentFailure(failure)))
        assertEquals(failure, GeminiErrorMapper.map(AssistantException(failure)))
    }

    @Test
    fun `status codes map consistently for any code`() {
        for (code in codes) {
            val mapped = GeminiErrorMapper.map(ApiException(code, "S", "m"))
            val expected =
                when {
                    code == 401 || code == 403 -> AssistantError.GeminiAuthFailed
                    code == 429 || code == 402 -> AssistantError.GeminiQuotaExceeded
                    code == 408 || code >= 500 -> AssistantError.GeminiUnavailable
                    else -> AssistantError.GeminiRequestFailed("HTTP $code S")
                }
            assertEquals(expected, mapped, "code=$code")
        }
    }
}
