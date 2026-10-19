package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext

@DisplayName("ArcTunnelControl")
class ArcTunnelControlTest {
    private lateinit var server: MockWebServer
    private lateinit var net: ArcTunnelNet
    private lateinit var identity: ArcTunnelIdentity
    private val peers = ArcTunnelTestPeers()
    private val events = ArcTunnelTestEvents()
    private val recorded = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineExceptionHandler { _, t -> recorded += t })
    private var control: ArcTunnelControl? = null
    private var job: Job? = null

    /** A control stream the test drives: frames queued in [outgoing] are written, the client's bytes collected. */
    private class ScriptedStream {
        val outgoing = LinkedBlockingQueue<ByteArray>()
        val incoming = LinkedBlockingQueue<Byte>()
        val ended = CountDownLatch(1)

        fun response(): MockResponse =
            ArcTunnelTestServer.answer(200) { socket ->
                val reader =
                    Thread {
                        try {
                            val source = socket.source.buffer()
                            while (!source.exhausted()) incoming.add(source.readByte())
                        } catch (e: IOException) {
                            System.err.println("scripted stream read ended: $e")
                        } finally {
                            ended.countDown()
                        }
                    }.apply {
                        isDaemon = true
                        start()
                    }
                val sink = socket.sink.buffer()
                try {
                    while (ended.count > 0) {
                        val frame = outgoing.poll(POLL_MS, TimeUnit.MILLISECONDS) ?: continue
                        sink.write(frame)
                        sink.flush()
                    }
                } catch (e: IOException) {
                    System.err.println("scripted stream write ended: $e")
                }
                reader.join(JOIN_MS)
            }

        fun read(count: Int): ByteArray =
            ByteArray(count) { requireNotNull(incoming.poll(BUDGET_S, TimeUnit.SECONDS)) { "no byte from the client" } }
    }

    @BeforeEach
    fun setUp() {
        server = ArcTunnelTestServer.start()
        net = ArcTunnelTestServer.net(server.port)
        identity = ArcTunnelTestIdentity.create(FakeArcTunnelKeyStore(), net)
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            control?.stop()
            job?.cancelAndJoin()
        }
        peers.close()
        server.close()
    }

    private fun opens(localPort: Int = peers.local.localPort): ArcTunnelOpenHandler =
        ArcTunnelOpenHandler(
            ArcTunnelDialBack(
                ArcTunnelTestServer.CONTROL_HOST,
                SSLContext.getDefault(),
                SSLContext.getDefault(),
                allowLocalTargets = true,
            ),
            { localPort },
            events,
            localConnectWindowMs = LOCAL_WINDOW_MS,
        )

    private fun start(handler: ArcTunnelOpenHandler = opens()): ArcTunnelControl {
        val c = ArcTunnelControl(net.controlClient(identity), net, ArcTunnelTestServer.CONTROL_HOST, handler, events)
        control = c
        job = scope.launch { c.run() }
        return c
    }

    private suspend fun awaitThat(condition: () -> Boolean) {
        withTimeout(BUDGET_S * MS_PER_S) { while (!condition()) delay(POLL_MS) }
    }

    private fun closedPort(): Int = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress()).use { it.localPort }

    private fun seesEof(socket: Socket): Boolean {
        socket.soTimeout = EOF_BUDGET_MS
        return try {
            socket.getInputStream().read() < 0
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: IOException) {
            true
        }
    }

    private fun acceptsNothing(listener: ServerSocket): Boolean {
        listener.soTimeout = NOTHING_ACCEPTED_MS
        return runCatching { listener.accept().close() }.exceptionOrNull() is SocketTimeoutException
    }

    private fun open(addr: String): ByteArray =
        ArcTunnelTestServer.frame(
            ArcTunnelFrames.OPEN,
            """{"token":"t","addr":"$addr","use_https":false,"use_mtls":false}""",
        )

    private fun nudge(nonce: String): ByteArray =
        ArcTunnelTestServer.frame(ArcTunnelFrames.RENEW_NUDGE, """{"nonce":"$nonce"}""")

    @Test
    fun `PING is answered with PONG`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            start()

            stream.outgoing += ArcTunnelTestServer.frame(ArcTunnelFrames.PING)

            assertArrayEquals(byteArrayOf(3, 0, 0, 0, 0), stream.read(5))
        }

    @Test
    fun `OPEN dials back then connects locally and splices`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            val remote = AtomicReference<Socket>()
            val local = AtomicReference<Socket>()
            val upgraded = CountDownLatch(1)
            val accepted = CountDownLatch(1)
            peers.acceptUpgrade(remote, upgraded)
            peers.acceptLocal(local, accepted)
            start()

            stream.outgoing += peers.openFrame()
            awaitThat { upgraded.count == 0L && accepted.count == 0L }
            remote.get().getOutputStream().write("ping".toByteArray())
            val toLocal = ByteArray(4).also { local.get().getInputStream().readNBytes(it, 0, 4) }
            local.get().getOutputStream().write("pong".toByteArray())
            val toRemote = ByteArray(4).also { remote.get().getInputStream().readNBytes(it, 0, 4) }

            assertEquals("ping", toLocal.decodeToString())
            assertEquals("pong", toRemote.decodeToString())
        }

    @Test
    fun `OPEN with unreachable local listener closes the dial-back`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            val remote = AtomicReference<Socket>()
            val upgraded = CountDownLatch(1)
            peers.acceptUpgrade(remote, upgraded)
            start(opens(localPort = closedPort()))

            stream.outgoing += peers.openFrame()
            awaitThat { upgraded.count == 0L }

            assertTrue(seesEof(remote.get()))
            awaitThat { events.failures.size == 1 }
        }

    @Test
    fun `OPEN dial-back failure never touches the local listener`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            start()

            stream.outgoing += open("127.0.0.1:${closedPort()}")
            awaitThat { events.failures.size == 1 }

            assertTrue(acceptsNothing(peers.local))
        }

    @Test
    fun `invalid OPEN is ignored`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            start()

            stream.outgoing +=
                ArcTunnelTestServer.frame(ArcTunnelFrames.OPEN, """{"token":"","addr":"127.0.0.1:1"}""")
            stream.outgoing += ArcTunnelTestServer.frame(ArcTunnelFrames.PING)

            assertArrayEquals(byteArrayOf(3, 0, 0, 0, 0), stream.read(5))
            assertTrue(acceptsNothing(peers.dial))
            assertEquals(0, events.unbound.get())
        }

    @Test
    fun `malformed OPEN tears the stream down and reconnects`() =
        runBlocking {
            val first = ScriptedStream()
            server.enqueue(first.response())
            server.enqueue(ArcTunnelTestServer.hold())
            start()

            first.outgoing += ArcTunnelTestServer.frame(ArcTunnelFrames.OPEN, "not json")

            awaitThat { events.boundCount.get() == 2 }
            assertEquals(1, events.unbound.get())
        }

    @Test
    fun `RENEW_NUDGE runs one renewal at a time`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            val gate = CompletableDeferred<Unit>()
            events.nudgeGate = gate
            start()

            stream.outgoing += nudge("0a")
            stream.outgoing += nudge("0b")
            stream.outgoing += ArcTunnelTestServer.frame(ArcTunnelFrames.PING)
            stream.read(5)
            assertEquals(listOf("0a"), events.nudges.toList())

            gate.complete(Unit)
            delay(SETTLE_MS)
            stream.outgoing += nudge("0c")

            awaitThat { events.nudges.size == 2 }
            assertEquals(listOf("0a", "0c"), events.nudges.toList())
        }

    @Test
    fun `non-200 answer closes the request body`() =
        runBlocking {
            val cleanEof = CountDownLatch(1)
            server.enqueue(
                ArcTunnelTestServer.answer(503) { socket ->
                    try {
                        socket.source.buffer().readByteArray()
                        cleanEof.countDown()
                    } catch (e: IOException) {
                        System.err.println("rejected stream ended with an error: $e")
                    }
                },
            )
            start()

            assertTrue(cleanEof.await(BUDGET_S, TimeUnit.SECONDS))
            assertEquals(0, events.boundCount.get())
        }

    @Test
    fun `stop cancels the in-flight stream at once`() =
        runBlocking {
            server.enqueue(ArcTunnelTestServer.hold())
            val c = start()
            awaitThat { events.boundCount.get() == 1 }

            c.stop()

            withTimeout(STOP_BUDGET_MS) { requireNotNull(job).join() }
        }

    @Test
    fun `stop closes the dial-back sessions`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            val remote = AtomicReference<Socket>()
            val local = AtomicReference<Socket>()
            val upgraded = CountDownLatch(1)
            val accepted = CountDownLatch(1)
            peers.acceptUpgrade(remote, upgraded)
            peers.acceptLocal(local, accepted)
            val c = start()
            stream.outgoing += peers.openFrame()
            awaitThat { upgraded.count == 0L && accepted.count == 0L }

            c.stop()

            assertTrue(seesEof(remote.get()))
            assertTrue(seesEof(local.get()))
        }

    @Test
    fun `backoff resets after a bound stream even when it ends abruptly`() =
        runBlocking {
            val arrivals = CopyOnWriteArrayList<Long>()
            val answers =
                ArrayDeque(
                    listOf(
                        MockResponse
                            .Builder()
                            .code(503)
                            .build(),
                        ArcTunnelTestServer.answer(200) { },
                        ArcTunnelTestServer.hold(),
                    ),
                )
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        arrivals += System.nanoTime()
                        return synchronized(answers) { answers.removeFirstOrNull() } ?: ArcTunnelTestServer.hold()
                    }
                }
            start()

            awaitThat { arrivals.size == 3 }

            val gapMs = (arrivals[2] - arrivals[1]) / NANOS_PER_MS
            assertTrue(gapMs in 900..1_700, "gap after the bound stream was $gapMs ms")
            assertEquals(2, events.boundCount.get())
        }

    @Test
    fun `a bound stream stays up through 12 seconds of silence`() =
        runBlocking {
            server.enqueue(ArcTunnelTestServer.hold())
            start()
            awaitThat { events.boundCount.get() == 1 }

            delay(SILENCE_MS)

            assertEquals(0, events.unbound.get())
            assertEquals(1, events.boundCount.get())
        }

    @Test
    fun `a failing OPEN child does not end the control loop`() =
        runBlocking {
            val stream = ScriptedStream()
            server.enqueue(stream.response())
            events.throwOnDialBackFailed = true
            start()

            stream.outgoing += open("127.0.0.1:${closedPort()}")
            awaitThat { recorded.size == 1 }
            stream.outgoing += ArcTunnelTestServer.frame(ArcTunnelFrames.PING)

            assertArrayEquals(byteArrayOf(3, 0, 0, 0, 0), stream.read(5))
            assertTrue(recorded.single() is IllegalStateException)
        }

    @Test
    fun `cancelling the scope cancels the in-flight call`() =
        runBlocking {
            server.enqueue(ArcTunnelTestServer.hold())
            start()
            awaitThat { events.boundCount.get() == 1 }

            withTimeout(STOP_BUDGET_MS) { requireNotNull(job).cancelAndJoin() }
        }

    private companion object {
        const val BACKLOG = 50
        const val LOCAL_WINDOW_MS = 300L
        const val POLL_MS = 20L
        const val JOIN_MS = 1_000L
        const val BUDGET_S = 10L
        const val MS_PER_S = 1_000L
        const val EOF_BUDGET_MS = 1_000
        const val NOTHING_ACCEPTED_MS = 300
        const val SETTLE_MS = 200L
        const val STOP_BUDGET_MS = 2_000L
        const val SILENCE_MS = 12_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}
