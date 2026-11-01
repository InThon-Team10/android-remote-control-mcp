package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

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

    /** Typed input inside the running conversation; ignored when no conversation is running. */
    fun sendText(text: String)
}

/** Microphone seam ([LiveAudio.SAMPLE_RATE] mono PCM16). */
interface AudioCapture {
    fun hasPermission(): Boolean

    /** Cold flow of ~100 ms chunks; the microphone is released when the collector is cancelled. */
    fun chunks(): Flow<ByteArray>
}

/** Speaker seam (24 kHz mono PCM16). [write] never blocks the caller. */
interface AudioPlayback {
    fun start()

    fun write(pcm: ByteArray)

    /** Milliseconds of audio written that the speaker has not played yet. */
    fun pendingMs(): Long

    fun release()
}

/** Starts/stops the foreground service that keeps the microphone alive while the AI drives other apps. */
interface VoiceServiceLauncher {
    fun start()

    fun stop()
}

/** Seam over the SDK's Realtime API; events travel as JSON in the API's wire format. */
interface RealtimeGateway {
    /** Registers [onEvent] immediately after connecting; [onClosed] gets the failure, or null on a clean close. */
    suspend fun connect(
        apiKey: String,
        onEvent: (JsonObject) -> Unit,
        onClosed: (Throwable?) -> Unit,
    ): RealtimeLink
}

interface RealtimeLink {
    /** The caller MUST NOT send concurrently (the connection allows one send in flight). */
    suspend fun send(event: JsonObject)

    fun close()
}

/** Audio format shared by the capture, the playback and the session configuration. */
object LiveAudio {
    const val SAMPLE_RATE = 24_000
    const val CHUNK_BYTES = SAMPLE_RATE * 2 / 10
    const val BYTES_PER_MS = SAMPLE_RATE * 2 / 1000
}
