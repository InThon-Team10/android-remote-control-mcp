package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.serialization.json.JsonObject

/**
 * The model's spoken reply: plays the audio, tracks how much of the current item was queued, and on a barge-in drops
 * what was not heard yet (flush, `conversation.item.truncate` with the played ms) and everything the cancelled reply
 * still sends (audio and captions).
 */
internal class RealtimeAudioOutput(
    private val playback: AudioPlayback,
    private val send: (JsonObject) -> Unit,
    private val events: LiveEvents,
) {
    private val cancelledResponses = HashSet<String>()
    private var item: String? = null
    private var itemMs = 0L
    private var earlierMs = 0L

    fun delta(event: JsonObject) {
        if (event.string("response_id") in cancelledResponses) return
        val pcm = event.string("delta")?.let(RealtimeProtocol::decodeAudio) ?: return
        val id = event.string("item_id")
        if (id != item) {
            item = id
            itemMs = 0
            earlierMs = playback.pendingMs()
        }
        itemMs += pcm.size / LiveAudio.BYTES_PER_MS
        events.speaking()
        playback.write(pcm)
    }

    fun transcript(event: JsonObject) {
        if (event.string("response_id") in cancelledResponses) return
        event.string("delta")?.let(events::assistantText)
    }

    /** The response ended: its late deltas are over, so it no longer needs to be remembered as cancelled. */
    fun responseDone(id: String?) {
        id?.let(cancelledResponses::remove)
    }

    /** The user started speaking while [activeResponse] (if any) was being sent or played. */
    fun bargeIn(activeResponse: String?) {
        activeResponse?.let(cancelledResponses::add)
        val pending = (playback.pendingMs() - earlierMs).coerceIn(0, itemMs)
        val playing = item
        playback.flush()
        if (playing != null && pending > 0) {
            send(RealtimeProtocol.truncate(playing, itemMs - pending))
        }
        item = null
    }
}
