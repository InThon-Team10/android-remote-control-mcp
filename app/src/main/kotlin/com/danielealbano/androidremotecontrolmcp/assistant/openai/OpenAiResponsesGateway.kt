package com.danielealbano.androidremotecontrolmcp.assistant.openai

import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.openai.errors.OpenAIException
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import javax.inject.Inject
import javax.inject.Singleton

/** Seam over the OpenAI Responses API so the model loops are unit-testable. */
interface OpenAiResponsesGateway {
    /**
     * One non-streaming `responses.create` call with [apiKey]. Throws the SDK's exceptions untouched; cancelling the
     * caller aborts the HTTP call and throws the cancellation.
     */
    suspend fun create(
        apiKey: String,
        params: ResponseCreateParams,
    ): Response
}

/**
 * SDK-backed gateway on the shared per-key client of [OpenAiClientProvider] (its retries and timeouts apply). The
 * blocking client runs inside `runInterruptible`: cancelling the coroutine interrupts the thread, which fails the HTTP
 * call at once, while the SDK's async client would keep a cancelled call running until its timeout.
 */
@Singleton
class OpenAiSdkResponsesGateway
    @Inject
    constructor(
        private val clients: OpenAiClientProvider,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : OpenAiResponsesGateway {
        override suspend fun create(
            apiKey: String,
            params: ResponseCreateParams,
        ): Response =
            try {
                runInterruptible(dispatcher) { clients.client(apiKey).responses().create(params) }
            } catch (error: OpenAIException) {
                // The interrupt of a cancelled call surfaces as an SDK I/O error: report the cancellation instead.
                currentCoroutineContext().ensureActive()
                throw error
            }
    }
