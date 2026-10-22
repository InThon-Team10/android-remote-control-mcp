package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.repository.ServerLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArcTunnelRenewalsImpl
    @Inject
    constructor(
        private val enrollment: ArcTunnelEnrollment,
        private val serverLog: ServerLogRepository,
    ) : ArcTunnelRenewals {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val gate = ArcTunnelIssuanceGate()
        private val retries = Channel<Unit>(Channel.CONFLATED)
        private val _state = MutableStateFlow<ArcTunnelRenewalState>(ArcTunnelRenewalState.Idle)
        override val state: StateFlow<ArcTunnelRenewalState> = _state.asStateFlow()

        @Volatile
        private var startRenewal: Deferred<Unit>? = null

        override fun resume() {
            gate.resume()
        }

        override suspend fun renewForStart(identity: ArcTunnelIdentity) {
            retries.tryReceive()
            _state.value = ArcTunnelRenewalState.Renewing
            val job = scope.async { issueShown(identity) }
            startRenewal = job
            try {
                job.await()
            } catch (e: CancellationException) {
                // Our own cancellation (the tunnel stopping) is rethrown; the issuance itself goes on.
                currentCoroutineContext().ensureActive()
                throw IllegalStateException(CANCELLED, e)
            }
        }

        override suspend fun renew(
            identity: ArcTunnelIdentity,
            nonce: String,
        ) {
            scope.async { enrollment.issueAndSave(identity, nonce, gate) }.await()
        }

        override fun cancel() {
            startRenewal?.cancel()
        }

        override fun retry() {
            retries.trySend(Unit)
        }

        override suspend fun awaitRetry() {
            retries.receive()
        }

        override fun stop(): Boolean {
            val inFlight = gate.stop()
            if (!inFlight) scope.coroutineContext.cancelChildren()
            // A failure shown for the stopped tunnel is stale now; an attempt in flight updates the state itself.
            if (_state.value is ArcTunnelRenewalState.Failed) _state.value = ArcTunnelRenewalState.Idle
            return inFlight
        }

        override suspend fun awaitIdle() {
            scope.coroutineContext[Job]
                ?.children
                ?.toList()
                ?.joinAll()
        }

        /** The tunnel-start issuance, mirrored into [state]; a halt caused by a server stop is not a failure. */
        private suspend fun issueShown(identity: ArcTunnelIdentity) {
            val result = runCatching { enrollment.issueAndSave(identity, null, gate) }
            result.onSuccess {
                _state.value = ArcTunnelRenewalState.Idle
                serverLog.log(ServerLogEntry.Type.TUNNEL, "Free E2E tunnel certificate renewed at start")
            }
            result.onFailure {
                if (it is CancellationException || gate.isStopped()) {
                    _state.value = ArcTunnelRenewalState.Idle
                } else {
                    val reason = it.message ?: it.javaClass.simpleName
                    Log.e(TAG, "tunnel-start certificate renewal failed", it)
                    _state.value = ArcTunnelRenewalState.Failed(reason)
                    serverLog.log(ServerLogEntry.Type.TUNNEL, "Free E2E tunnel certificate renewal failed: $reason")
                }
                throw it
            }
        }

        companion object {
            private const val TAG = "MCP:ArcTunnelRenewals"
            const val CANCELLED = "Certificate renewal cancelled"
        }
    }
