package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Guards issuance attempts against a server stop: an attempt (the `/issue` call plus saving its result) that is
 * in flight is let finish, because abandoning it can lose a certificate the server already issued; once stopped,
 * no new attempt starts until [resume].
 */
class ArcTunnelIssuanceGate {
    private var stopped = false
    private val attempts = MutableStateFlow(0)

    /** Allows attempts again (a new tunnel start or renewal). */
    @Synchronized
    fun resume() {
        stopped = false
    }

    /** Refuses new attempts; true when one is still in flight (wait for it with [awaitIdle]). */
    @Synchronized
    fun stop(): Boolean {
        stopped = true
        return attempts.value > 0
    }

    @Synchronized
    fun isStopped(): Boolean = stopped

    /** Runs one attempt; throws [IllegalStateException] when the gate is stopped. */
    suspend fun <T> attempt(block: suspend () -> T): T {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    /** Suspends until no attempt is in flight. */
    suspend fun awaitIdle() {
        attempts.first { it == 0 }
    }

    @Synchronized
    private fun enter() {
        check(!stopped) { STOPPED }
        attempts.value += 1
    }

    @Synchronized
    private fun exit() {
        attempts.value -= 1
    }

    companion object {
        const val STOPPED = "the tunnel was stopped; certificate issuance halted"
    }
}
