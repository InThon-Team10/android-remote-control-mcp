package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.openai.OpenAiClientProvider
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.openai.core.jsonMapper
import com.openai.helpers.RealtimeConnection
import com.openai.helpers.RealtimeWebSocketOptions
import com.openai.models.realtime.RealtimeClientEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** SDK-backed [RealtimeGateway]: `RealtimeConnection` over the transport of the per-key client. */
@Singleton
class OpenAiRealtimeGateway
    @Inject
    constructor(
        private val clients: OpenAiClientProvider,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : RealtimeGateway {
        override suspend fun connect(
            apiKey: String,
            onEvent: (JsonObject) -> Unit,
            onClosed: (Throwable?) -> Unit,
        ): RealtimeLink {
            val options = clients.clientOptions(apiKey)
            val connection =
                runInterruptible(dispatcher) {
                    RealtimeConnection.connect(
                        options,
                        RealtimeWebSocketOptions.builder().model(AssistantConfig.LIVE_MODEL).build(),
                    )
                }
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            scope.launch { receive(connection, onEvent, onClosed) }
            return SdkLink(connection, scope, dispatcher)
        }

        /** One receive pending at a time; a failed or closed connection ends the loop. */
        private suspend fun CoroutineScope.receive(
            connection: RealtimeConnection,
            onEvent: (JsonObject) -> Unit,
            onClosed: (Throwable?) -> Unit,
        ) {
            val failure =
                runCatching {
                    while (isActive) {
                        val event = runInterruptible(dispatcher) { connection.receive() }
                        onEvent(Json.parseToJsonElement(jsonMapper().writeValueAsString(event)).jsonObject)
                    }
                }.exceptionOrNull()
            if (failure is CancellationException || failure is Error) throw failure
            onClosed(failure)
        }

        private class SdkLink(
            private val connection: RealtimeConnection,
            private val scope: CoroutineScope,
            private val dispatcher: CoroutineDispatcher,
        ) : RealtimeLink {
            private val sending = Mutex()

            override suspend fun send(event: JsonObject) {
                val typed = jsonMapper().readValue(event.toString(), RealtimeClientEvent::class.java)
                sending.withLock { runInterruptible(dispatcher) { connection.send(typed) } }
            }

            override fun close() {
                scope.cancel()
                connection.close()
            }
        }
    }
