package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import kotlinx.coroutines.future.await
import javax.inject.Inject
import javax.inject.Singleton

/** Seam over the Gemini SDK so the agent loop is unit-testable. */
interface GeminiModelGateway {
    /** One non-streaming `generateContent` call. Throws the SDK's exceptions untouched. */
    suspend fun generate(
        apiKey: String,
        model: String,
        history: List<Content>,
        config: GenerateContentConfig,
    ): GenerateContentResponse
}

/**
 * SDK-backed gateway. One `Client` is cached per API key (a changed key closes the old client). Retries are the
 * SDK's own: 1 s initial delay, exponential, [MAX_RETRIES] retries on 429/500/502/503/504 and IO errors.
 */
@Singleton
class GeminiSdkModelGateway
    @Inject
    constructor() : GeminiModelGateway {
        private val lock = Any()
        private var cachedKey: String? = null
        private var cachedClient: Client? = null

        override suspend fun generate(
            apiKey: String,
            model: String,
            history: List<Content>,
            config: GenerateContentConfig,
        ): GenerateContentResponse =
            clientFor(apiKey)
                .async.models
                .generateContent(model, history, config)
                .await()

        private fun clientFor(apiKey: String): Client =
            synchronized(lock) {
                cachedClient?.takeIf { cachedKey == apiKey } ?: run {
                    cachedClient?.close()
                    Client
                        .builder()
                        .apiKey(apiKey)
                        .httpOptions(
                            HttpOptions
                                .builder()
                                .timeout(REQUEST_TIMEOUT_MS)
                                .retryOptions(
                                    HttpRetryOptions
                                        .builder()
                                        .attempts(MAX_RETRIES + 1)
                                        .initialDelay(INITIAL_DELAY_SECONDS)
                                        .expBase(EXP_BASE)
                                        .httpStatusCodes(RETRYABLE_STATUS_CODES)
                                        .build(),
                                ).build(),
                        ).build()
                        .also {
                            cachedClient = it
                            cachedKey = apiKey
                        }
                }
            }

        private companion object {
            const val REQUEST_TIMEOUT_MS = 90_000
            const val MAX_RETRIES = 3
            const val INITIAL_DELAY_SECONDS = 1.0
            const val EXP_BASE = 2.0
            val RETRYABLE_STATUS_CODES = listOf(429, 500, 502, 503, 504)
        }
    }
