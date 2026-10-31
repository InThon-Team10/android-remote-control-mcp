package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * The only writer of a [RealtimeLink]: events are sent one at a time in the order they were queued (the connection
 * allows one send in flight). A send stuck longer than [sendTimeoutMs] means the link is dead and is reported to
 * [onFailure], like any send error; the queue then stops.
 */
internal class RealtimeSendQueue(
    scope: CoroutineScope,
    private val link: RealtimeLink,
    private val sendTimeoutMs: Long,
    private val onFailure: (Throwable) -> Unit,
) {
    private val queue = Channel<JsonObject>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in queue) {
                if (!trySend(event)) return@launch
            }
        }
    }

    fun enqueue(event: JsonObject) {
        queue.trySend(event)
    }

    fun close() {
        queue.close()
    }

    private suspend fun trySend(event: JsonObject): Boolean {
        val failure = runCatching { withTimeout(sendTimeoutMs) { link.send(event) } }.exceptionOrNull() ?: return true
        if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
        if (failure is Error) throw failure
        onFailure(failure)
        return false
    }
}
