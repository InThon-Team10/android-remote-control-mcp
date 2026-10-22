package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import kotlinx.coroutines.flow.StateFlow

/** The tunnel-start renewal as the UI sees it. */
sealed class ArcTunnelRenewalState {
    data object Idle : ArcTunnelRenewalState()

    data object Renewing : ArcTunnelRenewalState()

    data class Failed(
        val message: String,
    ) : ArcTunnelRenewalState()
}

/**
 * Certificate renewals of a running tunnel. Every renewal runs in its own long-lived scope, so cancelling the
 * caller (the tunnel stopping) never abandons a `/issue` attempt in flight; a server stop ends them through
 * [stop].
 */
interface ArcTunnelRenewals {
    /** The tunnel-start renewal: Idle → Renewing → Idle | Failed. Routine renewals are not shown. */
    val state: StateFlow<ArcTunnelRenewalState>

    /** Allows issuance again at a tunnel start (after a [stop]). */
    fun resume()

    /**
     * Tunnel start with a missing or expired public certificate: issues it (shown as [state]) and returns once it
     * is saved. Throws the failure, or [IllegalStateException] when the user cancelled it ([cancel]).
     */
    suspend fun renewForStart(identity: ArcTunnelIdentity)

    /** A routine renewal requested by the server (`RENEW_NUDGE`); silent. */
    suspend fun renew(
        identity: ArcTunnelIdentity,
        nonce: String,
    )

    /** User Cancel: aborts the tunnel-start renewal at once, even mid-request. */
    fun cancel()

    /** User Retry after a failed tunnel-start renewal: releases [awaitRetry]. */
    fun retry()

    /** Suspends until the user asks to retry the failed tunnel-start renewal. */
    suspend fun awaitRetry()

    /**
     * Server stop: no new issuance attempt starts. True when an attempt is still in flight (keep the process in the
     * foreground until [awaitIdle]); otherwise every pending renewal is cancelled at once.
     */
    fun stop(): Boolean

    /** Suspends until every renewal has ended. */
    suspend fun awaitIdle()
}
