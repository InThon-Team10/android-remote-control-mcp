package com.danielealbano.androidremotecontrolmcp.assistant.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("RealtimeSendQueue")
class RealtimeSendQueueTest {
    private class Link(
        val block: suspend (JsonObject) -> Unit,
    ) : RealtimeLink {
        var inFlight = 0
        var maxInFlight = 0
        val sent = mutableListOf<String>()

        override suspend fun send(event: JsonObject) {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                block(event)
                sent += event["type"].toString()
            } finally {
                inFlight--
            }
        }

        var closed = false

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `events are sent one at a time in order`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val link = Link { gate.await() }
            val queue = RealtimeSendQueue(backgroundScope, link, 1000) { }

            listOf("a", "b", "c").forEach { queue.enqueue(RealtimeTestEvents.type(it)) }
            runCurrent()
            assertEquals(1, link.inFlight)
            gate.complete(Unit)
            runCurrent()

            assertEquals(listOf("\"a\"", "\"b\"", "\"c\""), link.sent)
            assertEquals(1, link.maxInFlight)
        }

    @Test
    fun `a send stuck past the timeout reports a failure and stops the queue`() =
        runTest {
            val failures = mutableListOf<Throwable>()
            val link = Link { awaitCancellation() }
            val queue = RealtimeSendQueue(backgroundScope, link, 1000) { failures += it }

            queue.enqueue(RealtimeTestEvents.type("a"))
            queue.enqueue(RealtimeTestEvents.type("b"))
            runCurrent()
            advanceTimeBy(1001)
            runCurrent()

            assertEquals(1, failures.size)
            assertTrue(link.sent.isEmpty())
        }

    @Test
    fun `a send error is reported`() =
        runTest {
            val failures = mutableListOf<Throwable>()
            val link = Link { error("boom") }
            val queue = RealtimeSendQueue(backgroundScope, link, 1000) { failures += it }

            queue.enqueue(RealtimeTestEvents.type("a"))
            runCurrent()

            assertEquals("boom", failures.single().message)
        }

    @Test
    fun `a timeout closes the link and reports the failure`() =
        runTest {
            val failures = mutableListOf<Throwable>()
            val link = Link { awaitCancellation() }
            val queue = RealtimeSendQueue(backgroundScope, link, 1000) { failures += it }

            queue.enqueue(RealtimeTestEvents.type("a"))
            runCurrent()
            advanceTimeBy(1001)
            runCurrent()

            assertTrue(link.closed)
            assertEquals(1, failures.size)
        }

    @Test
    fun `stale audio is dropped oldest first while control events are kept in order`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val link = Link { gate.await() }
            val queue = RealtimeSendQueue(backgroundScope, link, 60_000) { }
            val chunk = ByteArray(LiveAudio.CHUNK_BYTES)

            queue.enqueue(RealtimeTestEvents.type("first"))
            runCurrent()
            queue.enqueue(RealtimeTestEvents.type("truncate"))
            repeat(25) { queue.enqueue(RealtimeProtocol.audioAppend(chunk)) }
            queue.enqueue(RealtimeTestEvents.type("response.create"))
            gate.complete(Unit)
            runCurrent()

            val audio = link.sent.count { it == "\"input_audio_buffer.append\"" }
            assertEquals(20, audio)
            val control = link.sent.filter { !it.contains("audio") }
            assertEquals(listOf("\"first\"", "\"truncate\"", "\"response.create\""), control)
        }
}
