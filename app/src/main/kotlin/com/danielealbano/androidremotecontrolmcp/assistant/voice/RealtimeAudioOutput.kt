package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.serialization.json.JsonObject

/** The model's spoken reply: plays the audio, reports speaking and passes the captions on. */
internal class RealtimeAudioOutput(
    private val playback: AudioPlayback,
    private val events: LiveEvents,
) {
    fun delta(event: JsonObject) {
        val pcm = event.string("delta")?.let(RealtimeProtocol::decodeAudio) ?: return
        events.speaking()
        playback.write(pcm)
    }

    fun transcript(event: JsonObject) {
        event.string("delta")?.let(events::assistantText)
    }
}
