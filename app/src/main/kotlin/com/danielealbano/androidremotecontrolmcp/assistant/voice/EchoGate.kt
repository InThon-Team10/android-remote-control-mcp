package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.util.Log

/**
 * Half-duplex microphone gate: while the assistant's voice is playing, and for [tailMs] after it drains, microphone
 * audio is not sent. On speakerphone the device echo canceller does not remove all of the assistant's own voice;
 * the leak reached the server as speech, which interrupted the assistant mid-sentence. The user cannot interrupt by
 * voice while the assistant talks; stopping the conversation still works.
 */
internal class EchoGate(
    private val pendingPlaybackMs: () -> Long,
    private val nowMs: () -> Long,
    private val tailMs: Long = ECHO_TAIL_MS,
) {
    private var lastPlayingAtMs: Long? = null
    private var open = true

    /** True when a microphone chunk captured now may be sent. */
    fun allows(): Boolean {
        val now = nowMs()
        if (pendingPlaybackMs() > 0) lastPlayingAtMs = now
        val allowed = lastPlayingAtMs?.let { now - it >= tailMs } ?: true
        if (allowed != open) {
            open = allowed
            Log.d(VOICE_TRACE, "echo gate ${if (allowed) "open" else "closed"}")
        }
        return allowed
    }

    companion object {
        /** How long the microphone stays closed after the assistant's voice stops (room echo dies out). */
        const val ECHO_TAIL_MS = 300L
    }
}
