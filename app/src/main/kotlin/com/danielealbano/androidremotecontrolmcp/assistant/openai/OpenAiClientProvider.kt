package com.danielealbano.androidremotecontrolmcp.assistant.openai

import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OkHttpClient
import com.openai.core.ClientOptions
import com.openai.core.LogLevel
import com.openai.core.Timeout
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The OpenAI SDK client of one API key, shared by the text planner (Responses API) and the live voice session
 * (Realtime API). Call the blocking [OpenAIClient] inside `runInterruptible(Dispatchers.IO)`: interrupting the thread
 * cancels the HTTP call, while the SDK's async client keeps a cancelled call running.
 */
interface OpenAiClientProvider {
    /**
     * The REST client for [apiKey]. The same key returns the same client; another key closes the previous client
     * (failing its in-flight calls and open Realtime connections) and builds a new one.
     *
     * @throws IllegalArgumentException when [apiKey] is blank.
     */
    fun client(apiKey: String): OpenAIClient

    /**
     * The options behind [client] for `RealtimeConnection.connect`, cached and closed together with it.
     *
     * @throws IllegalArgumentException when [apiKey] is blank.
     */
    fun clientOptions(apiKey: String): ClientOptions
}

/**
 * SDK-backed provider. The options are those `OpenAIOkHttpClient` builds (the SDK's own OkHttp transport), kept so
 * that Realtime connections share the REST client's transport: the key is set explicitly (never read from the
 * environment), [MAX_RETRIES] retries, short timeouts and the SDK's HTTP logging off (bodies carry screen content).
 */
@Singleton
class OpenAiSdkClientProvider
    @Inject
    constructor() : OpenAiClientProvider {
        private val lock = Any()
        private var cached: CachedClient? = null

        override fun client(apiKey: String): OpenAIClient = cachedFor(apiKey).client

        override fun clientOptions(apiKey: String): ClientOptions = cachedFor(apiKey).options

        private fun cachedFor(apiKey: String): CachedClient {
            require(apiKey.isNotBlank()) { "OpenAI API key cannot be blank" }
            return synchronized(lock) {
                cached?.takeIf { it.apiKey == apiKey } ?: run {
                    cached?.client?.close()
                    CachedClient(apiKey, buildOptions(apiKey)).also { cached = it }
                }
            }
        }

        /** Deliberately not a data class: the key must never show up in a `toString()`. */
        private class CachedClient(
            val apiKey: String,
            val options: ClientOptions,
        ) {
            val client: OpenAIClient = OpenAIClientImpl(options)
        }

        internal companion object {
            const val MAX_RETRIES = 2
            val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
            val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

            fun buildOptions(apiKey: String): ClientOptions {
                val timeout =
                    Timeout
                        .builder()
                        .connect(CONNECT_TIMEOUT)
                        .request(REQUEST_TIMEOUT)
                        .build()
                return ClientOptions
                    .builder()
                    .httpClient(OkHttpClient.builder().timeout(timeout).build())
                    .apiKey(apiKey)
                    .maxRetries(MAX_RETRIES)
                    .timeout(timeout)
                    .logLevel(LogLevel.OFF)
                    .build()
            }
        }
    }
