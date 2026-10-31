package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.google.genai.types.FunctionResponse
import com.google.genai.types.LiveConnectConfig
import com.google.genai.types.LiveServerMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface LiveVoiceState {
    data object Idle : LiveVoiceState

    data object Connecting : LiveVoiceState

    data object Listening : LiveVoiceState

    data object Speaking : LiveVoiceState

    data class Error(
        val error: AssistantError,
    ) : LiveVoiceState
}

/** True while a live conversation holds the microphone and speaker (connecting, listening or speaking). */
fun LiveVoiceState.isActive(): Boolean = this != LiveVoiceState.Idle && this !is LiveVoiceState.Error

/** Live voice conversation with the assistant (D27). Transcripts and tool calls land in the AssistantSession. */
interface LiveVoiceSession {
    val state: StateFlow<LiveVoiceState>

    /** Idempotent while running. Gate and connection failures are returned (typed AssistantException) and set Error. */
    suspend fun start(): Result<Unit>

    /** Ends the conversation and releases microphone, speaker and foreground service; idempotent. */
    suspend fun stop()
}

/** Microphone seam (16 kHz mono PCM16). */
interface AudioCapture {
    fun hasPermission(): Boolean

    /** Cold flow of ~100 ms chunks; the microphone is released when the collector is cancelled. */
    fun chunks(): Flow<ByteArray>
}

/** Speaker seam (24 kHz mono PCM16). [write] never blocks the caller. */
interface AudioPlayback {
    fun start()

    fun write(pcm: ByteArray)

    /** Drops everything queued (the user interrupted the model). */
    fun flush()

    fun release()
}

/** Starts/stops the foreground service that keeps the microphone alive while the AI drives other apps. */
interface VoiceServiceLauncher {
    fun start()

    fun stop()
}

/** Seam over the SDK's Live API. */
interface LiveGateway {
    /** Registers [onMessage] immediately after connecting; [onClosed] gets the failure, or null on a clean close. */
    suspend fun connect(
        apiKey: String,
        config: LiveConnectConfig,
        onMessage: (LiveServerMessage) -> Unit,
        onClosed: (Throwable?) -> Unit,
    ): LiveConnection
}

interface LiveConnection {
    suspend fun sendAudio(pcm: ByteArray)

    suspend fun sendToolResponse(responses: List<FunctionResponse>)

    fun close()
}
