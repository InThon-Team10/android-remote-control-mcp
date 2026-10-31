package com.danielealbano.androidremotecontrolmcp.assistant.voice

import java.io.File

/** One-shot recording session wrapping the platform recorder (the unit-test seam). */
interface AudioRecordingSession {
    /** Configures and prepares the recorder; [onMaxDuration] fires at the duration limit. May throw. */
    fun prepare(
        file: File,
        onMaxDuration: () -> Unit,
    )

    /** May throw. */
    fun start()

    /** May throw, except after the maximum duration was reached (then it is a no-op). */
    fun stop()

    /** Never throws; safe to call multiple times. */
    fun release()
}

fun interface AudioRecordingSessionFactory {
    fun create(): AudioRecordingSession
}

fun interface ElapsedClock {
    fun elapsedRealtimeMs(): Long
}
