package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The outbound mTLS HTTP/2 control connection (PROTOCOL.md §3): one duplex `POST /api/v1/control`, PING→PONG,
 * OPEN → [ArcTunnelOpenHandler], RENEW_NUDGE → [ArcTunnelControlEvents.onRenewNudge] (single flight). A torn
 * stream reconnects with [ArcTunnelBackoff]. The in-flight call is cancelled by [stop] and, through a sibling
 * "canceller" coroutine, by the cancellation of the scope running [run], so a blocked read never outlives
 * either. Children (OPEN handlers, renewals, the watchdog) run under a supervisor: one failing child is
 * reported to the scope's handler, the loop goes on.
 */
class ArcTunnelControl(
    client: OkHttpClient,
    private val net: ArcTunnelNet,
    private val controlHost: String,
    private val opens: ArcTunnelOpenHandler,
    private val events: ArcTunnelControlEvents,
) {
    private val running = AtomicBoolean(true)
    private val current = AtomicReference<Call?>(null)
    private val renewalInFlight = AtomicBoolean(false)
    private val node = AtomicReference("unknown")
    private val client =
        client
            .newBuilder()
            .addNetworkInterceptor { chain ->
                chain
                    .connection()
                    ?.route()
                    ?.socketAddress
                    ?.address
                    ?.hostAddress
                    ?.let { node.set(it) }
                chain.proceed(chain.request())
            }.build()

    fun stop() {
        running.set(false)
        current.get()?.cancel()
    }

    /** Serves control streams until [stop]; OPEN handlers, renewals and the watchdog run as children of this scope. */
    suspend fun run() =
        supervisorScope {
            val backoff = ArcTunnelBackoff()
            while (running.get() && isActive) {
                val wasBound = AtomicBoolean(false)
                try {
                    withContext(Dispatchers.IO) { serveControlOnce(this@supervisorScope, wasBound) }
                } catch (e: IOException) {
                    Log.w(TAG, "control connection ended", e)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "control stream torn down on a malformed frame", e)
                }
                if (!running.get() || !isActive) break
                if (wasBound.get()) backoff.reset()
                events.onUnbound()
                delay(backoff.next())
            }
        }

    /**
     * One control stream; [wasBound] is set as soon as the stream is bound, so an abrupt end still resets
     * the backoff.
     */
    private fun serveControlOnce(
        scope: CoroutineScope,
        wasBound: AtomicBoolean,
    ) {
        var sink: BufferedSink? = null
        val request =
            Request
                .Builder()
                .url(net.url(controlHost, CONTROL_PATH))
                .post(duplexBody { sink = it })
                .build()
        val call = client.newCall(request)
        current.set(call)
        if (!running.get()) call.cancel()
        // A cancelled scope (provider stop) cancels the blocking execute/read through the call: the sibling
        // below is cancelled with the scope and its finally runs on the way out. UNDISPATCHED: it reaches
        // awaitCancellation() before this function continues, so an already-cancelled scope still cancels.
        val canceller =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
        val sessions = ArcTunnelSessions { Log.d(TAG, "dial-back session close failed", it) }
        val lastFrameAt = AtomicLong(System.nanoTime())
        val silent = AtomicBoolean(false)
        val watchdog = scope.launch { ArcTunnelWatchdog.watch(call, lastFrameAt, silent) }
        var response: Response? = null
        try {
            val r = call.execute()
            response = r
            val s = sink
            if (r.code == HttpURLConnection.HTTP_OK && s != null) {
                wasBound.set(true)
                events.onBound(node.get())
                readFrames(scope, r.body.source(), s, sessions, lastFrameAt)
            } else {
                Log.w(TAG, "control connect rejected: ${r.code}")
            }
        } finally {
            canceller.cancel()
            watchdog.cancel()
            sessions.closeAll()
            try {
                sink?.close()
            } catch (e: IOException) {
                Log.d(TAG, "control request body close failed", e)
            }
            response?.close()
            current.compareAndSet(call, null)
            if (silent.get()) client.connectionPool.evictAll()
        }
    }

    private fun readFrames(
        scope: CoroutineScope,
        source: BufferedSource,
        sink: BufferedSink,
        sessions: ArcTunnelSessions,
        lastFrameAt: AtomicLong,
    ) {
        var frame = ArcTunnelFrames.read(source)
        while (frame != null && running.get()) {
            lastFrameAt.set(System.nanoTime())
            dispatch(scope, frame.first, frame.second, sink, sessions)
            frame = if (running.get()) ArcTunnelFrames.read(source) else null
        }
    }

    /**
     * A malformed payload throws `SerializationException` (an `IllegalArgumentException`): the stream tears
     * down.
     */
    private fun dispatch(
        scope: CoroutineScope,
        type: Int,
        payload: ByteArray,
        sink: BufferedSink,
        sessions: ArcTunnelSessions,
    ) {
        when (type) {
            ArcTunnelFrames.PING -> {
                sink.write(ArcTunnelFrames.PONG_FRAME)
                sink.flush()
            }

            ArcTunnelFrames.OPEN -> {
                val open = ArcTunnelJson.decodeFromString(ArcTunnelOpenPayload.serializer(), String(payload))
                scope.launch { opens.handle(open, sessions) }
            }

            ArcTunnelFrames.RENEW_NUDGE -> {
                onNudge(scope, String(payload))
            }

            else -> {
                Log.d(TAG, "unknown control frame type $type ignored")
            }
        }
    }

    private fun onNudge(
        scope: CoroutineScope,
        payloadJson: String,
    ) {
        val nonce = ArcTunnelJson.decodeFromString(ArcTunnelRenewNudgePayload.serializer(), payloadJson).nonce
        when {
            nonce.isEmpty() -> Log.w(TAG, "RENEW_NUDGE without a nonce ignored")
            !renewalInFlight.compareAndSet(false, true) -> Log.i(TAG, "renewal in progress; nudge ignored")
            else -> scope.launch { renew(nonce) }
        }
    }

    private suspend fun renew(nonce: String) {
        try {
            events.onRenewNudge(nonce)
        } finally {
            renewalInFlight.set(false)
        }
    }

    private fun duplexBody(capture: (BufferedSink) -> Unit): RequestBody =
        object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun isDuplex(): Boolean = true

            override fun writeTo(sink: BufferedSink) {
                capture(sink)
            }
        }

    companion object {
        private const val TAG = "MCP:ArcTunnelControl"
        private const val CONTROL_PATH = "/api/v1/control"
    }
}
