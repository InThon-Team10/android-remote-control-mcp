package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiSdkModelGateway
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.google.genai.AsyncSession
import com.google.genai.types.Blob
import com.google.genai.types.FunctionResponse
import com.google.genai.types.LiveConnectConfig
import com.google.genai.types.LiveSendRealtimeInputParameters
import com.google.genai.types.LiveSendToolResponseParameters
import com.google.genai.types.LiveServerMessage
import kotlinx.coroutines.future.await
import javax.inject.Inject
import javax.inject.Singleton

/** SDK-backed [LiveGateway] reusing the per-key `Client` of the text gateway. */
@Singleton
class GeminiSdkLiveGateway
    @Inject
    constructor(
        private val models: GeminiSdkModelGateway,
    ) : LiveGateway {
        override suspend fun connect(
            apiKey: String,
            config: LiveConnectConfig,
            onMessage: (LiveServerMessage) -> Unit,
            onClosed: (Throwable?) -> Unit,
        ): LiveConnection {
            val session =
                models
                    .clientFor(apiKey)
                    .async.live
                    .connect(AssistantConfig.LIVE_MODEL, config)
                    .await()
            // receive() completes once the callback is registered (not when the session ends): only a failed
            // registration means the session is unusable.
            session.receive { onMessage(it) }.whenComplete { _, error -> if (error != null) onClosed(error) }
            return SdkConnection(session)
        }

        private class SdkConnection(
            private val session: AsyncSession,
        ) : LiveConnection {
            override suspend fun sendAudio(pcm: ByteArray) {
                val blob =
                    Blob
                        .builder()
                        .mimeType(INPUT_MIME)
                        .data(pcm)
                        .build()
                session.sendRealtimeInput(LiveSendRealtimeInputParameters.builder().audio(blob).build()).await()
            }

            override suspend fun sendToolResponse(responses: List<FunctionResponse>) {
                val parameters = LiveSendToolResponseParameters.builder().functionResponses(responses).build()
                session.sendToolResponse(parameters).await()
            }

            override fun close() {
                session.close()
            }
        }

        private companion object {
            const val INPUT_MIME = "audio/pcm;rate=16000"
        }
    }
