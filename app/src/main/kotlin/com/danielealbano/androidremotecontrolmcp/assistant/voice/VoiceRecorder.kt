package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.coroutines.flow.StateFlow

/** Recorded voice message. Plain class (no value equality needed; holds raw bytes). */
class VoiceClip(
    val bytes: ByteArray,
    val durationMs: Long,
) {
    companion object {
        const val MIME_TYPE_M4A = "audio/m4a"
    }
}

interface VoiceRecorder {
    val isRecording: StateFlow<Boolean>

    /** True once the platform stopped the recording at the maximum duration; the UI must then call [stop]. */
    val maxDurationReached: StateFlow<Boolean>

    /** Starts recording; idempotent while already recording. Any failure is returned, never thrown. */
    suspend fun start(): Result<Unit>

    /** Stops, reads and deletes the file. Failure when not recording or the recording is empty/invalid. */
    suspend fun stop(): Result<VoiceClip>

    /** Discards the current recording (no-op when not recording).
     * The UI must call it if destroyed while recording.
     */
    suspend fun cancel()
}
