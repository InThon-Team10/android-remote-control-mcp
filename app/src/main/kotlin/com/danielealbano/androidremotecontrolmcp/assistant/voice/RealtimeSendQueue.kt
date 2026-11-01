package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * The only writer of a [RealtimeLink]: events are sent one at a time in the order they were queued (the connection
 * allows one send in flight). A send stuck longer than [sendTimeoutMs] means the link is dead: the link is closed
 * (which makes the hung send fail instead of interrupting it mid-write) and the failure is reported to [onFailure],
 * like any send error; the queue then stops. When more than [MAX_PENDING_AUDIO_MS] of microphone audio is waiting,
 * the oldest audio is dropped; control events are never dropped.
 */
internal class RealtimeSendQueue(
    private val scope: CoroutineScope,
    private val link: RealtimeLink,
    private val sendTimeoutMs: Long,
    private val onFailure: (Throwable) -> Unit,
) {
    private val lock = Any()
    private val pending = ArrayDeque<JsonObject>()
    private var pendingAudioMs = 0L
    private var closed = false
    private val wakeup = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            while (true) {
                val event = next() ?: return@launch
                if (!trySend(event)) return@launch
            }
        }
    }

    fun enqueue(event: JsonObject) {
        synchronized(lock) {
            if (closed) return
            pending.addLast(event)
            pendingAudioMs += audioMs(event)
            dropStaleAudio()
        }
        wakeup.trySend(Unit)
    }

    fun close() {
        synchronized(lock) { closed = true }
        wakeup.trySend(Unit)
    }

    private fun dropStaleAudio() {
        if (pendingAudioMs <= MAX_PENDING_AUDIO_MS) return
        val iterator = pending.iterator()
        while (iterator.hasNext() && pendingAudioMs > MAX_PENDING_AUDIO_MS) {
            val audio = audioMs(iterator.next())
            if (audio > 0) {
                iterator.remove()
                pendingAudioMs -= audio
            }
        }
    }

    private suspend fun next(): JsonObject? {
        while (true) {
            synchronized(lock) {
                pending.removeFirstOrNull()?.let {
                    pendingAudioMs -= audioMs(it)
                    return it
                }
                if (closed) return null
            }
            wakeup.receive()
        }
    }

    private suspend fun trySend(event: JsonObject): Boolean {
        val sending = scope.async { runCatching { link.send(event) } }
        val failure =
            try {
                withTimeout(sendTimeoutMs) { sending.await() }.exceptionOrNull() ?: return true
            } catch (timeout: TimeoutCancellationException) {
                link.close()
                timeout
            }
        if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
        if (failure is Error) throw failure
        onFailure(failure)
        return false
    }

    private fun audioMs(event: JsonObject): Long {
        val base64 = (event["audio"] as? JsonPrimitive)?.content
        val isAppend = (event["type"] as? JsonPrimitive)?.content == AUDIO_APPEND
        if (!isAppend || base64 == null) return 0
        return maxOf(1L, base64.length * BASE64_BYTES_PER_GROUP / BASE64_CHARS_PER_GROUP / LiveAudio.BYTES_PER_MS)
    }

    private companion object {
        const val AUDIO_APPEND = "input_audio_buffer.append"
        const val BASE64_BYTES_PER_GROUP = 3L
        const val BASE64_CHARS_PER_GROUP = 4L
        const val MAX_PENDING_AUDIO_MS = 2_000L
    }
}
