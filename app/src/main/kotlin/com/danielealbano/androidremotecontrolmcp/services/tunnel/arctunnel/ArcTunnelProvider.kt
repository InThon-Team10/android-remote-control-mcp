package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelEndpoint
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelStatus
import com.danielealbano.androidremotecontrolmcp.data.repository.ServerLogRepository
import com.danielealbano.androidremotecontrolmcp.services.tunnel.TunnelListenerHost
import com.danielealbano.androidremotecontrolmcp.services.tunnel.TunnelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Free E2E tunnel provider: identity + certificate, loopback TLS listener, control loop,
 * Certificate Transparency check.
 */
class ArcTunnelProvider
    @Inject
    constructor(
        private val enrollment: ArcTunnelEnrollment,
        private val keys: ArcTunnelKeyStore,
        private val ctAlerts: ArcTunnelCtAlerts,
        private val serverLog: ServerLogRepository,
    ) : TunnelProvider {
        /** Everything belonging to one started tunnel; dropped as a whole by [close]. */
        private class Session(
            val host: TunnelListenerHost,
            val scope: CoroutineScope,
            val env: ArcTunnelEnv,
            val ctCheckEnabled: Boolean,
        ) {
            val port = AtomicInteger(0)
            val servedNotAfterMs = AtomicLong(0)
            val certSwap = ArcTunnelCertSwap()
            val connected = AtomicBoolean(false)

            @Volatile
            var identity: ArcTunnelIdentity? = null

            @Volatile
            var monitor: ArcTunnelCtMonitor? = null

            @Volatile
            var opens: ArcTunnelOpenHandler? = null

            @Volatile
            var control: ArcTunnelControl? = null
        }

        private val _status = MutableStateFlow<TunnelStatus>(TunnelStatus.Disconnected)
        override val status: StateFlow<TunnelStatus> = _status.asStateFlow()
        private val mutex = Mutex()

        @Volatile
        private var listenerHost: TunnelListenerHost? = null

        @Volatile
        private var session: Session? = null

        /** Test seam for the Certificate Transparency monitor (API base, random source, minimum interval). */
        internal var ctOptions: ArcTunnelCtMonitor.Options = ArcTunnelCtMonitor.Options()

        /** Test seam: the certificate-swap poll interval. */
        internal var swapPollMs: Long = SWAP_POLL_MS

        /** Must be called before [start] with the host that serves the MCP application. */
        fun bind(host: TunnelListenerHost) {
            listenerHost = host
        }

        /**
         * Returns as soon as the session is launched; the status moves on from `Connecting` asynchronously.
         * A second start while running is ignored (logged) and leaves the live status untouched.
         */
        override suspend fun start(
            localPort: Int,
            config: ServerConfig,
        ) {
            mutex.withLock {
                if (session != null) {
                    Log.w(TAG, "Free E2E tunnel already running; start ignored")
                    return@withLock
                }
                val launched =
                    runCatching {
                        val host = checkNotNull(listenerHost) { "bind() must be called before start()" }
                        val handler =
                            CoroutineExceptionHandler { _, t -> Log.e(TAG, "unexpected tunnel session failure", t) }
                        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
                        Session(host, scope, ArcTunnelEnv.from(config), config.arcTunnelCtCheckEnabled)
                    }
                launched.onSuccess {
                    session = it
                    _status.value = TunnelStatus.Connecting
                    it.scope.launch { bootstrap(it) }
                }
                launched.onFailure {
                    if (it is CancellationException) throw it
                    Log.e(TAG, "Failed to start the free E2E tunnel", it)
                    _status.value = TunnelStatus.Error(it.message ?: it.javaClass.simpleName)
                }
            }
        }

        override suspend fun stop() {
            mutex.withLock {
                session?.let { close(it) }
                session = null
                _status.value = TunnelStatus.Disconnected
            }
        }

        /**
         * The session's own start-up; any failure ends the session with an `Error` status (the next `stop`
         * disposes it). `readyIdentity` waits for an in-flight enrollment and issues a missing certificate.
         */
        private suspend fun bootstrap(s: Session) {
            val outcome =
                runCatching {
                    val identity = enrollment.readyIdentity(s.env) ?: error(NOT_ENROLLED_MESSAGE)
                    val net = identity.net
                    val monitor =
                        ArcTunnelCtMonitor(
                            ArcTunnelCtMonitor.Deps(identity, enrollment.store(s.env), keys, ctAlerts),
                            s.scope,
                            s.ctCheckEnabled,
                            ctOptions,
                        )
                    s.identity = identity
                    s.monitor = monitor
                    startListener(s, identity, null)
                    monitor.restoreAlert()
                    val events = Events(s)
                    val dialBack = net.dialBack(s.env.hosts.controlHost, identity)
                    val opens = ArcTunnelOpenHandler(dialBack, { s.port.get() }, events)
                    val control =
                        ArcTunnelControl(net.controlClient(identity), net, s.env.hosts.controlHost, opens, events)
                    s.opens = opens
                    s.control = control
                    s.scope.launch { control.run() }
                    s.scope.launch { swapLoop(s) }
                }
            outcome.onFailure {
                if (it is CancellationException) throw it
                Log.e(TAG, "Free E2E tunnel session failed", it)
                _status.value = TunnelStatus.Error(it.message ?: it.javaClass.simpleName)
                withContext(NonCancellable + Dispatchers.IO) { s.host.stopTlsListener() }
                s.scope.cancel()
            }
        }

        /** Starts (or restarts) the listener; a cancellation that lands during the blocking start stops it again. */
        private suspend fun startListener(
            s: Session,
            identity: ArcTunnelIdentity,
            port: Int?,
        ) {
            try {
                withContext(Dispatchers.IO) {
                    s.port.set(s.host.startTlsListener(identity.tlsMaterial(), port) { s.monitor?.onRequest() })
                    s.servedNotAfterMs.set(identity.publicLeaf()?.notAfter?.time ?: 0L)
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { s.host.stopTlsListener() }
                throw e
            }
        }

        /**
         * Unblock the control read, cancel every child (their OkHttp calls are cancelled with them), wait at most
         * [STOP_JOIN_MS], then stop the listener and the connection pool.
         */
        private suspend fun close(s: Session) {
            s.control?.stop()
            val job = s.scope.coroutineContext[Job]
            job?.cancel()
            withTimeoutOrNull(STOP_JOIN_MS) { job?.join() }
            withContext(Dispatchers.IO) {
                s.host.stopTlsListener()
                s.monitor?.close()
                s.identity?.net?.close()
            }
        }

        private suspend fun swapLoop(s: Session) {
            while (s.scope.isActive) {
                delay(swapPollMs)
                val splices = s.opens?.activeSplices() ?: 0
                if (s.certSwap.isDue(splices, s.servedNotAfterMs.get())) swap(s)
            }
        }

        /**
         * Restart the listener on the SAME port with the renewed chain; a failure is retried at the next
         * poll.
         */
        private suspend fun swap(s: Session) {
            val identity = s.identity ?: return
            val started = s.certSwap.pending()
            val result =
                runCatching {
                    withContext(Dispatchers.IO) { s.host.stopTlsListener() }
                    startListener(s, identity, s.port.get())
                }
            result.onSuccess {
                s.certSwap.done(started)
                if (s.connected.get()) _status.value = connectedStatus(s)
                serverLog.log(ServerLogEntry.Type.TUNNEL, "Free E2E tunnel certificate renewed and installed")
            }
            result.onFailure {
                if (it is CancellationException) throw it
                // The listener is down until the next poll succeeds: say so instead of showing Connected.
                Log.e(TAG, "Tunnel listener restart failed; retrying at the next poll", it)
                _status.value = TunnelStatus.Error("Listener restart failed; retrying: ${it.message}")
            }
        }

        private fun connectedStatus(s: Session): TunnelStatus.Connected =
            TunnelStatus.Connected(
                endpoints = listOf(TunnelEndpoint(url = s.identity?.publicUrl().orEmpty(), valid = true)),
                providerType = TunnelProviderType.ARC_TUNNEL,
            )

        private inner class Events(
            private val s: Session,
        ) : ArcTunnelControlEvents {
            override fun onBound(node: String) {
                s.connected.set(true)
                Log.i(TAG, "Free E2E tunnel control bound via $node")
                _status.value = connectedStatus(s)
            }

            override fun onUnbound() {
                if (s.connected.compareAndSet(true, false)) _status.value = TunnelStatus.Connecting
            }

            @Suppress("TooGenericExceptionCaught")
            override suspend fun onRenewNudge(nonce: String) {
                val identity = s.identity ?: return
                try {
                    enrollment.renew(identity, nonce)
                    s.certSwap.schedule()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "renewal failed; keeping the current certificate", e)
                    serverLog.log(ServerLogEntry.Type.TUNNEL, "Free E2E tunnel renewal failed: ${e.message}")
                }
            }

            override fun onDialBackFailed(message: String) {
                Log.w(TAG, message)
            }
        }

        companion object {
            private const val TAG = "MCP:ArcTunnelProvider"
            private const val SWAP_POLL_MS = 5_000L
            private const val STOP_JOIN_MS = 1_000L
            private const val NOT_ENROLLED_MESSAGE = "Not enrolled: enable Remote Access in Settings → Tunnel first"
        }
    }
